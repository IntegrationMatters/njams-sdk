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
package com.im.njams.sdk.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This interface can be used to validate if the given libraries are in the
 * classpath.
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.5
 */
public interface ClasspathValidator {
    /**
     * The logger used by default implementations.
     */
    Logger LOG = LoggerFactory.getLogger(ClasspathValidator.class);

    /**
     * Per default this method validates that libraries that are returned by
     * {@link #librariesToCheck librariesToCheck} are available in the
     * classpath.
     *
     * @throws java.lang.ClassNotFoundException This exception is thrown
     * if any class is not found.
     */
    public default void validate() throws ClassNotFoundException {
        String[] libs = librariesToCheck();
        if (libs != null && libs.length != 0) {
            for (String path : libs) {
                try {
                    Class.forName(path, false, ClasspathValidator.class.getClassLoader());
                    LOG.trace("The class {} was found at runtime!", path);
                } catch (ClassNotFoundException ex) {
                    LOG.error("The class {} hasn't been found! You have to provide it!", path, ex);
                    throw ex;
                }
            }
        }
    }

    /**
     * This method gets all libraries that need to be checked. As default, it
     * returns null.
     *
     * @return An array of Strings of fully qualified class names.
     */
    public String[] librariesToCheck();
}
