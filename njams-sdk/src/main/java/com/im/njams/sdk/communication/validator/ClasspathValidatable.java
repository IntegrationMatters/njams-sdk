package com.im.njams.sdk.communication.validator;

public interface ClasspathValidatable extends Validatable{

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
