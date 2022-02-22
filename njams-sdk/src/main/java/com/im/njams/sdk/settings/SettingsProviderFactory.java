/*
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * This factory is not used anywhere in SDK or in one of the big clients.
 * <p>
 * Deprecate it in version 4.2.0 and it will be removed in next minor.
 */
@Deprecated
public class SettingsProviderFactory {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsProviderFactory.class);

    /**
     * Property key for the settings properties. Specifies which implementation will be loaded.
     */
    public static final String SETTINGS_PROVIDER = "njams.sdk.settings.provider";

    /**
     * Returns the SettingsProvider, which name matches the name given via the Properties into the constructor.
     *
     * @param properties * the properties to find the settings provider
     * @return the {@link SettingsProvider} or a {@link UnsupportedOperationException}
     */
    public static SettingsProvider getSettingsProvider(final Properties properties) {
        if (properties.containsKey(SETTINGS_PROVIDER)) {
            final String name = properties.getProperty(SETTINGS_PROVIDER);
            final ServiceLoader<SettingsProvider> receiverList = ServiceLoader.load(SettingsProvider.class);
            final Iterator<SettingsProvider> iterator = receiverList.iterator();
            while (iterator.hasNext()) {
                final SettingsProvider settingsProvider = iterator.next();
                if (settingsProvider.getName().equals(name)) {
                    LOG.info("Create SettingsProvider {}", settingsProvider.getName());
                    return settingsProvider;
                }
            }
            String available = StreamSupport
                    .stream(Spliterators.spliteratorUnknownSize(ServiceLoader.load(SettingsProvider.class).iterator(),
                            Spliterator.ORDERED), false)
                    .map(cp -> cp.getName()).collect(Collectors.joining(", "));
            throw new UnsupportedOperationException(
                    "Unable to find SettingsProvider implementation with name " + name + ", available are: "
                            + available);
        } else {
            throw new UnsupportedOperationException(
                    "Unable to find " + SETTINGS_PROVIDER + " in settings properties");
        }
    }
}
