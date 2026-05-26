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
package com.im.njams.sdk.settings;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Skeletal base for {@link ReadOnlyClientSetting} implementations. Provides the standard secured-keys
 * handling: a mutable per-instance set of case-insensitive substring tokens used to decide whether
 * a property key's value should be masked in log output. The set is seeded with the default tokens
 * {@code password}, {@code credentials}, {@code secret}, and {@code keystore.key}, and can be
 * extended at runtime via {@link #addSecureProperties(Set)}.
 * <p>
 * Subclasses provide the actual storage operations ({@link #getProperty(String)},
 * {@link #containsKey(String)}, and {@link #iterator()}); the secured-keys behavior is inherited.
 */
public abstract class AbstractReadOnlySettings implements ReadOnlyClientSetting {

    private static final Set<String> DEFAULT_SECURED =
        Set.of("password", "credentials", "secret", "keystore.key");

    private final Set<String> secured = new HashSet<>(DEFAULT_SECURED);

    @Override
    public Set<String> getSecuredProperties() {
        return Collections.unmodifiableSet(secured);
    }

    @Override
    public void addSecureProperties(Set<String> additional) {
        additional.forEach(p -> secured.add(p.toLowerCase()));
    }
}
