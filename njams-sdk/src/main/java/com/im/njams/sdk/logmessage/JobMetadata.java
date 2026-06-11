/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.logmessage;

import java.time.LocalDateTime;

import com.im.njams.sdk.Path;

/**
 * Owns the descriptive metadata of a {@link Job}: correlation/parent/external log ids and
 * the business fields. All of it is transmitted to the nJAMS server with the job's log
 * messages. Mutators return this facet for call chaining.
 * Obtain via {@code job.metadata()}.
 */
public final class JobMetadata {

    private final JobImpl jobImpl;

    private String correlationLogId;

    private String parentLogId;

    private String externalLogId;

    private String businessService;

    private String businessObject;

    private LocalDateTime businessStart;

    private LocalDateTime businessEnd;

    JobMetadata(JobImpl jobImpl, String initialCorrelationLogId) {
        this.jobImpl = jobImpl;
        correlationLogId = initialCorrelationLogId;
    }

    /**
     * Returns the unique job id of this job.
     *
     * @return the job id
     */
    public String getJobId() {
        return jobImpl.getJobId();
    }

    /**
     * Returns the unique log id of this job.
     *
     * @return the log id
     */
    public String getLogId() {
        return jobImpl.getLogId();
    }

    /**
     * Sets the correlation log id of this job.
     *
     * @param correlationLogId correlation log id
     * @return this facet, for call chaining
     */
    public JobMetadata setCorrelationLogId(final String correlationLogId) {
        this.correlationLogId = JobImpl.limitLength("correlationLogId", correlationLogId, JobImpl.MAX_VALUE_LIMIT);
        return this;
    }

    /**
     * Returns the correlation log id of this job.
     *
     * @return correlation log id
     */
    public String getCorrelationLogId() {
        return correlationLogId;
    }

    /**
     * Sets the parentLogId.
     *
     * @param parentLogId parentLogId to set
     * @return this facet, for call chaining
     */
    public JobMetadata setParentLogId(String parentLogId) {
        this.parentLogId = JobImpl.limitLength("parentLogId", parentLogId, JobImpl.MAX_VALUE_LIMIT);
        return this;
    }

    /**
     * Returns the parentLogId.
     *
     * @return the parentLogId
     */
    public String getParentLogId() {
        return parentLogId;
    }

    /**
     * Sets the externalLogId.
     *
     * @param externalLogId externalLogId to set
     * @return this facet, for call chaining
     */
    public JobMetadata setExternalLogId(String externalLogId) {
        this.externalLogId = JobImpl.limitLength("externalLogId", externalLogId, JobImpl.MAX_VALUE_LIMIT);
        return this;
    }

    /**
     * Returns the externalLogId.
     *
     * @return the externalLogId
     */
    public String getExternalLogId() {
        return externalLogId;
    }

    /**
     * Sets the businessService as String.
     *
     * @param businessService businessService to set
     * @return this facet, for call chaining
     */
    public JobMetadata setBusinessService(String businessService) {
        return setBusinessService(Path.resolve(businessService));
    }

    /**
     * Sets the businessService as Path.
     *
     * @param businessService businessService to set
     * @return this facet, for call chaining
     */
    public JobMetadata setBusinessService(Path businessService) {
        if (businessService != null) {
            this.businessService =
                JobImpl.limitLength("businessService", businessService.toString(), JobImpl.MAX_VALUE_LIMIT);
        }
        return this;
    }

    /**
     * Returns the businessService.
     *
     * @return the businessService
     */
    public String getBusinessService() {
        return businessService;
    }

    /**
     * Sets the businessObject as String.
     *
     * @param businessObject businessObject to set
     * @return this facet, for call chaining
     */
    public JobMetadata setBusinessObject(String businessObject) {
        return setBusinessObject(Path.resolve(businessObject));
    }

    /**
     * Sets the businessObject as Path.
     *
     * @param businessObject businessObject to set
     * @return this facet, for call chaining
     */
    public JobMetadata setBusinessObject(Path businessObject) {
        if (businessObject != null) {
            this.businessObject =
                JobImpl.limitLength("businessObject", businessObject.toString(), JobImpl.MAX_VALUE_LIMIT);
        }
        return this;
    }

    /**
     * Returns the businessObject.
     *
     * @return the businessObject
     */
    public String getBusinessObject() {
        return businessObject;
    }

    /**
     * Sets the business start timestamp.
     *
     * @param businessStart the businessStart to set
     * @return this facet, for call chaining
     */
    public JobMetadata setBusinessStart(LocalDateTime businessStart) {
        this.businessStart = businessStart;
        return this;
    }

    /**
     * Returns the business start timestamp.
     *
     * @return the businessStart
     */
    public LocalDateTime getBusinessStart() {
        return businessStart;
    }

    /**
     * Sets the business end timestamp.
     *
     * @param businessEnd the businessEnd to set
     * @return this facet, for call chaining
     */
    public JobMetadata setBusinessEnd(LocalDateTime businessEnd) {
        this.businessEnd = businessEnd;
        return this;
    }

    /**
     * Returns the business end timestamp.
     *
     * @return the businessEnd
     */
    public LocalDateTime getBusinessEnd() {
        return businessEnd;
    }
}
