package com.im.njams.sdk.logmessage;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.common.TreeElement;
import com.faizsiegeln.njams.messageformat.v4.common.TreeElementType;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.im.njams.sdk.client.NjamsMetadata;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.InstructionListener;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ProcessConfiguration;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.ProcessModelUtils;
import com.im.njams.sdk.model.image.ImageSupplier;
import com.im.njams.sdk.model.image.ResourceImageSupplier;
import com.im.njams.sdk.model.layout.ProcessModelLayouter;
import com.im.njams.sdk.model.layout.SimpleProcessModelLayouter;
import com.im.njams.sdk.model.svg.NjamsProcessDiagramFactory;
import com.im.njams.sdk.model.svg.ProcessDiagramFactory;
import com.im.njams.sdk.serializer.NjamsSerializers;
import com.im.njams.sdk.settings.Settings;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class NjamsProjectMessage implements InstructionListener {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NjamsProjectMessage.class);

    private static final String DEFAULT_TAXONOMY_ROOT_TYPE = "njams.taxonomy.root";
    private static final String DEFAULT_TAXONOMY_FOLDER_TYPE = "njams.taxonomy.folder";
    private static final String DEFAULT_TAXONOMY_CLIENT_TYPE = "njams.taxonomy.client";
    private static final String DEFAULT_TAXONOMY_PROCESS_TYPE = "njams.taxonomy.process";
    private static final String DEFAULT_TAXONOMY_ROOT_ICON = "images/root.png";
    private static final String DEFAULT_TAXONOMY_FOLDER_ICON = "images/folder.png";
    private static final String DEFAULT_TAXONOMY_CLIENT_ICON = "images/client.png";
    private static final String DEFAULT_TAXONOMY_PROCESS_ICON = "images/process.png";

    /**
     * default secure processing
     */
    private static final String DEFAULT_DISABLE_SECURE_PROCESSING = "false";

    // Also used for synchronizing access to the project message resources:
    // process-models, images, global-variables, tree-elements
    // Path -> ProcessModel
    private final Map<String, ProcessModel> processModels = new HashMap<>();

    // The start time of the engine
    private final LocalDateTime startTime;

    // Images
    private final Collection<ImageSupplier> images = new HashSet<>();

    // Name -> Value
    private final Map<String, String> globalVariables = new HashMap<>();

    // tree representation for the client
    private final List<TreeElement> treeElements;
    private final NjamsMetadata instanceMetadata;
    private final NjamsFeatures features;
    private final Configuration configuration;
    private final Sender sender;
    private final NjamsState state;
    private final NjamsJobs jobs;
    private final NjamsSerializers serializers;
    private final Settings settings;
    private ProcessDiagramFactory processDiagramFactory;
    private ProcessModelLayouter processModelLayouter;

    public NjamsProjectMessage(NjamsMetadata njamsMetadata, NjamsFeatures features, Configuration configuration,
        Sender sender, NjamsState state, NjamsJobs jobs, NjamsSerializers serializers, Settings settings) {
        this.settings = settings;
        this.startTime = DateTimeUtility.now();
        this.processModelLayouter = new SimpleProcessModelLayouter();
        this.processDiagramFactory = createProcessDiagramFactory();
        this.treeElements = new ArrayList<>();
        this.instanceMetadata = njamsMetadata;
        this.features = features;
        this.configuration = configuration;
        this.sender = sender;
        this.state = state;
        this.jobs = jobs;
        this.serializers = serializers;

        createTreeElements(instanceMetadata.clientPath, TreeElementType.CLIENT);
    }

    private ProcessDiagramFactory createProcessDiagramFactory() {
        ProcessDiagramFactory factory;
        if(shouldProcessDiagramsBeSecure()){
            factory = NjamsProcessDiagramFactory.createSecureDiagramFactory();
        }else{
            factory = NjamsProcessDiagramFactory.createNotSecureDiagramFactory();
        }
        return factory;
    }

    private boolean shouldProcessDiagramsBeSecure(){
        return !("true".equalsIgnoreCase(settings.getProperty(
            Settings.PROPERTY_DISABLE_SECURE_PROCESSING,
            DEFAULT_DISABLE_SECURE_PROCESSING)));
    }

    public Map<String, String> getGlobalVariables() {
        return globalVariables;
    }

    /**
     * Flush all Resources to the server by creating a new ProjectMessage. It
     * can only be flushed when the instance was started.
     */
    public void sendProjectMessage() {
        addDefaultImagesIfNeededAndAbsent();
        final ProjectMessage msg = new ProjectMessage();
        setStarters();
        msg.getTreeElements().addAll(treeElements);
        msg.setPath(instanceMetadata.clientPath.toString());
        msg.setClientVersion(instanceMetadata.clientVersion);
        msg.setSdkVersion(instanceMetadata.sdkVersion);
        msg.setCategory(instanceMetadata.category);
        msg.setStartTime(startTime);
        msg.setMachine(instanceMetadata.machine);
        msg.setFeatures(features.get());
        msg.setLogMode(configuration.getLogMode());
        synchronized (processModels) {
            processModels.values().stream().map(ProcessModel::getSerializableProcessModel)
                .forEach(ipm -> msg.getProcesses().add(ipm));
            images.forEach(i -> msg.getImages().put(i.getName(), i.getBase64Image()));
            msg.getGlobalVariables().putAll(globalVariables);
            LOG.debug("Sending project message with {} process-models, {} images, {} global-variables.",
                processModels.size(), images.size(), globalVariables.size());
        }
        sender.send(msg);
    }

    /**
     * Sets the TreeElements starter to true if the corresponding processModel
     * is a starter
     */
    private void setStarters() {
        treeElements.stream().filter(te -> te.getTreeElementType() == TreeElementType.PROCESS)
            .filter(te -> processModels.get(te.getPath()).isStarter()).forEach(te -> te.setStarter(true));
    }

    /**
     * Checks all tree elements if the given treeDefaultType has been used, and
     * adds the treeDefaultIcon if not images has been added yet
     *
     * @param treeDefaultType type of the tree element
     * @param treeDefaultIcon icon which should be added if needed
     */
    private void addDefaultImagesIfNeededAndAbsent(String treeDefaultType, String treeDefaultIcon) {
        synchronized (processModels) {
            boolean found = treeElements.stream().anyMatch(te -> te.getType().equals(treeDefaultType));
            if (found && images.stream().noneMatch(i -> i.getName().equals(treeDefaultType))) {
                addImage(new ResourceImageSupplier(treeDefaultType, treeDefaultIcon));
            }
        }
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
     * Create DomainObjectStructure which is the tree representation for the
     * client
     */
    void createTreeElements(Path path, TreeElementType targetDomainObjectType) {
        synchronized (processModels) {
            String currentPath = ">";
            for (int i = 0; i < path.getParts().size(); i++) {
                String part = path.getParts().get(i);
                currentPath += part + ">";
                final String finalPath = currentPath;
                boolean found = treeElements.stream().filter(d -> d.getPath().equals(finalPath)).findAny().isPresent();
                if (!found) {
                    TreeElementType domainObjectType = i == path.getParts().size() - 1 ? targetDomainObjectType : null;
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
     * Set the type for a TreeElment given by a path.
     *
     * @param path the path of the tree icon
     * @param type icon type of the tree element
     */
    public void setTreeElementType(Path path, String type) {
        synchronized (processModels) {
            TreeElement dos = treeElements.stream().filter(d -> d.getPath().equals(path.toString())).findAny()
                .orElse(null);
            if (dos == null) {
                throw new NjamsSdkRuntimeException("Unable to find DomainObjectStructure for path " + path);
            }
            dos.setType(type);
        }
    }

    /**
     * Send an additional process for an already started client.
     * This will create a small ProjectMessage only containing the new process.
     *
     * @param model the additional model to send
     */
    public void sendAdditionalProcess(final ProcessModel model) {
        if (!state.isStarted()) {
            throw new NjamsSdkRuntimeException("Njams is not started. Please use createProcess Method instead");
        }
        final ProjectMessage msg = new ProjectMessage();
        msg.setPath(instanceMetadata.clientPath.toString());
        msg.setClientVersion(instanceMetadata.clientVersion);
        msg.setSdkVersion(instanceMetadata.sdkVersion);
        msg.setCategory(instanceMetadata.category);
        msg.setStartTime(startTime);
        msg.setMachine(instanceMetadata.machine);
        msg.setFeatures(features.get());
        msg.setLogMode(configuration.getLogMode());

        addTreeElements(msg.getTreeElements(), instanceMetadata.clientPath, TreeElementType.CLIENT, false);
        addTreeElements(msg.getTreeElements(), model.getPath(), TreeElementType.PROCESS, model.isStarter());

        msg.getProcesses().add(model.getSerializableProcessModel());
        sender.send(msg);
    }

    private List<TreeElement> addTreeElements(List<TreeElement> treeElements, Path processPath,
        TreeElementType targetDomainObjectType, boolean isStarter) {
        String currentPath = ">";
        for (int i = 0; i < processPath.getParts().size(); i++) {
            String part = processPath.getParts().get(i);
            currentPath += part + ">";
            final String finalPath = currentPath;
            boolean found = treeElements.stream().filter(d -> d.getPath().equals(finalPath)).findAny().isPresent();
            if (!found) {
                TreeElementType domainObjectType =
                    i == processPath.getParts().size() - 1 ? targetDomainObjectType : null;
                String type = getTreeElementDefaultType(i == 0, domainObjectType);
                treeElements.add(new TreeElement(currentPath, part, type, domainObjectType));
            }
        }
        treeElements.stream().filter(te -> te.getTreeElementType() == TreeElementType.PROCESS)
            .forEach(te -> te.setStarter(isStarter));
        return treeElements;
    }

    public void addImage(ImageSupplier imageSupplier) {
        synchronized (processModels) {
            images.add(imageSupplier);
        }
    }

    public void addGlobalVariables(Map<String, String> globalVariables) {
        synchronized (processModels) {
            this.globalVariables.putAll(globalVariables);
        }
    }

    /**
     * Return the ProcessModel to the path;
     *
     * @param path the path where to get the ProcessModel from
     * @return the ProcessModel or {@link NjamsSdkRuntimeException}
     */
    public ProcessModel getProcessModel(final Path path) {
        if (!hasProcessModel(path)) {
            throw new NjamsSdkRuntimeException("ProcessModel not found for path " + path);
        }

        final List<String> parts = Stream.of(instanceMetadata.clientPath, path).map(Path::getParts)
            .flatMap(List::stream).collect(toList());
        synchronized (processModels) {
            final ProcessModel processModel = processModels.get(new Path(parts).toString());
            return processModel;
        }
    }

    /**
     * Check for a process model under that path
     *
     * @param path the path where to search for a {@link ProcessModel}.
     * @return true if found else false
     */
    @Deprecated
    public boolean hasProcessModel(final Path path) {
        if (path == null) {
            return false;
        }
        final List<String> parts = Stream.of(instanceMetadata.clientPath, path).map(Path::getParts)
            .flatMap(List::stream).collect(toList());
        synchronized (processModels) {
            final ProcessModel processModel = processModels.get(new Path(parts).toString());
            return processModel != null;
        }
    }

    /**
     * Returns a collection of all process models
     *
     * @return Collection of all process models
     */
    public Collection<ProcessModel> getProcessModels() {
        synchronized (processModels) {
            return Collections.unmodifiableCollection(processModels.values());
        }
    }

    @Override
    public void onInstruction(Instruction instruction) {
        if (Command.SEND_PROJECTMESSAGE.commandString().equalsIgnoreCase(instruction.getCommand())) {
            sendProjectMessage();
            final Response response = new Response();
            response.setResultCode(0);
            response.setResultMessage("Successfully sent ProjectMessage via NjamsClient");
            instruction.setResponse(response);
            LOG.debug("Sent ProjectMessage requested via Instruction via Njams");
        }
    }

    /**
     * Create a process and add it to this instance.
     *
     * @param path Relative path to the client of the process which should be
     *             created
     * @return the new ProcessModel or a {@link NjamsSdkRuntimeException}
     */
    public ProcessModel createProcess(final Path path) {
        final Path fullClientPath = path.addBase(instanceMetadata.clientPath);
        ProcessModelUtils processModelUtils = new ProcessModelUtils(jobs, instanceMetadata, serializers, configuration,
            settings, processDiagramFactory, processModelLayouter, sender);
        final ProcessModel model = new ProcessModel(fullClientPath, processModelUtils);
        synchronized (processModels) {
            createTreeElements(fullClientPath, TreeElementType.PROCESS);
            processModels.put(fullClientPath.toString(), model);
        }
        return model;
    }

    /**
     * Adds a process model to this instance. The model must be build for this instance.
     *
     * @param processModel The model to be added. A {@link NjamsSdkRuntimeException} is thrown if the given model was
     *                     created for another instance than this.
     */
    public void addProcessModel(final ProcessModel processModel) {
        if (processModel == null) {
            return;
        }
        if (processModel.getNjamsMetadata().equals(instanceMetadata)) {
            throw new NjamsSdkRuntimeException("Process model has been created for a different nJAMS instance.");
        }
        final List<String> clientParts = instanceMetadata.clientPath.getParts();
        final List<String> prefix = processModel.getPath().getParts().subList(0, clientParts.size());
        if (!clientParts.equals(prefix)) {
            throw new NjamsSdkRuntimeException("Process model path does not match this nJAMS instance.");
        }
        synchronized (processModels) {
            createTreeElements(processModel.getPath(), TreeElementType.PROCESS);
            processModels.put(processModel.getPath().toString(), processModel);
        }
    }

    /**
     * @param processModelLayouter the processModelLayouter to set
     */
    public void setProcessModelLayouter(ProcessModelLayouter processModelLayouter) {
        this.processModelLayouter = processModelLayouter;
    }

    /**
     * @return the processModelLayouter
     */
    public ProcessModelLayouter getProcessModelLayouter() {
        return processModelLayouter;
    }

    /**
     * Returns if the given process is excluded. This could be explicitly set on
     * the process, or if the Engine LogMode is set to none.
     *
     * @param processPath for the process which should be checked
     * @return true if the process is excluded, or false if not
     */
    public boolean isExcluded(Path processPath) {
        if (processPath == null) {
            return false;
        }
        if (configuration.getLogMode() == LogMode.NONE) {
            return true;
        }
        ProcessConfiguration processConfiguration = configuration.getProcess(processPath.toString());
        return processConfiguration != null && processConfiguration.isExclude();
    }

    /**
     * @return the ProcessDiagramFactory
     */
    public ProcessDiagramFactory getProcessDiagramFactory() {
        return processDiagramFactory;
    }

    /**
     * @param processDiagramFactory the processDiagramFactory to set
     */
    public void setProcessDiagramFactory(ProcessDiagramFactory processDiagramFactory) {
        this.processDiagramFactory = processDiagramFactory;
    }
}
