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

import java.util.Properties;

/**
 * Property Util
 *
 * @author pnientiedt
 */
public class PropertyUtil {

    /**
     * Return new Properties, which contains only the properties starting with a
     * given prefix.
     *
     * @param properties properties to be filtered
     * @param prefix prefix
     * @return new filtered Properties
     */
    public static Properties filter(Properties properties, String prefix) {
        Properties response = new Properties();
        properties.entrySet()
                .stream()
                .filter(e -> String.class.isAssignableFrom(e.getKey().getClass()))
                .filter(e -> ((String) e.getKey()).startsWith(prefix))
                .forEach(e -> response.setProperty((String) e.getKey(), (String) e.getValue()));
        return response;
    }

    /**
     * Return new Properties, which contains only the properties starting with a
     * given prefix, stripped from that prefix.
     *
     * @param properties properties to be filtered
     * @param prefix prefix
     * @return new filtered and stripped Properties
     */
    public static Properties filterAndCut(Properties properties, String prefix) {
        Properties response = new Properties();
        properties.entrySet()
                .stream()
                .filter(e -> String.class.isAssignableFrom(e.getKey().getClass()))
                .filter(e -> ((String) e.getKey()).startsWith(prefix))
                .forEach(e -> response.setProperty(((String) e.getKey()).substring(((String) e.getKey()).indexOf(prefix) + prefix.length() + 1), (String) e.getValue()));
        return response;
    }
}
