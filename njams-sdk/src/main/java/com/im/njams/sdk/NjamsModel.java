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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
public class NjamsModel {

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

    // Identifiers of resources already announced to the server (start message or an additional
    // message), so an additional-resources message never repeats data the server already knows.
    private final Set<Path> announcedProcessPaths = new HashSet<>();
    private final Set<String> announcedImageNames = new HashSet<>();
    private final Set<String> announcedGlobalVariableNames = new HashSet<>();

    // tree representation for the client
    private final TaxonomyTree taxonomy;

    private final GlobalVariables globalVariables;

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
        globalVariables = new GlobalVariables(projectMessageLock);
        assembler = new ProjectMessageAssembler(metadata, features, configuration, globalVariables,
            projectMessageLock);
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
     * Adds a process model to this instance. The model must be built for this instance and must be
     * added before {@code start()}: process models are announced to the nJAMS server in the project
     * message assembled at start, so a model added afterwards would silently never reach the server.
     * To register a process once the client is already started, use {@link #create(Path)} and
     * announce it with the {@link #additionalResources()} builder.
     *
     * @param processModel The model to be added. A {@link NjamsSdkRuntimeException} is thrown if the given model was
     *                     created for another instance than this.
     * @throws NjamsSdkRuntimeException if the client has already been started
     */
    public void add(final ProcessModel processModel) {
        lifecycle.requireNotStarted("NjamsModel.add");
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
     * Returns the global variables of this client.
     *
     * @return the globalVariables
     */
    public Map<String, String> getGlobalVariables() {
        return globalVariables.getAll();
    }

    /**
     * Adds the given global variables to this instance's global variables. Global variables are
     * announced to the nJAMS server in the project message when the client starts.
     *
     * @param globalVariables The global variables to be added to this instance.
     * @return this facet, for call chaining
     * @throws NjamsSdkRuntimeException if the client has already been started — a later change
     *                                  would never reach the server
     */
    public NjamsModel addGlobalVariables(Map<String, String> globalVariables) {
        lifecycle.requireNotStarted("NjamsModel.addGlobalVariables");
        addGlobalVariablesInternal(globalVariables);
        return this;
    }

    void addGlobalVariablesInternal(Map<String, String> globalVariables) {
        this.globalVariables.add(globalVariables);
    }

    /**
     * Returns the regular expression that defines how global-variable references are detected and replaced in this
     * client's configurations. When {@code null}, the nJAMS server applies its own default matching behavior.
     *
     * @return the global-variable matching pattern, or {@code null} if none was set
     */
    public String getGlobalVariablesPattern() {
        return globalVariables.getPattern();
    }

    /**
     * Sets the regular expression that defines how global-variable references are detected and replaced in this
     * client's configurations, overriding the nJAMS server's default matching. The pattern must use named groups:
     * {@code full} (the entire reference, e.g. {@code %%var%%}) and {@code name} (the variable name) are required;
     * {@code default} (a fallback value) and {@code optional} (any non-blank match marks the reference as optional)
     * are optional. The pattern is transported to the server with the project message.
     *
     * @param globalVariablesPattern the regex pattern, or {@code null} to clear it and let the server apply its
     *                               default behavior
     * @return this facet, for call chaining
     * @throws NjamsSdkRuntimeException if the pattern is not a valid regular expression or does not declare the
     *                                  required named groups {@code full} and {@code name}, or if the client has
     *                                  already been started — a later change would never reach the server
     */
    public NjamsModel setGlobalVariablesPattern(String globalVariablesPattern) {
        lifecycle.requireNotStarted("NjamsModel.setGlobalVariablesPattern");
        setGlobalVariablesPatternInternal(globalVariablesPattern);
        return this;
    }

    void setGlobalVariablesPatternInternal(String globalVariablesPattern) {
        globalVariables.setPattern(globalVariablesPattern);
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
        final ProjectMessage msg;
        synchronized (projectMessageLock) {
            msg = assembler.buildFull(processModels.values(), images, taxonomy);
            markAllAnnounced();
        }
        njams.getSender().send(msg, metadata.getClientSessionId());
    }

    /**
     * Returns a builder for announcing additional project resources — process models, global
     * variables, and images — to the nJAMS server after the client has started. See
     * {@link AdditionalResources} for the announce-once semantics.
     *
     * @return a new additional-resources builder bound to this instance
     */
    public AdditionalResources additionalResources() {
        return additionalResources(njams);
    }

    /** Owner-aware variant, see {@link #create(Path, Njams)}. */
    AdditionalResources additionalResources(final Njams owner) {
        return new AdditionalResourcesBuilder(owner);
    }

    /** Marks every resource currently held by this instance as announced to the server. */
    private void markAllAnnounced() {
        announcedProcessPaths.addAll(processModels.keySet());
        images.forEach(image -> announcedImageNames.add(image.getName()));
        announcedGlobalVariableNames.addAll(globalVariables.getAll().keySet());
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

    /**
     * Package-private implementation of {@link AdditionalResources}. Stages resources and, on
     * {@link #build()}, transmits only those whose identifier (process path, image name, or
     * global-variable name) has not been announced yet.
     */
    private final class AdditionalResourcesBuilder implements AdditionalResources {

        private final Njams owner;
        private final Map<Path, ProcessModel> stagedModels = new LinkedHashMap<>();
        private final Map<String, String> stagedGlobalVariables = new LinkedHashMap<>();
        private final Map<String, ImageSupplier> stagedImages = new LinkedHashMap<>();

        private AdditionalResourcesBuilder(final Njams owner) {
            this.owner = owner;
        }

        @Override
        public AdditionalResources addProcessModel(final ProcessModel processModel) {
            if (processModel == null) {
                return this;
            }
            if (processModel.getNjams() != owner) {
                throw new NjamsSdkRuntimeException("Process model has been created for a different nJAMS instance.");
            }
            if (!processModel.getPath().startsWith(metadata.getClientPath())) {
                throw new NjamsSdkRuntimeException("Process model path does not match this nJAMS instance.");
            }
            stagedModels.put(processModel.getPath(), processModel);
            return this;
        }

        @Override
        public AdditionalResources addGlobalVariables(final Map<String, String> variables) {
            if (variables != null) {
                stagedGlobalVariables.putAll(variables);
            }
            return this;
        }

        @Override
        public AdditionalResources addImage(final String key, final String resourcePath) {
            return addImage(new ResourceImageSupplier(key, resourcePath));
        }

        @Override
        public AdditionalResources addImage(final ImageSupplier imageSupplier) {
            if (imageSupplier != null) {
                stagedImages.put(imageSupplier.getName(), imageSupplier);
            }
            return this;
        }

        @Override
        public void build() {
            lifecycle.requireStarted();
            synchronized (projectMessageLock) {
                final List<ProcessModel> newModels = new ArrayList<>();
                for (final ProcessModel model : stagedModels.values()) {
                    if (announcedProcessPaths.add(model.getPath())) {
                        createTreeElements(model.getPath(), TreeElementType.PROCESS);
                        processModels.put(model.getPath(), model);
                        newModels.add(model);
                    }
                }
                final Map<String, String> newGlobalVariables = new LinkedHashMap<>();
                stagedGlobalVariables.forEach((name, value) -> {
                    if (announcedGlobalVariableNames.add(name)) {
                        newGlobalVariables.put(name, value);
                    }
                });
                final List<ImageSupplier> newImages = new ArrayList<>();
                for (final ImageSupplier image : stagedImages.values()) {
                    if (announcedImageNames.add(image.getName())) {
                        newImages.add(image);
                    }
                }
                if (newModels.isEmpty() && newGlobalVariables.isEmpty() && newImages.isEmpty()) {
                    return;
                }
                if (!newGlobalVariables.isEmpty()) {
                    addGlobalVariablesInternal(newGlobalVariables);
                }
                newImages.forEach(NjamsModel.this::addImageInternal);
                final ProjectMessage msg =
                    assembler.buildAdditional(newModels, newImages, newGlobalVariables, taxonomy);
                njams.getSender().send(msg, metadata.getClientSessionId());
            }
        }
    }
}
