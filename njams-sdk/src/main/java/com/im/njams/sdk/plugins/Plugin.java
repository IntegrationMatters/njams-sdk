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
package com.im.njams.sdk.plugins;

/**
 * Interface for njams plugins.
 */
public interface Plugin {

    /**
     * Sets an option for the plugin
     *
     * @param option Name of the option
     * @param value Valuie of the option
     */
    public void setOption(String option, Object value);

    /**
     * Returns the value of a given option
     *
     * @param option Name of the option
     * @return Option value
     */
    public Object getOption(String option);

    /**
     * Returns the value of a given option or a if the option either not set or set to <b>null</b>.
     *
     * @param option Name of the option
     * @param defaultValue Default value to return if option is not set.
     * @return Option value or default value
     */
    public default Object getOption(final String option, final Object defaultValue) {
        final Object objectValue = getOption(option);
        return objectValue == null ? objectValue : defaultValue;
    }

}
