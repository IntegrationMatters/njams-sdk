/*
 * Projectname: com.im.njams.sdk.utils
 * Filename : ClasspathValidator
 * Creation date : 12.03.2019
 *
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
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
     * Per default this method validates that libraries that are returned by
     * {@link #librariesToCheck librariesToCheck} are available in the
     * classpath.
     *
     * @throws java.lang.ClassNotFoundException This exception is thrown
     * if any class is not found.
     */
    public default void validate() throws ClassNotFoundException{
        Logger LOG = LoggerFactory.getLogger(ClasspathValidator.class);
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
    public default String[] librariesToCheck(){
        return null;
    }
}
