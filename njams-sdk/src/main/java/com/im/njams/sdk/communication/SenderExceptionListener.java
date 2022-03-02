package com.im.njams.sdk.communication;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * ExceptionListener will be called when an exception occurred.
 */
public interface SenderExceptionListener {

    /**
     * Implement special handling when a problem occurred during sending a message
     *
     * @param exception the exception thrown
     * @param msg       the message
     */
    void onException(NjamsSdkRuntimeException exception, CommonMessage msg);
}
