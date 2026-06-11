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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.common.TreeElement;
import com.faizsiegeln.njams.messageformat.v4.common.TreeElementType;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.im.njams.sdk.Njams.Feature;
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
 * Obtain via {@code njams.processes()}.
 */
public final class NjamsProcesses {

    private static final Logger LOG = LoggerFactory.getLogger(NjamsProcesses.class);

    private static final String DEFAULT_TAXONOMY_ROOT_TYPE = "njams.taxonomy.root";
    private static final String DEFAULT_TAXONOMY_FOLDER_TYPE = "njams.taxonomy.folder";
    private static final String DEFAULT_TAXONOMY_CLIENT_TYPE = "njams.taxonomy.client";
    private static final String DEFAULT_TAXONOMY_PROCESS_TYPE = "njams.taxonomy.process";
    private static final String DEFAULT_TAXONOMY_ROOT_ICON = "images/root.png";
    private static final String DEFAULT_TAXONOMY_FOLDER_ICON = "images/folder.png";
    private static final String DEFAULT_TAXONOMY_CLIENT_ICON = "images/client.png";
    private static final String DEFAULT_TAXONOMY_PROCESS_ICON = "images/process.png";

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
    private final List<TreeElement> treeElements = new ArrayList<>();

    private ProcessDiagramFactory processDiagramFactory;

    private ProcessModelLayouter processModelLayouter;

    NjamsProcesses(Njams njams, LifecycleState lifecycle, NjamsMetadata metadata, NjamsFeatures features,
        NjamsConfiguration configuration, Object projectMessageLock) {
        this.njams = njams;
        this.lifecycle = lifecycle;
        this.metadata = metadata;
        this.features = features;
        this.configuration = configuration;
        this.projectMessageLock = projectMessageLock;
        processDiagramFactory = new NjamsProcessDiagramFactory(njams);
        processModelLayouter = new CommonBfsModelLayouter();
    }

    /**
     * Create a process and add it to this instance.
     *
     * @param path Relative path to the client of the process which should be
     *             created
     * @return the new ProcessModel or a {@link NjamsSdkRuntimeException}
     */
    public ProcessModel create(final Path path) {
        return create(path, njams);
    }

    /**
     * Variant that binds the created model to the given owner instance. Needed by the deprecated
     * facade method: when the {@link Njams} instance is proxied (e.g. a test spy), the model must
     * reference the proxy the caller is working with, not this facet's plain backreference.
     */
    ProcessModel create(final Path path, final Njams owner) {
        final Path fullClientPath = metadata.getClientPath().resolveOrCreateChild(path.toString());
        final ProcessModel model = new ProcessModel(fullClientPath, owner);
        synchronized (projectMessageLock) {
            createTreeElements(fullClientPath, TreeElementType.PROCESS);
            processModels.put(fullClientPath, model);
        }
        return model;
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
        Path ancestor = modelPath;
        while (ancestor != null && ancestor != metadata.getClientPath()) {
            ancestor = ancestor.getParent();
        }
        if (ancestor == null) {
            throw new NjamsSdkRuntimeException("Process model path does not match this nJAMS instance.");
        }
        synchronized (projectMessageLock) {
            createTreeElements(modelPath, TreeElementType.PROCESS);
            processModels.put(modelPath, processModel);
        }
    }

    /**
     * Return the ProcessModel to the path;
     *
     * @param relativePath The relative path of the process model to get
     * @return the ProcessModel or {@link NjamsSdkRuntimeException}
     */
    public ProcessModel get(final Path relativePath) {
        final Path absolutePath = metadata.getClientPath().resolveChild(relativePath.toString());
        final ProcessModel pm;
        synchronized (projectMessageLock) {
            pm = absolutePath == null ? null : processModels.get(absolutePath);
        }
        if (pm == null) {
            throw new NjamsSdkRuntimeException("ProcessModel not found for path " + relativePath);
        }
        return pm;
    }

    /**
     * Check for a process model under that path
     *
     * @param relativePath The relative path of the process model to check
     * @return true if found else false
     */
    public boolean has(final Path relativePath) {
        if (relativePath == null) {
            return false;
        }
        final Path absolutePath = metadata.getClientPath().resolveChild(relativePath.toString());
        if (absolutePath == null) {
            return false;
        }
        synchronized (projectMessageLock) {
            return processModels.containsKey(absolutePath);
        }
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
     * additional project messages sent for lazily added processes do not transport images yet, so
     * an image added after start would silently never reach the nJAMS server.
     *
     * @param key          the key of the image
     * @param resourcePath the path where to find the image
     * @throws NjamsSdkRuntimeException if the client has already been started
     */
    public void addImage(final String key, final String resourcePath) {
        lifecycle.requireNotStarted("NjamsProcesses.addImage");
        addImageInternal(new ResourceImageSupplier(key, resourcePath));
    }

    /**
     * Add an image with an arbitrary supplier implementation. Images must be registered before
     * {@code start()}: additional project messages sent for lazily added processes do not
     * transport images yet, so an image added after start would silently never reach the nJAMS
     * server.
     *
     * @param imageSupplier the supplier used by SDK to find the image
     * @throws NjamsSdkRuntimeException if the client has already been started
     */
    public void addImage(final ImageSupplier imageSupplier) {
        lifecycle.requireNotStarted("NjamsProcesses.addImage");
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
        lifecycle.requireNotStarted("NjamsProcesses.setTreeElementType");
        setTreeElementTypeInternal(path, type);
    }

    void setTreeElementTypeInternal(Path path, String type) {
        synchronized (projectMessageLock) {
            TreeElement dos = treeElements.stream().filter(d -> d.getPath().equals(path.toString())).findAny()
                .orElse(null);
            if (dos == null) {
                throw new NjamsSdkRuntimeException("Unable to find DomainObjectStructure for path " + path);
            }
            dos.setType(type);
        }
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
        setStarters();
        final ProjectMessage msg = prepareProjectMessage();
        msg.getTreeElements().addAll(treeElements);
        synchronized (projectMessageLock) {
            processModels.values().stream().map(ProcessModel::getSerializableProcessModel)
                .forEach(ipm -> msg.getProcesses().add(ipm));
            images.forEach(i -> msg.getImages().put(i.getName(), i.getBase64Image()));
            msg.getGlobalVariables().putAll(metadata.getGlobalVariables());
            msg.setGlobalVariablesPattern(metadata.getGlobalVariablesPattern());
            LOG.debug("Sending project message with {} process-models, {} images, {} global-variables.",
                processModels.size(), images.size(), metadata.getGlobalVariables().size());
        }
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
        final ProjectMessage msg = prepareProjectMessage();
        addTreeElements(msg.getTreeElements(), metadata.getClientPath(), TreeElementType.CLIENT, false);
        addTreeElements(msg.getTreeElements(), model.getPath(), TreeElementType.PROCESS, model.isStarter());
        msg.getProcesses().add(model.getSerializableProcessModel());
        njams.getSender().send(msg, metadata.getClientSessionId());
    }

    /**
     * Initializes the common body of a project message.
     */
    private ProjectMessage prepareProjectMessage() {
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

    /**
     * Adds imgages for the default keys, if they are used and no image has benn
     * added for them
     */
    private void addDefaultImagesIfNeededAndAbsent() {
        addDefaultImagesIfNeededAndAbsent(DEFAULT_TAXONOMY_FOLDER_TYPE, DEFAULT_TAXONOMY_FOLDER_ICON);
        addDefaultImagesIfNeededAndAbsent(DEFAULT_TAXONOMY_ROOT_TYPE, DEFAULT_TAXONOMY_ROOT_ICON);
        addDefaultImagesIfNeededAndAbsent(DEFAULT_TAXONOMY_CLIENT_TYPE, DEFAULT_TAXONOMY_CLIENT_ICON);
        addDefaultImagesIfNeededAndAbsent(DEFAULT_TAXONOMY_PROCESS_TYPE, DEFAULT_TAXONOMY_PROCESS_ICON);
    }

    /**
     * Checks all tree elements if the given treeDefaultType has been used, and
     * adds the treeDefaultIcon if not images has been added yet
     *
     * @param treeDefaultType type of the tree element
     * @param treeDefaultIcon icon which should be added if needed
     */
    private void addDefaultImagesIfNeededAndAbsent(String treeDefaultType, String treeDefaultIcon) {
        synchronized (projectMessageLock) {
            boolean found = treeElements.stream().anyMatch(te -> te.getType().equals(treeDefaultType));
            if (found && images.stream().noneMatch(i -> i.getName().equals(treeDefaultType))) {
                // runs during send() - after start - so it must bypass the pre-start guard
                addImageInternal(new ResourceImageSupplier(treeDefaultType, treeDefaultIcon));
            }
        }
    }

    /**
     * Create DomainObjectStructure which is the tree representation for the
     * client
     */
    void createTreeElements(Path path, TreeElementType targetDomainObjectType) {
        synchronized (projectMessageLock) {
            final List<String> parts = path.getSegments();
            String currentPath = ">";
            for (int i = 0; i < parts.size(); i++) {
                String part = parts.get(i);
                currentPath += part + ">";
                final String finalPath = currentPath;
                boolean found = treeElements.stream().filter(d -> d.getPath().equals(finalPath)).findAny().isPresent();
                if (!found) {
                    TreeElementType domainObjectType = i == parts.size() - 1 ? targetDomainObjectType : null;
                    String type = getTreeElementDefaultType(i == 0, domainObjectType);
                    treeElements.add(new TreeElement(currentPath, part, type, domainObjectType));
                }
            }
        }
    }

    /**
     * Returns the default icon type for a TreeElement, based on the criterias
     * first and TreeElementType
     *
     * @param first          Is this the root element
     * @param treeElmentType The treeElementType
     * @return the icon type
     */
    private String getTreeElementDefaultType(boolean first, TreeElementType treeElmentType) {
        String type = DEFAULT_TAXONOMY_FOLDER_TYPE;
        if (first) {
            type = DEFAULT_TAXONOMY_ROOT_TYPE;
        } else if (treeElmentType == TreeElementType.CLIENT) {
            type = DEFAULT_TAXONOMY_CLIENT_TYPE;
        } else if (treeElmentType == TreeElementType.PROCESS) {
            type = DEFAULT_TAXONOMY_PROCESS_TYPE;
        }
        return type;
    }

    /**
     * Sets the TreeElements starter flag according to the corresponding processModel
     */
    private void setStarters() {
        treeElements.stream().filter(t -> t.getTreeElementType() == TreeElementType.PROCESS)
            .forEach(t -> t.setStarter(
                Optional.ofNullable(
                    processModels.get(Path.resolve(t.getPath()))).map(ProcessModel::isStarter).orElse(false)));
    }

    private List<TreeElement> addTreeElements(List<TreeElement> treeElements, Path processPath,
        TreeElementType targetDomainObjectType, boolean isStarter) {
        final List<String> parts = processPath.getSegments();
        String currentPath = ">";
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            currentPath += part + ">";
            final String finalPath = currentPath;
            boolean found = treeElements.stream().filter(d -> d.getPath().equals(finalPath)).findAny().isPresent();
            if (!found) {
                TreeElementType domainObjectType =
                    i == parts.size() - 1 ? targetDomainObjectType : null;
                String type = getTreeElementDefaultType(i == 0, domainObjectType);
                treeElements.add(new TreeElement(currentPath, part, type, domainObjectType));
            }
        }
        treeElements.stream().filter(te -> te.getTreeElementType() == TreeElementType.PROCESS)
            .forEach(te -> te.setStarter(isStarter));
        return treeElements;
    }
}
