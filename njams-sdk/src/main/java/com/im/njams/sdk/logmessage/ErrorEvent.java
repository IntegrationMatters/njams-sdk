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
package com.im.njams.sdk.logmessage;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

import com.im.njams.sdk.common.DateTimeUtility;

/**
 * This class represents an event that can be set to a activity.
 *
 * @author stkniep
 * @version 4.0.6
 */
public class ErrorEvent {

    private EventStatus status = EventStatus.ERROR;
    private String eventCode = null;
    private String payload = null;
    private String message = null;
    private String stacktrace = null;
    private LocalDateTime eventTime = null;

    public ErrorEvent() {
    }

    public ErrorEvent(Throwable exception) {
        message = exception.toString();
        stacktrace = getStack(exception);
    }

    /**
     * Return the EventStatus
     *
     * @return the EventStatus
     */
    public EventStatus getStatus() {
        return status;
    }

    /**
     * Set the EventStatus
     *
     * @param status the EventStatus to set
     * @return this event
     */
    public ErrorEvent setStatus(final EventStatus status) {
        this.status = status;
        return this;
    }

    /**
     * Return the code
     *
     * @return the code
     */
    public String getCode() {
        return eventCode;
    }

    /**
     * Set the code
     *
     * @param code the code to set
     * @return this event
     */
    public ErrorEvent setCode(final String code) {
        eventCode = code;
        return this;
    }

    /**
     * Return the message
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Set the message
     *
     * @param message the message to set
     * @return this event
     */
    public ErrorEvent setMessage(final String message) {
        this.message = message;
        return this;
    }

    /**
     * Return the payload
     *
     * @return the payload
     */
    public String getPayload() {
        return payload;
    }

    /**
     * Set the payload
     *
     * @param payload the payload to set
     * @return this event
     */
    public ErrorEvent setPayload(final String payload) {
        this.payload = payload;
        return this;
    }

    /**
     * Return the stacktrace
     *
     * @return the stacktrace
     */
    public String getStacktrace() {
        return stacktrace;
    }

    /**
     * Set the stacktrace
     *
     * @param stacktrace the stacktrace to set
     * @return this event
     */
    public ErrorEvent setStacktrace(final String stacktrace) {
        this.stacktrace = stacktrace;
        return this;
    }

    public ErrorEvent setException(final Throwable exception) {
        message = exception.toString();
        stacktrace = getStack(exception);
        return this;
    }

    private String getStack(Throwable exception) {
        try (StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw)) {
            exception.printStackTrace(pw);
            return sw.toString();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Return the event time
     *
     * @return the event time
     */
    public LocalDateTime getEventTime() {
        return eventTime;
    }

    /**
     * Set the event time
     *
     * @param eventTime the event time to set
     * @return this event
     */
    public ErrorEvent setEventTime(final LocalDateTime eventTime) {
        this.eventTime = eventTime;
        return this;
    }

    public ErrorEvent now() {
        eventTime = DateTimeUtility.now();
        return this;
    }
}
