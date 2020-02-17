/*
 * Copyright (c) 2020 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.logmessage;

import java.util.regex.Pattern;

/**
 * DataMaskingType is a POJO to hold the name of the pattern and the actual regex, once as String, once as Pattern.
 *
 * @author krautenberg
 * @version 4.0.16
 */
public class DataMaskingType {

    private String nameOfPattern;

    private String regex;

    private Pattern pattern;

    /**
     * Sets the name of the pattern, the regex as String and parses the String to a {@link Pattern}.
     *
     * @param nameOfPattern name of the pattern to use
     * @param regex value of the pattern, the actual regex
     */
    public DataMaskingType(String nameOfPattern, String regex) {
        this.nameOfPattern = nameOfPattern;
        this.regex = regex;
        this.pattern = Pattern.compile(regex);
    }

    /**
     * Gets the name of the pattern.
     *
     * @return name of Pattern
     */
    public String getNameOfPattern() {
        return nameOfPattern;
    }

    /**
     * Gets the regex of the pattern as String
     *
     * @return regex of pattern as String
     */
    public String getRegex() {
        return regex;
    }

    /**
     * Gets the Pattern
     *
     * @return gets the pattern.
     */
    public Pattern getPattern() {
        return pattern;
    }
}
