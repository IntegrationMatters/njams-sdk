package com.im.njams.sdk.communication.http;

import java.net.URI;
import java.net.URL;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * A special {@link NjamsSdkRuntimeException} that triggers reconnecting the {@link HttpSseReceiver}.
 *
 * @author cwinkler
 *
 */
public class HttpSendException extends NjamsSdkRuntimeException {

    private static final long serialVersionUID = -4404653059366192032L;

    public HttpSendException(URI uri, Throwable cause) {
        super("Error sending message with HTTP client URI " + uri, cause);
    }

    public HttpSendException(URL url, Throwable cause) {
        super("Error sending message with HTTP client URL " + url, cause);
    }

}
