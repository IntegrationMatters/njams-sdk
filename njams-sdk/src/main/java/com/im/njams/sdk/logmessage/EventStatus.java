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
package com.im.njams.sdk.logmessage;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Event status
 */
public enum EventStatus {
    /**
     * INFO
     */
    INFO(0),
    /**
     * SUCCESS
     */
    SUCCESS(1),
    /**
     * WARNING
     */
    WARNING(2),
    /**
     * ERROR
     */
    ERROR(3);
    private final int value;

    private EventStatus(int numeric) {
        value = numeric;
    }

    /**
     * The integer value
     *
     * @return integer value
     */
    public int getValue() {
        return value;
    }

    /**
     * Gets the corresponding EventStatus to the given value. If the value is
     * null, it returns null. For any other illegal value an
     * NjamsSdkRuntimeException will be thrown.
     * 
     * @param value integer value to the corresponding EventStatus
     * @return the corresponding EventStatus or null
     */
    public static EventStatus byValue(final Integer value) {
        if (value == null) {
            return null;
        }
        for (EventStatus js : values()) {
            if (js.getValue() == value) {
                return js;
            }
        }
        throw new NjamsSdkRuntimeException("Illegal event status value: " + value);
    }

}
