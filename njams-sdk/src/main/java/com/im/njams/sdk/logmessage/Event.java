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

import java.time.LocalDateTime;

/**
 * This class represents an event that can be set to a activity.
 *
 * @author stkniep
 * @version 4.0.6
 */
public class Event {

    private final ActivityImpl activity;

    Event(ActivityImpl activity) {
        this.activity = activity;
    }

    /**
     * Return the EventStatus
     *
     * @return the EventStatus
     */
    public EventStatus getStatus() {
        return EventStatus.byValue(activity.getEventStatus());
    }

    /**
     * Set the EventStatus
     *
     * @param status the EventStatus to set
     * @return this event
     */
    public Event setStatus(final EventStatus status) {
        activity.setEventStatus(status.getValue());
        return this;
    }

    /**
     * Return the code
     *
     * @return the code
     */
    public String getCode() {
        return activity.getEventCode();
    }

    /**
     * Set the code
     *
     * @param code the code to set
     * @return this event
     */
    public Event setCode(final String code) {
        activity.setEventCode(code);
        return this;
    }

    /**
     * Return the message
     *
     * @return the message
     */
    public String getMessage() {
        return activity.getEventMessage();
    }

    /**
     * Set the message
     *
     * @param message the message to set
     * @return this event
     */
    public Event setMessage(final String message) {
        activity.setEventMessage(message);
        return this;
    }

    /**
     * Return the payload
     *
     * @return the payload
     */
    public String getPayload() {
        return activity.getEventPayload();
    }

    /**
     * Set the payload
     *
     * @param payload the payload to set
     * @return this event
     */
    public Event setPayload(final String payload) {
        activity.setEventPayload(payload);
        return this;
    }

    /**
     * Return the stacktrace
     *
     * @return the stacktrace
     */
    public String getStacktrace() {
        return activity.getStackTrace();
    }

    /**
     * Set the stacktrace
     *
     * @param stacktrace the stacktrace to set
     * @return this event
     */
    public Event setStacktrace(final String stacktrace) {
        activity.setStackTrace(stacktrace);
        return this;
    }

    /**
     * Return the execution time
     *
     * @return the execution time
     */
    public LocalDateTime getExecutionTime() {
        return activity.getExecution();
    }

    /**
     * Set the execution time
     *
     * @param executionTime the execution time to set
     * @return this event
     */
    public Event setExecutionTime(final LocalDateTime executionTime) {
        activity.setExecution(executionTime);
        return this;
    }

}
