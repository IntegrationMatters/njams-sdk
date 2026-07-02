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

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.common.TreeElementType;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.im.njams.sdk.Njams.Feature;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.image.ImageSupplier;

/**
 * Internal collaborator of {@link NjamsModel}: assembles the {@link ProjectMessage} sent to the
 * nJAMS server, both the full message at start and the small additional-process message.
 * Confines the relocated/wire-format {@link ProjectMessage} construction to internal code. Not
 * part of the public API.
 */
final class ProjectMessageAssembler {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectMessageAssembler.class);

    private final NjamsMetadata metadata;
    private final NjamsFeatures features;
    private final NjamsConfiguration configuration;
    private final GlobalVariables globalVariables;
    private final Object projectMessageLock;

    ProjectMessageAssembler(NjamsMetadata metadata, NjamsFeatures features, NjamsConfiguration configuration,
        GlobalVariables globalVariables, Object projectMessageLock) {
        this.metadata = metadata;
        this.features = features;
        this.configuration = configuration;
        this.globalVariables = globalVariables;
        this.projectMessageLock = projectMessageLock;
    }

    /**
     * Builds the full project message: header, all process models, images, global variables and
     * the complete taxonomy tree.
     */
    ProjectMessage buildFull(Collection<ProcessModel> processModels, Collection<ImageSupplier> images,
        TaxonomyTree taxonomy) {
        final ProjectMessage msg = prepare();
        taxonomy.copyInto(msg.getTreeElements());
        synchronized (projectMessageLock) {
            processModels.stream().map(ProcessModel::getSerializableProcessModel)
                .forEach(ipm -> msg.getProcesses().add(ipm));
            images.forEach(i -> msg.getImages().put(i.getName(), i.getBase64Image()));
            msg.getGlobalVariables().putAll(globalVariables.getAll());
            msg.setGlobalVariablesPattern(globalVariables.getPattern());
            LOG.debug("Sending project message with {} process-models, {} images, {} global-variables.",
                processModels.size(), images.size(), globalVariables.getAll().size());
        }
        return msg;
    }

    /**
     * Builds an additional project message containing only the given process models, images and
     * global variables, plus the tree elements leading to each process. Used to announce
     * resources added after the client has started.
     */
    ProjectMessage buildAdditional(Collection<ProcessModel> models, Collection<ImageSupplier> images,
        Map<String, String> globalVariables, TaxonomyTree taxonomy) {
        final ProjectMessage msg = prepare();
        taxonomy.buildInto(msg.getTreeElements(), metadata.getClientPath(), TreeElementType.CLIENT, false);
        for (final ProcessModel model : models) {
            taxonomy.buildInto(msg.getTreeElements(), model.getPath(), TreeElementType.PROCESS, model.isStarter());
            msg.getProcesses().add(model.getSerializableProcessModel());
        }
        images.forEach(image -> msg.getImages().put(image.getName(), image.getBase64Image()));
        msg.getGlobalVariables().putAll(globalVariables);
        return msg;
    }

    /**
     * Initializes the common header of a project message.
     */
    private ProjectMessage prepare() {
        final ProjectMessage msg = new ProjectMessage();
        msg.setPath(metadata.getClientPath().toString());
        msg.setClientVersion(metadata.getClientVersion());
        msg.setSdkVersion(metadata.getSdkVersion());
        msg.setRuntimeVersion(metadata.getRuntimeVersion());
        msg.setCategory(metadata.getCategory());
        msg.setStartTime(metadata.getStartTime());
        msg.setMachine(metadata.getMachine());
        msg.setFeatures(features.list().stream().map(Feature::key).collect(Collectors.toList()));
        msg.setLogMode(configuration.getLogMode());
        msg.setClientId(metadata.getClientSessionId());
        msg.setRecording(configuration.get().isRecording());
        return msg;
    }
}
