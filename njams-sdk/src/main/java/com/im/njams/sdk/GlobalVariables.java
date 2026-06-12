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
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Internal collaborator of {@link NjamsModel}: holds the client's global variables and the
 * global-variable matching pattern (with its validation), which are announced in the project
 * message. Not part of the public API.
 */
final class GlobalVariables {

    // Matches a named-group declaration, e.g. "(?<name>"; deliberately excludes look-behind "(?<=" / "(?<!".
    private static final Pattern NAMED_GROUP_DECLARATION = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>");

    private final Object projectMessageLock;

    // Name -> Value
    private final Map<String, String> globalVariables = new HashMap<>();

    // Regex defining how global-variable references are matched; null means the server applies its default.
    private String globalVariablesPattern;

    GlobalVariables(Object projectMessageLock) {
        this.projectMessageLock = projectMessageLock;
    }

    Map<String, String> getAll() {
        return globalVariables;
    }

    void add(Map<String, String> variables) {
        synchronized (projectMessageLock) {
            globalVariables.putAll(variables);
        }
    }

    String getPattern() {
        return globalVariablesPattern;
    }

    void setPattern(String pattern) {
        if (pattern != null) {
            validate(pattern);
        }
        globalVariablesPattern = pattern;
    }

    private static void validate(String regex) {
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new NjamsSdkRuntimeException("Invalid global-variables pattern: " + regex, e);
        }
        boolean hasFull = false;
        boolean hasName = false;
        final Matcher declarations = NAMED_GROUP_DECLARATION.matcher(regex);
        while (declarations.find()) {
            final String group = declarations.group(1);
            if ("full".equals(group)) {
                hasFull = true;
            } else if ("name".equals(group)) {
                hasName = true;
            }
        }
        if (!hasFull || !hasName) {
            throw new NjamsSdkRuntimeException(
                "Global-variables pattern must declare the named groups 'full' and 'name': " + regex);
        }
    }
}
