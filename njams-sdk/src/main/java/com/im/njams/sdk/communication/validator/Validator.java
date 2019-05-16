package com.im.njams.sdk.communication.validator;

public interface Validator<Validatable>{

    public void validate(Validatable validatable) throws Throwable;
}
