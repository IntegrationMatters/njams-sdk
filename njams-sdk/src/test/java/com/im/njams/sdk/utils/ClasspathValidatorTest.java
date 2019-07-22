/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.utils;

import com.im.njams.sdk.communication_to_merge.validator.ClasspathValidatable;
import com.im.njams.sdk.communication_to_merge.validator.ClasspathValidator;
import org.junit.Test;

/**
 * This class tests the classpathValidator.
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.5
 */
public class ClasspathValidatorTest {

    private ClasspathValidator validator = new ClasspathValidator();
    /**
     * Test of validate method, of class ClasspathValidator.
     *
     * @throws java.lang.ClassNotFoundException should not be thrown
     */
    @Test
    public void testValidateWithNull() throws ClassNotFoundException {
        ClasspathValidatable validatable = new ClasspathValidatable() {
            @Override
            public String[] librariesToCheck() {
                return null;
            }
        };
        validator.validate(validatable);
        //If nothing is thrown, everything is fine.
    }

    /**
     * Test of validate method, of class ClasspathValidator.
     *
     * @throws java.lang.ClassNotFoundException should not be thrown
     */
    @Test
    public void testValidateWithEmptyArray() throws ClassNotFoundException {
        ClasspathValidatable validatable = new ClasspathValidatable() {
            @Override
            public String[] librariesToCheck(){
                return new String[0];
            }
        };
        validator.validate(validatable);
        //If nothing is thrown, everything is fine.
    }

    /**
     * Test of validate method, of class ClasspathValidator.
     *
     * @throws java.lang.ClassNotFoundException should not be thrown
     */
    @Test
    public void testValidateSuccess() throws ClassNotFoundException {
        ClasspathValidatable validatable = new ClasspathValidatable() {
            @Override
            public String[] librariesToCheck(){
                return new String[]{"com.im.njams.sdk.utils.ClasspathValidatorTest"};
            }
        };
        validator.validate(validatable);
        //If nothing is thrown, everything is fine.
    }

    /**
     * Test of validate method, of class ClasspathValidator.
     *
     * @throws java.lang.ClassNotFoundException should be thrown
     */
    @Test(expected = ClassNotFoundException.class)
    public void testValidateFailure() throws ClassNotFoundException {
        ClasspathValidatable validatable = new ClasspathValidatable() {
            @Override
            public String[] librariesToCheck(){
                return new String[]{"this.is.not.a.good.classpath"};
            }
        };
        //This should throw a ClassNotFoundException
        validator.validate(validatable);
    }
}
