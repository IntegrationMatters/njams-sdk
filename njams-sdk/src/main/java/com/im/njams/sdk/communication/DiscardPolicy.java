/*
 * Copyright (c) 2020 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.communication;

import com.im.njams.sdk.NjamsSettings;

import java.util.Arrays;

/**
 * Defines all implemented discard policies that can be set via {@link NjamsSettings#PROPERTY_DISCARD_POLICY}.
 *
 * @author cwinkler
 */
public enum DiscardPolicy {
    //none|onconnectionloss|discard
    /**
     * Default. Never discard. Processing blocks if messages cannot be sent. (property value=none)
     */
    NONE,
    /**
     * Discard only if connection is lost. In all other cases, processing blocks if messages cannot be sent.
     * (property value=onconnectionloss)
     */
    ON_CONNECTION_LOSS("onconnectionloss"),
    /**
     * Discard messages always if they cannot be sent instantly. (property value=discard)
     */
    DISCARD;

    /**
     * The default policy, i.e. {@link DiscardPolicy#NONE}.
     */
    public static final DiscardPolicy DEFAULT = NONE;

    private final String value;

    private DiscardPolicy() {
        value = name().toLowerCase();
    }

    private DiscardPolicy(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * Finds an instance by its string value.
     *
     * @param value Value of the instance to find.
     * @return The instance according to given value or {@link #DEFAULT} if no matching instance was found.
     */
    public static DiscardPolicy byValue(String value) {
        return Arrays.stream(values()).filter(p -> p.value.equalsIgnoreCase(value) || p.name().equalsIgnoreCase(value))
            .findAny().orElse(DEFAULT);
    }
}
