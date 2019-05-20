package com.im.njams.sdk.communication.validator;

public interface Validator<T extends Validatable>{

    public void validate(T validatable) throws Exception;
}
