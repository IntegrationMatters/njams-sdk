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
package com.im.njams.sdk;

import java.util.Map;

import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.image.ImageSupplier;

/**
 * Builder for announcing additional project resources to the nJAMS server after the client has
 * started. Collect any combination of process models, global variables, and images, then call
 * {@link #build()} to flush them in a single additional project message.
 * <p>
 * Only resources that have not been announced before are transmitted: a process model (by its
 * path), an image (by its name), or a global variable (by its name) that was already sent — with
 * the original start-time project message or an earlier {@code build()} — is omitted, so an
 * additional message never repeats data already known to the server. Obtain an instance via
 * {@link NjamsModel#additionalResources()}.
 */
public interface AdditionalResources {

    /**
     * Adds a process model to be announced. The model must have been built for this client
     * instance. A {@code null} model is ignored.
     *
     * @param processModel the process model to announce
     * @return this builder, for call chaining
     * @throws com.im.njams.sdk.common.NjamsSdkRuntimeException if the model was created for a
     *                                                          different nJAMS instance, or its
     *                                                          path does not start with the client
     *                                                          path
     */
    AdditionalResources addProcessModel(ProcessModel processModel);

    /**
     * Adds global variables to be announced. A {@code null} map is ignored.
     *
     * @param variables the global variables to announce
     * @return this builder, for call chaining
     */
    AdditionalResources addGlobalVariables(Map<String, String> variables);

    /**
     * Adds an image, loaded from the given classpath resource, to be announced.
     *
     * @param key          the key of the image
     * @param resourcePath the classpath resource path of the image
     * @return this builder, for call chaining
     */
    AdditionalResources addImage(String key, String resourcePath);

    /**
     * Adds an image with an arbitrary supplier implementation to be announced. A {@code null}
     * supplier is ignored.
     *
     * @param imageSupplier the supplier used to find the image
     * @return this builder, for call chaining
     */
    AdditionalResources addImage(ImageSupplier imageSupplier);

    /**
     * Sends the collected resources that have not been announced yet as a single additional
     * project message. Resources whose identifier (process path, image name, or global-variable
     * name) was already announced are omitted. If nothing new remains, no message is sent.
     *
     * @throws com.im.njams.sdk.common.NjamsSdkRuntimeException if the client has not been started
     */
    void build();
}
