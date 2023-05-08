package com.im.njams.sdk.communication.http;

import java.net.URI;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

public class HttpSendException extends NjamsSdkRuntimeException {

    private static final long serialVersionUID = -4404653059366192032L;

    public HttpSendException(URI uri, Throwable cause) {
        super("Error sending message with HTTP client URI " + uri, cause);
    }

}
