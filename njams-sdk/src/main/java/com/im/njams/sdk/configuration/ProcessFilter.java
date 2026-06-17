/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.configuration.ProcessFilterEntry.FilterType;
import com.im.njams.sdk.configuration.ProcessFilterEntry.MatcherType;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.utils.StringUtils;

/**
 * This class implements filtering processes based on the runtime {@link Configuration}.
 */
public class ProcessFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessFilter.class);
    private final Collection<Pattern> includePatterns = new ArrayList<>();
    private final Collection<Pattern> excludePatterns = new ArrayList<>();
    private final Collection<String> includes = new ArrayList<>();
    private final Collection<String> excludes = new ArrayList<>();
    // Additional exclude patterns read directly from the client settings. Kept separate from the
    // configuration-derived patterns so they can be carried over when the filter is rebuilt after a
    // configuration change, without re-reading the settings.
    private final Collection<Pattern> settingsExcludePatterns;
    // cached decisions for faster results
    private final Map<Path, Boolean> decisions = new ConcurrentHashMap<>();

    private final boolean includeAll;
    private final boolean excludeNone;

    private final Configuration config;

    /**
     * Creates a process filter based on the given configuration only (no settings-based patterns).
     * @param config The runtime configuration providing the server-managed process filters.
     */
    public ProcessFilter(final Configuration config) {
        this(config, (ClientSettings) null);
    }

    /**
     * Creates a process filter that, in addition to the configuration filters, applies the
     * process-exclusion regular expressions read directly from the given client settings (keys
     * starting with {@link NjamsSettings#PROPERTY_PROCESS_EXCLUDE_REGEX_PREFIX}). The settings-based
     * patterns are treated exactly like configured EXCLUDE/REGEX filters.
     * @param config The runtime configuration providing the server-managed process filters.
     * @param settings The client settings to read additional exclude patterns from, or
     * <code>null</code> for none.
     */
    ProcessFilter(final Configuration config, final ClientSettings settings) {
        this(config, compileSettingsExcludePatterns(settings));
    }

    /**
     * Creates a process filter with pre-compiled settings-based exclude patterns. Used to rebuild the
     * filter after a configuration change while preserving the settings-based patterns.
     * @param config The runtime configuration providing the server-managed process filters.
     * @param settingsExcludePatterns Pre-compiled settings-based exclude patterns, or
     * <code>null</code> for none.
     */
    ProcessFilter(final Configuration config, final Collection<Pattern> settingsExcludePatterns) {
        this.config = config;
        this.settingsExcludePatterns =
            settingsExcludePatterns == null ? Collections.emptyList() : settingsExcludePatterns;
        convertProcessExcludes();
        if (config.getProcessFilters() != null) {
            for (final ProcessFilterEntry filter : config.getProcessFilters()) {
                if (StringUtils.isBlank(filter.getFilterValue())) {
                    continue;
                }
                if (filter.getFilterType() == FilterType.EXCLUDE) {
                    if (filter.getMatcherType() == MatcherType.REGEX) {
                        final Pattern c = compilePattern(filter.getFilterValue());
                        if (c != null) {
                            excludePatterns.add(c);
                            LOG.debug("Added exclude pattern: {}", filter.getFilterValue());
                        }
                    } else {
                        excludes.add(filter.getFilterValue().trim().toLowerCase());
                        LOG.debug("Added exclude value: {}", filter.getFilterValue());
                    }
                } else if (filter.getMatcherType() == MatcherType.REGEX) {
                    final Pattern c = compilePattern(filter.getFilterValue());
                    if (c != null) {
                        includePatterns.add(compilePattern(filter.getFilterValue()));
                        LOG.debug("Added include pattern: {}", filter.getFilterValue());
                    }
                } else {
                    includes.add(filter.getFilterValue().trim().toLowerCase());
                    LOG.debug("Added include value: {}", filter.getFilterValue());
                }
            }
        }
        includeAll = includes.isEmpty() && includePatterns.isEmpty();
        excludeNone = excludes.isEmpty() && excludePatterns.isEmpty() && this.settingsExcludePatterns.isEmpty();
    }

    /**
     * Reads and compiles the process-exclusion regular expressions directly from the given settings
     * (keys starting with {@link NjamsSettings#PROPERTY_PROCESS_EXCLUDE_REGEX_PREFIX}). Invalid
     * expressions are skipped (logged as a warning).
     */
    private static Collection<Pattern> compileSettingsExcludePatterns(final ClientSettings settings) {
        if (settings == null) {
            return Collections.emptyList();
        }
        final List<Pattern> patterns = new ArrayList<>();
        for (final Entry<String, String> entry : settings) {
            if (entry.getKey().startsWith(NjamsSettings.PROPERTY_PROCESS_EXCLUDE_REGEX_PREFIX)
                && StringUtils.isNotBlank(entry.getValue())) {
                final Pattern c = compilePattern(entry.getValue().trim());
                if (c != null) {
                    patterns.add(c);
                    LOG.debug("Added setting-based exclude pattern: {}", entry.getValue());
                }
            }
        }
        return patterns;
    }

    /**
     * Returns the compiled settings-based exclude patterns, so they can be carried over when the
     * filter is rebuilt after a configuration change.
     * @return the compiled settings-based exclude patterns, never <code>null</code>
     */
    Collection<Pattern> settingsExcludePatterns() {
        return settingsExcludePatterns;
    }

    private static Pattern compilePattern(final String regex) {
        if (StringUtils.isBlank(regex)) {
            return null;
        }
        try {
            return Pattern.compile(regex);
        } catch (final PatternSyntaxException e) {
            LOG.warn("Ignoring illegal process match pattern {}: {}", regex, e.getMessage());
        }
        return null;
    }

    /**
     * Returns <code>true</code> if the given process should be processed. Respectively, returns <code>false</code>
     * if the given process is excluded from processing for one of the following reasons.
     * <ol>
     * <li>The client instance's log-mode is {@link LogMode#NONE}</li>
     * <li>The process itself is excluded by the according flag {@link ProcessConfiguration#isExclude()}</li>
     * <li>The combination of configured process includes/excludes in {@link Configuration#getProcessFilters()}
     * results in excluding the process</li>
     * </ol>
     * @param processPath The path of the process to test.
     * @return <code>true</code> if the process with shall be processed.
     */
    public boolean isSelected(final Path processPath) {
        if (processPath == null) {
            return false;
        }
        final String pathString = processPath.toString();
        if (config.getLogMode() == LogMode.NONE) {
            // do not cache, since this setting can change during runtime!
            return false;
        }
        if (includeAll && excludeNone) {
            // all selected; no need for caching
            return true;
        }
        final Boolean cached = decisions.get(processPath);
        if (cached != null) {
            return cached;
        }
        // do expensive (pattern-) matching just once; store the result in the decisions cache
        final String lowerCase = pathString.toLowerCase();
        if (excludes.contains(lowerCase)) {
            decisions.put(processPath, false);
            return false;
        }
        if (includes.contains(lowerCase)) {
            decisions.put(processPath, true);
            return true;
        }
        if (excludePatterns.stream().anyMatch(r -> r.matcher(pathString).matches())
            || settingsExcludePatterns.stream().anyMatch(r -> r.matcher(pathString).matches())) {
            decisions.put(processPath, false);
            return false;
        }
        if (includeAll || includePatterns.stream().anyMatch(r -> r.matcher(pathString).matches())) {
            decisions.put(processPath, true);
            return true;
        }
        decisions.put(processPath, false);
        return false;

    }

    /**
     * Sets or removes an exclude filter for the given process depending on the <code>exclude</code> parameter.<br>
     * This is used for processing the according set command from nJAMS server.
     * @param processPath The process path for that an exclude filter shall be added or removed.
     * @param excluded If <code>true</code> an exclude-filter for the given path is added (if none exists), if
     * <code>false</code>, any existing exclude-filter for that path is removed.
     *
     */
    public void setExcluded(Path processPath, boolean excluded) {
        decisions.put(processPath, excluded);
        final String pathString = processPath.toString();
        if (excluded) {
            if (config.getProcessFilters().stream().noneMatch(excludeProcessPredicate(pathString))) {
                // Add directly to the list (not via Configuration.addProcessFilter) to avoid
                // triggering a filter rebuild while this filter is mutating.
                config.getProcessFilters()
                    .add(new ProcessFilterEntry(FilterType.EXCLUDE, MatcherType.VALUE, pathString));
                config.save();
                LOG.debug("Process {} excluded explicitly.", pathString);
            }
        } else if (config.getProcessFilters().removeIf(excludeProcessPredicate(pathString))) {
            config.save();
            LOG.debug("Process {} no longer explicitly excluded.", pathString);
        }
    }

    /**
     * Returns whether or not the process with the given path has an explicit exclude filter setm i.e., an exclude
     * filter that excludes this path only.<br>
     * This is the new implementation for the former (deprecated) {@link ProcessConfiguration#isExclude()} flag and
     * is used to serve the according get command from nJAMS server.<br>
     * This is different from !{@link #isSelected(Path)} since there can be additional filters that prevent the given
     * process from being processed even when it is not explicitly excluded.
     * @param processPath The process path to test.
     * @return <code>true</code> only if there is an explicit exclude filter for the given path.
     */
    public boolean hasExcludeFilter(Path processPath) {
        return config.getProcessFilters().stream().anyMatch(excludeProcessPredicate(processPath.toString()));
    }

    private Predicate<ProcessFilterEntry> excludeProcessPredicate(String process) {
        return f -> FilterType.EXCLUDE.equals(f.getFilterType()) && MatcherType.VALUE.equals(f.getMatcherType())
            && process.equalsIgnoreCase(f.getFilterValue());
    }

    /**
     * Maps the former {@link ProcessConfiguration#isExclude()} settings into according {@link ProcessFilterEntry}s.
     */
    @SuppressWarnings("removal")
    private void convertProcessExcludes() {
        if (config.getProcesses() == null) {
            return;
        }
        boolean needsSave = false;
        for (Entry<String, ProcessConfiguration> e : config.getProcesses().entrySet()) {
            if (Boolean.TRUE.equals(e.getValue().isExclude())) {
                // Add directly to the list (not via Configuration.addProcessFilter) to avoid
                // triggering a filter rebuild during construction of this filter.
                config.getProcessFilters()
                    .add(new ProcessFilterEntry(FilterType.EXCLUDE, MatcherType.VALUE, e.getKey()));
                LOG.debug("Converted old exclude: {}={}", e.getKey(), e.getValue().isExclude());
                e.getValue().setExclude(null);
                needsSave = true;
            }
        }
        if (needsSave) {
            config.save();
        }
    }
}
