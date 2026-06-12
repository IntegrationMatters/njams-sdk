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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

import com.faizsiegeln.njams.messageformat.v4.common.TreeElementType;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.image.ImageSupplier;
import com.im.njams.sdk.model.image.ResourceImageSupplier;
import com.im.njams.sdk.model.layout.CommonBfsModelLayouter;
import com.im.njams.sdk.model.layout.ProcessModelLayouter;
import com.im.njams.sdk.model.svg.NjamsProcessDiagramFactory;
import com.im.njams.sdk.model.svg.ProcessDiagramFactory;

/**
 * Owns the process models, taxonomy tree, images and process diagram tooling of an
 * {@link Njams} client, and assembles and sends project messages.
 * Obtain via {@code njams.model()}.
 */
public final class NjamsModel {

    private final Njams njams;
    private final LifecycleState lifecycle;
    private final NjamsMetadata metadata;
    private final NjamsFeatures features;
    private final NjamsConfiguration configuration;
    private final Object projectMessageLock;

    // Path -> ProcessModel
    private final Map<Path, ProcessModel> processModels = new HashMap<>();

    // Images
    private final Collection<ImageSupplier> images = new HashSet<>();

    // tree representation for the client
    private final TaxonomyTree taxonomy;

    private final ProjectMessageAssembler assembler;

    private ProcessDiagramFactory processDiagramFactory;

    private ProcessModelLayouter processModelLayouter;

    NjamsModel(Njams njams, LifecycleState lifecycle, NjamsMetadata metadata, NjamsFeatures features,
        NjamsConfiguration configuration, Object projectMessageLock) {
        this.njams = njams;
        this.lifecycle = lifecycle;
        this.metadata = metadata;
        this.features = features;
        this.configuration = configuration;
        this.projectMessageLock = projectMessageLock;
        taxonomy = new TaxonomyTree(projectMessageLock);
        assembler = new ProjectMessageAssembler(metadata, features, configuration, projectMessageLock);
        processDiagramFactory = new NjamsProcessDiagramFactory(njams);
        processModelLayouter = new CommonBfsModelLayouter();
    }

    /**
     * Create a process and add it to this instance.
     *
     * @param absoluteProcessPath the absolute path of the process to create. It must start with
     *                            this client's path (see {@link NjamsMetadata#getClientPath()}); a
     *                            path relative to the client can be built with
     *                            {@code getClientPath().getOrCreateChild(...)}.
     * @return the new ProcessModel
     * @throws NjamsSdkRuntimeException if the path does not start with the client path
     */
    public ProcessModel create(final Path absoluteProcessPath) {
        return create(absoluteProcessPath, njams);
    }

    /**
     * Create a process directly below this client's path and add it to this instance. Convenience
     * for the common single-element case; equivalent to
     * {@code create(getClientPath().getOrCreateChild(processName))}.
     *
     * @param processName the name of the process as a single path segment directly below the
     *                    client path; must not be {@code null}, blank, or contain {@code >}
     * @return the new ProcessModel
     */
    public ProcessModel create(final String processName) {
        return create(metadata.getClientPath().getOrCreateChild(processName), njams);
    }

    /**
     * Variant that binds the created model to the given owner instance. Needed by the deprecated
     * facade method: when the {@link Njams} instance is proxied (e.g. a test spy), the model must
     * reference the proxy the caller is working with, not this facet's plain backreference.
     */
    ProcessModel create(final Path absoluteProcessPath, final Njams owner) {
        requireUnderClientPath(absoluteProcessPath);
        final ProcessModel model = new ProcessModel(absoluteProcessPath, owner);
        synchronized (projectMessageLock) {
            createTreeElements(absoluteProcessPath, TreeElementType.PROCESS);
            processModels.put(absoluteProcessPath, model);
        }
        return model;
    }

    private void requireUnderClientPath(final Path processPath) {
        if (processPath == null || !processPath.startsWith(metadata.getClientPath())) {
            throw new NjamsSdkRuntimeException(
                "Process path " + processPath + " must start with the client path "
                    + metadata.getClientPath());
        }
    }

    /**
     * Adds a process model to this instance. The model must be build for this instance.
     *
     * @param processModel The model to be added. A {@link NjamsSdkRuntimeException} is thrown if the given model was
     *                     created for another instance than this.
     */
    public void add(final ProcessModel processModel) {
        add(processModel, njams);
    }

    /** Owner-aware variant, see {@link #create(Path, Njams)}. */
    void add(final ProcessModel processModel, final Njams owner) {
        if (processModel == null) {
            return;
        }
        if (processModel.getNjams() != owner) {
            throw new NjamsSdkRuntimeException("Process model has been created for a different nJAMS instance.");
        }
        final Path modelPath = processModel.getPath();
        if (!modelPath.startsWith(metadata.getClientPath())) {
            throw new NjamsSdkRuntimeException("Process model path does not match this nJAMS instance.");
        }
        synchronized (projectMessageLock) {
            createTreeElements(modelPath, TreeElementType.PROCESS);
            processModels.put(modelPath, processModel);
        }
    }

    /**
     * Return the ProcessModel for the given absolute path.
     *
     * @param absoluteProcessPath the absolute path of the process model to get
     * @return the ProcessModel
     * @throws NjamsSdkRuntimeException if no process model exists for the given path
     */
    public ProcessModel get(final Path absoluteProcessPath) {
        final ProcessModel pm;
        synchronized (projectMessageLock) {
            pm = absoluteProcessPath == null ? null : processModels.get(absoluteProcessPath);
        }
        if (pm == null) {
            throw new NjamsSdkRuntimeException("ProcessModel not found for path " + absoluteProcessPath);
        }
        return pm;
    }

    /**
     * Return the ProcessModel for a process directly below this client's path. Convenience for the
     * common single-element case; equivalent to {@code get(getClientPath().getChild(processName))}.
     *
     * @param processName the name of the process as a single path segment directly below the
     *                    client path
     * @return the ProcessModel
     * @throws NjamsSdkRuntimeException if no process model exists for the given name
     */
    public ProcessModel get(final String processName) {
        return get(metadata.getClientPath().getChild(processName));
    }

    /**
     * Check for a process model under the given absolute path.
     *
     * @param absoluteProcessPath the absolute path of the process model to check
     * @return true if found else false
     */
    public boolean has(final Path absoluteProcessPath) {
        if (absoluteProcessPath == null) {
            return false;
        }
        synchronized (projectMessageLock) {
            return processModels.containsKey(absoluteProcessPath);
        }
    }

    /**
     * Check for a process model for a process directly below this client's path. Convenience for
     * the common single-element case; equivalent to
     * {@code has(getClientPath().getChild(processName))}.
     *
     * @param processName the name of the process as a single path segment directly below the
     *                    client path
     * @return true if found else false
     */
    public boolean has(final String processName) {
        return has(metadata.getClientPath().getChild(processName));
    }

    /**
     * Returns a collection of all process models
     *
     * @return Collection of all process models
     */
    public Collection<ProcessModel> getAll() {
        synchronized (projectMessageLock) {
            return Collections.unmodifiableCollection(processModels.values());
        }
    }

    /**
     * Adds an image for a given resource path. Images must be registered before {@code start()}:
     * additional project messages sent for lazily added model do not transport images yet, so
     * an image added after start would silently never reach the nJAMS server.
     *
     * @param key          the key of the image
     * @param resourcePath the path where to find the image
     * @throws NjamsSdkRuntimeException if the client has already been started
     */
    public void addImage(final String key, final String resourcePath) {
        lifecycle.requireNotStarted("NjamsModel.addImage");
        addImageInternal(new ResourceImageSupplier(key, resourcePath));
    }

    /**
     * Add an image with an arbitrary supplier implementation. Images must be registered before
     * {@code start()}: additional project messages sent for lazily added model do not
     * transport images yet, so an image added after start would silently never reach the nJAMS
     * server.
     *
     * @param imageSupplier the supplier used by SDK to find the image
     * @throws NjamsSdkRuntimeException if the client has already been started
     */
    public void addImage(final ImageSupplier imageSupplier) {
        lifecycle.requireNotStarted("NjamsModel.addImage");
        addImageInternal(imageSupplier);
    }

    void addImageInternal(final ImageSupplier imageSupplier) {
        synchronized (projectMessageLock) {
            images.add(imageSupplier);
        }
    }

    /**
     * Set the type for a TreeElment given by a path. Tree-element types are announced to the
     * nJAMS server in the project message at start and must be set before {@code start()}.
     *
     * @param path the path of the tree icon
     * @param type icon type of the tree element
     * @throws NjamsSdkRuntimeException if no tree element exists for the given path, or if the
     *                                  client has already been started
     */
    public void setTreeElementType(Path path, String type) {
        lifecycle.requireNotStarted("NjamsModel.setTreeElementType");
        setTreeElementTypeInternal(path, type);
    }

    void setTreeElementTypeInternal(Path path, String type) {
        taxonomy.setType(path, type);
    }

    /**
     * Returns the layouter that positions the elements of a process model when generating
     * its diagram.
     *
     * @return the processModelLayouter
     */
    public ProcessModelLayouter getLayouter() {
        return processModelLayouter;
    }

    /**
     * Sets the layouter that positions the elements of a process model when generating
     * its diagram.
     *
     * @param processModelLayouter the processModelLayouter to set
     */
    public void setLayouter(ProcessModelLayouter processModelLayouter) {
        this.processModelLayouter = processModelLayouter;
    }

    /**
     * Returns the factory that generates process diagrams.
     *
     * @return the ProcessDiagramFactory
     */
    public ProcessDiagramFactory getDiagramFactory() {
        return processDiagramFactory;
    }

    /**
     * Sets the factory that generates process diagrams.
     *
     * @param processDiagramFactory the processDiagramFactory to set
     */
    public void setDiagramFactory(ProcessDiagramFactory processDiagramFactory) {
        this.processDiagramFactory = processDiagramFactory;
    }

    /**
     * Flush all resources to the server by creating a new ProjectMessage. It
     * can only be flushed when the instance was started.
     */
    public void send() {
        addDefaultImagesIfNeededAndAbsent();
        taxonomy.markStarters(path -> Optional.ofNullable(processModels.get(path))
            .map(ProcessModel::isStarter).orElse(false));
        final ProjectMessage msg = assembler.buildFull(processModels.values(), images, taxonomy);
        njams.getSender().send(msg, metadata.getClientSessionId());
    }

    /**
     * Announce an additional process for an already started client.
     * This will create a small ProjectMessage only containing the new process.
     *
     * @param model the additional model to send
     */
    public void announce(final ProcessModel model) {
        if (!lifecycle.isStarted()) {
            throw new NjamsSdkRuntimeException("Njams is not started. Please use createProcess Method instead");
        }
        final ProjectMessage msg = assembler.buildAdditional(model, taxonomy);
        njams.getSender().send(msg, metadata.getClientSessionId());
    }

    /**
     * Adds images for the default taxonomy keys that are used by the tree, if no image has been
     * added for them yet.
     */
    private void addDefaultImagesIfNeededAndAbsent() {
        synchronized (projectMessageLock) {
            taxonomy.defaultImagesInUse().forEach((type, icon) -> {
                if (images.stream().noneMatch(i -> i.getName().equals(type))) {
                    // runs during send() - after start - so it must bypass the pre-start guard
                    addImageInternal(new ResourceImageSupplier(type, icon));
                }
            });
        }
    }

    /**
     * Create DomainObjectStructure which is the tree representation for the
     * client
     */
    void createTreeElements(Path path, TreeElementType targetDomainObjectType) {
        taxonomy.register(path, targetDomainObjectType);
    }
}
