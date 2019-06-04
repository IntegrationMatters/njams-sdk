package com.im.njams.sdk.service;

public interface NjamsService {

    /**
     * The implementation should return its name here, by which it can be
     * identified. This name will be used as key for creating new services with this name.
     *
     * @return the name of the service implementation
     */
    public String getName();
}
