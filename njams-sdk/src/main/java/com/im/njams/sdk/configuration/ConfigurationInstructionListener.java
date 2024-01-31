/*
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.configuration;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ExtractRule;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.RuleType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.InstructionListener;
import com.im.njams.sdk.logmessage.ExtractHandler;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.utils.JsonUtils;
import com.im.njams.sdk.utils.StringUtils;

/**
 * InstructionListener implementation for all instructions which will modify
 * configuration values.
 *
 * @author pnientiedt
 */
public class ConfigurationInstructionListener implements InstructionListener {

    private static final Pattern ATTRIBUTE_NAME_PATTERN = Pattern.compile("^[^\\.\\s]([^\\.]*[^\\.\\s])*$");

    private static class InstructionSupport {
        private final Response response;
        private final Instruction instruction;

        private InstructionSupport(final Instruction instruction) {
            this.instruction = instruction;

            // initialize success response; overwritten by error(...) methods.
            response = new Response();
            response.setResultCode(0);
            response.setResultMessage("Success");
        }

        /**
         * SDK-148 Sets the response into the instruction, if the command was handled.
         */
        private void applyResponse() {
            instruction.setResponse(response);
        }

        /**
         * Returns <code>true</code>, only if all given parameters exist and have none-blank values.
         * Sets according {@link #error(String)} response.
         *
         * @param names
         * @return
         */
        private boolean validate(String... names) {
            Collection<String> missing =
                Arrays.stream(names).filter(n -> StringUtils.isBlank(getParameter(n))).collect(Collectors.toList());
            if (!missing.isEmpty()) {
                error("missing parameter(s) " + missing);
                return false;
            }

            return true;
        }

        /**
         * Returns <code>true</code> only if the given parameter's value can be parsed to an instance of the given
         * enumeration type.
         * Sets according {@link #error(String)} response.
         *
         * @param name
         * @param enumeration
         * @return
         */
        private <T extends Enum<T>> boolean validate(final String name, final Class<T> enumeration) {
            if (getEnumParameter(name, enumeration) == null) {
                error("could not parse value of parameter [" + name + "] value=" + getParameter(name));
                return false;
            }
            return true;
        }

        /**
         * Creates an error response with the given message.
         *
         * @param message
         */
        private void error(final String message) {
            error(message, null);
        }

        private void error(final String message, final Exception e) {
            error(message, e, false);
        }

        /**
         * Creates an error response with the given message and exception.
         *
         * @param message
         * @param e
         * @param silent if set to <code>true</code> no error is logged.
         */
        private void error(final String message, final Exception e, boolean silent) {
            if (!silent) {
                String on = null;
                final String process = getProcessPath();
                if (StringUtils.isNotBlank(process)) {
                    on = " on process: " + process;
                    final String activity = getActivityId();
                    if (StringUtils.isNotBlank(activity)) {
                        on += "#" + activity;
                    }
                }
                LOG.error("Failed to execute command [{}]{}. Reason: {}", instruction.getCommand(),
                    on != null ? on : "", message, e);
            }
            response.setResultCode(1);
            response.setResultMessage(message + (e != null ? ": " + e.getMessage() : ""));
        }

        /**
         * Whether or not the response is flagged as error.
         *
         * @return
         */
        private boolean isError() {
            return response.getResultCode() == 1;
        }

        /**
         * Sets a response parameter.
         *
         * @param name  The name of the parameter to set into the response.
         * @param value The value to set for the given parameter.
         * @return
         */
        private InstructionSupport setParameter(final String name, final Object value) {
            if (value instanceof LocalDateTime) {
                response.getParameters().put(name, DateTimeUtility.toString((LocalDateTime) value));
            } else {
                response.getParameters().put(name, value == null ? null : String.valueOf(value));
            }
            return this;
        }

        /**
         * Returns the process path from the request, if set.
         *
         * @return
         */
        private String getProcessPath() {
            return getParameter(PROCESS_PATH);
        }

        /**
         * Returns the activity ID from the request, if any.
         *
         * @return
         */
        private String getActivityId() {
            return getParameter(ACTIVITY_ID);
        }

        /**
         * Whether or not the request has a parameter with the given name.
         *
         * @param name The parameter name to test.
         * @return
         */
        private boolean hasParameter(final String name) {
            return instruction.getRequest().getParameters().keySet().stream().anyMatch(k -> k.equalsIgnoreCase(name));
        }

        /**
         * Returns a request parameter.
         *
         * @param name The name of the parameter to return.
         * @return
         */
        private String getParameter(final String name) {
            return instruction.getRequest().getParameters().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name)).map(Entry::getValue)
                .findAny().orElse(null);
        }

        /**
         * Returns a request parameter as integer.
         *
         * @param name The name of the parameter to return.
         * @return
         */
        private int getIntParameter(final String name) {
            final String s = getParameter(name);
            try {
                return s == null ? 0 : Integer.parseInt(s);
            } catch (final NumberFormatException e) {
                LOG.error("Failed to parse parameter {} from request.", name, e);
                return 0;
            }
        }

        /**
         * Returns a request parameter as boolean.
         *
         * @param name The name of the parameter to return.
         * @return
         */
        private boolean getBoolParameter(final String name) {
            return getParameter(name, Boolean::parseBoolean);
        }

        /**
         * Returns a request parameter as enum value.
         *
         * @param <T>         The type of the enum.
         * @param name        The name of the parameter to return.
         * @param enumeration The enum type.
         * @return
         */
        private <T extends Enum<T>> T getEnumParameter(final String name, final Class<T> enumeration) {
            final String constantName = getParameter(name);
            if (constantName == null || enumeration == null || enumeration.getEnumConstants() == null) {
                return null;
            }
            return Arrays.stream(enumeration.getEnumConstants()).filter(c -> c.name().equalsIgnoreCase(constantName))
                .findAny().orElse(null);
        }

        /**
         * Returns a request parameter build by invoking the given producer method, passing in the value read from
         * the request.
         *
         * @param <T>      The target type to produce.
         * @param name     The name of the parameter to return.
         * @param producer The producer method accepting the raw string parameter value from the request.
         * @return
         */
        private <T> T getParameter(final String name, Function<String, T> producer) {
            return producer.apply(getParameter(name));
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationInstructionListener.class);
    private static final String PROCESS_PATH = "processPath";
    private static final String ACTIVITY_ID = "activityId";
    private static final String LOG_LEVEL = "logLevel";
    private static final String LOG_MODE = "logMode";
    private static final String ITERATIONS = "iterations";
    private static final String DEEP_TRACE = "deeptrace";
    private static final String RECORD = "Record";
    private static final String ENGINE_WIDE_RECORDING = "EngineWideRecording";
    private static final String EXTRACT = "extract";
    private static final String STARTTIME = "starttime";
    private static final String ENABLE_TRACING = "enableTracing";
    private static final String ENDTIME = "endtime";
    private static final String EXCLUDE = "exclude";
    private static final String MATCH_RESULT = "matchResult";
    private static final String DATA = "data";
    private static final String EXPRESSION = "expression";
    private static final String RULE_TYPE = "ruleType";

    private final Configuration configuration;
    private final Njams njams;

    /**
     * Initialize ConfigurationInstructionListener
     *
     * @param njams The {@link Njams} instance for that this listener is registered.
     */
    public ConfigurationInstructionListener(Njams njams) {
        this.njams = njams;
        configuration = njams.getConfiguration();
    }

    /**
     * Configure configuration if a valid instruction will be given.
     *
     * @param instruction to validate
     */
    @Override
    public void onInstruction(final Instruction instruction) {
        final Command command = Command.getFromInstruction(instruction);
        final InstructionSupport instructionSupport = new InstructionSupport(instruction);
        if (command == null) {
            instructionSupport.error("Missing or unsupported command [" + instruction.getCommand()
                + "] in instruction.");
            instructionSupport.applyResponse();
            return;
        }
        LOG.debug("Received command: {}", command);
        switch (command) {
        case CONFIGURE_EXTRACT:
            configureExtract(instructionSupport);
            break;
        case DELETE_EXTRACT:
            deleteExtract(instructionSupport);
            break;
        case GET_EXTRACT:
            getExtract(instructionSupport);
            break;
        case GET_LOG_LEVEL:
            getLogLevel(instructionSupport);
            break;
        case GET_LOG_MODE:
            getLogMode(instructionSupport);
            break;
        case GET_RECORDING:
            getReording(instructionSupport);
            break;
        case GET_TRACING:
            getTracing(instructionSupport);
            break;
        case RECORD:
            record(instructionSupport);
            break;
        case SET_LOG_LEVEL:
            setLogLevel(instructionSupport);
            break;
        case SET_LOG_MODE:
            setLogMode(instructionSupport);
            break;
        case SET_TRACING:
            setTracing(instructionSupport);
            break;
        case TEST_EXPRESSION:
            testExpression(instructionSupport);
            break;

        // non-config commands
        case GET_REQUEST_HANDLER:
        case SEND_PROJECTMESSAGE:
        case REPLAY:
        case PING:
            // not handled here.
            LOG.debug("Ignoring command: {}", command);
            return;

        default:
            LOG.debug("Unknown command: {}", command);
            return;
        }

        // set response into instruction
        instructionSupport.applyResponse();
        LOG.debug("Handled command: {} (result={}) on process: {}{}", command, instructionSupport.isError() ? "error"
            : "ok", instructionSupport.getProcessPath(),
            instructionSupport.getActivityId() == null ? ""
                : "#"
                    + instructionSupport.getActivityId());
    }

    private void getLogLevel(final InstructionSupport instructionSupport) {
        //fetch parameters
        if (!instructionSupport.validate(PROCESS_PATH)) {
            return;
        }
        final String processPath = instructionSupport.getProcessPath();

        //execute action
        // init with defaults
        LogLevel logLevel = LogLevel.INFO;
        boolean exclude = configuration.hasProcessExcludeFilter(new Path(processPath));

        // differing config stored?
        final ProcessConfiguration process = configuration.getProcess(processPath);
        if (process != null) {
            logLevel = process.getLogLevel();
        }

        instructionSupport.setParameter(LOG_LEVEL, logLevel.name()).setParameter(EXCLUDE, exclude)
            .setParameter(LOG_MODE, configuration.getLogMode());

        LOG.debug("Return LogLevel for {}", processPath);
    }

    private void setLogLevel(final InstructionSupport instructionSupport) {
        if (!instructionSupport.validate(PROCESS_PATH, LOG_LEVEL)
            || !instructionSupport.validate(LOG_LEVEL, LogLevel.class)) {
            return;
        }
        //fetch parameters
        final String processPath = instructionSupport.getProcessPath();
        final LogLevel loglevel = instructionSupport.getEnumParameter(LOG_LEVEL, LogLevel.class);

        //execute action
        ProcessConfiguration process = configuration.getProcess(processPath);
        final LogMode logMode = instructionSupport.getEnumParameter(LOG_MODE, LogMode.class);
        if (logMode != null) {
            configuration.setLogMode(logMode);
        }
        process.setLogLevel(loglevel);
        configuration.setProcessExcluded(new Path(processPath), instructionSupport.getBoolParameter(EXCLUDE));
        process.setExclude(null);
        saveConfiguration(instructionSupport);
    }

    private void getLogMode(final InstructionSupport instructionSupport) {
        instructionSupport.setParameter(LOG_MODE, configuration.getLogMode());
        LOG.debug("Return LogMode: {}", configuration.getLogMode());
    }

    private void setLogMode(final InstructionSupport instructionSupport) {
        if (!instructionSupport.validate(LOG_MODE) || !instructionSupport.validate(LOG_MODE, LogMode.class)) {
            return;
        }
        //fetch parameters
        final LogMode logMode = instructionSupport.getEnumParameter(LOG_MODE, LogMode.class);
        configuration.setLogMode(logMode);
        saveConfiguration(instructionSupport);
        LOG.debug("Set LogMode to {}", logMode);
    }

    private void setTracing(final InstructionSupport instructionSupport) {
        if (!instructionSupport.validate(PROCESS_PATH, ACTIVITY_ID)) {
            return;
        }
        //fetch parameters

        LocalDateTime endTime;
        try {
            endTime = parseDateTime(instructionSupport.getParameter(ENDTIME));
        } catch (final Exception e) {
            instructionSupport.error("Unable to parse end-time from tracepoint.", e);
            return;
        }
        if (endTime == null) {
            endTime = DateTimeUtility.now().plusMinutes(15);
        }
        if (instructionSupport.getBoolParameter(ENABLE_TRACING) && endTime.isAfter(DateTimeUtility.now())) {
            LOG.debug("Update tracepoint.");
            updateTracePoint(instructionSupport, endTime);
        } else {
            LOG.debug("Delete tracepoint.");
            deleteTracePoint(instructionSupport);
        }
    }

    private LocalDateTime parseDateTime(final String dateTime) {
        if (StringUtils.isBlank(dateTime)) {
            return null;
        }
        return DateTimeUtility.fromString(dateTime);
    }

    private void updateTracePoint(final InstructionSupport instructionSupport, final LocalDateTime endTime) {
        LocalDateTime startTime;
        try {
            startTime = parseDateTime(instructionSupport.getParameter(STARTTIME));
        } catch (final Exception e) {
            instructionSupport.error("Unable to parse start-time from tracepoint.", e);
            return;
        }
        if (startTime == null) {
            startTime = DateTimeUtility.now();
        }

        final String processPath = instructionSupport.getProcessPath();
        final String activityId = instructionSupport.getActivityId();

        //execute action
        ProcessConfiguration process = configuration.getProcess(processPath);
        ActivityConfiguration activity = process.getActivity(activityId);
        if (activity == null) {
            activity = new ActivityConfiguration();
            process.getActivities().put(activityId, activity);
        }
        final TracepointExt tp = new TracepointExt();
        tp.setStarttime(startTime);
        tp.setEndtime(endTime);
        tp.setIterations(instructionSupport.getIntParameter(ITERATIONS));
        tp.setDeeptrace(instructionSupport.getBoolParameter(DEEP_TRACE));
        activity.setTracepoint(tp);
        saveConfiguration(instructionSupport);
        LOG.debug("Tracepoint on {}#{} updated", processPath, activityId);
    }

    private void deleteTracePoint(final InstructionSupport instructionSupport) {
        //execute action
        final String processPath = instructionSupport.getProcessPath();
        final String activityId = instructionSupport.getActivityId();

        final ProcessConfiguration process = configuration.getProcess(processPath);
        if (process == null) {
            LOG.debug("Delete tracepoint: no process configuration for: {}", processPath);
            return;
        }
        final ActivityConfiguration activity = process.getActivity(activityId);
        if (activity == null) {
            LOG.debug("Delete tracepoint: no activity configuration for: {}#{}", processPath, activityId);
            return;
        }
        activity.setTracepoint(null);
        saveConfiguration(instructionSupport);
        LOG.debug("Tracepoint on {}#{} deleted", processPath, activityId);
    }

    private void configureExtract(final InstructionSupport instructionSupport) {
        if (!instructionSupport.validate(PROCESS_PATH, ACTIVITY_ID, EXTRACT)) {
            return;
        }
        //fetch parameters
        final String processPath = instructionSupport.getProcessPath();
        final String activityId = instructionSupport.getActivityId();
        final String extractString = instructionSupport.getParameter(EXTRACT);

        //execute action
        ProcessConfiguration process = configuration.getProcess(processPath);
        ActivityConfiguration activity = null;
        activity = process.getActivity(activityId);
        if (activity == null) {
            activity = new ActivityConfiguration();
            process.getActivities().put(activityId, activity);
        }
        Extract extract = null;
        try {
            final ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();
            extract = mapper.readValue(extractString, Extract.class);
        } catch (final Exception e) {
            instructionSupport.error("Unable to deserialize extract", e);
            return;
        }
        final List<String> invalidAttributes =
            extract.getExtractRules().stream().map(ExtractRule::getAttribute)
                .filter(this::isAttributeNameInvalid).collect(Collectors.toList());
        if (!invalidAttributes.isEmpty()) {
            final String errorMessage =
                "Invalid attribute names " + invalidAttributes + " in extract for " + extract.getName();
            instructionSupport.error(errorMessage);
            return;
        }

        activity.setExtract(extract);
        saveConfiguration(instructionSupport);
        LOG.debug("Configure extract for {}", processPath);
    }

    private void deleteExtract(final InstructionSupport instructionSupport) {
        if (!instructionSupport.validate(PROCESS_PATH, ACTIVITY_ID)) {
            return;
        }
        //fetch parameters
        final String processPath = instructionSupport.getProcessPath();
        final String activityId = instructionSupport.getActivityId();

        //execute action
        ProcessConfiguration process = null;
        process = configuration.getProcess(processPath);
        if (process == null) {
            instructionSupport.error("Process configuration " + processPath + " not found");
            return;
        }
        ActivityConfiguration activity = null;
        activity = process.getActivity(activityId);
        if (activity == null) {
            instructionSupport.error("Activity " + activityId + " not found");
            return;
        }
        activity.setExtract(null);
        saveConfiguration(instructionSupport);
    }

    private void getTracing(final InstructionSupport instructionSupport) {
        if (!instructionSupport.validate(PROCESS_PATH, ACTIVITY_ID)) {
            return;
        }
        //fetch parameters
        final String processPath = instructionSupport.getProcessPath();
        final String activityId = instructionSupport.getActivityId();

        //execute action
        final ProcessConfiguration process = configuration.getProcess(processPath);
        if (process == null) {
            instructionSupport.error("Process " + processPath + " not found");
            return;
        }
        ActivityConfiguration activity = null;
        activity = process.getActivity(activityId);
        if (activity == null) {
            instructionSupport.error("Activity " + activityId + " not found");
            return;
        }
        TracepointExt tracepoint = null;
        tracepoint = activity.getTracepoint();
        if (tracepoint == null) {
            instructionSupport.error("Tracepoint for actvitiy " + activityId + " not found");
            return;
        }

        instructionSupport.setParameter(STARTTIME, tracepoint.getStarttime())
            .setParameter(ENDTIME, tracepoint.getEndtime())
            .setParameter(ITERATIONS, tracepoint.getIterations())
            .setParameter(DEEP_TRACE, tracepoint.isDeeptrace());

    }

    private void getExtract(final InstructionSupport instructionSupport) {
        if (!instructionSupport.validate(PROCESS_PATH, ACTIVITY_ID)) {
            return;
        }
        //fetch parameters
        final String processPath = instructionSupport.getProcessPath();
        final String activityId = instructionSupport.getActivityId();

        //execute action
        final ProcessConfiguration process = configuration.getProcess(processPath);
        if (process == null) {
            instructionSupport.error("Process " + processPath + " not found");
            return;
        }
        final ActivityConfiguration activity = process.getActivity(activityId);
        if (activity == null) {
            instructionSupport.error("Activity " + activityId + " not found");
            return;
        }
        final Extract extract = activity.getExtract();
        if (extract == null) {
            instructionSupport.error("Extract for actvitiy " + activityId + " not found");
            return;
        }
        try {
            instructionSupport.setParameter(EXTRACT, JsonUtils.serialize(extract));
        } catch (final Exception e) {
            instructionSupport.error("Unable to serialize Extract", e);
            return;
        }

        LOG.debug("Get Extract for {} -> {}", processPath, activityId);
    }

    private void getReording(final InstructionSupport instructionSupport) {
        instructionSupport.setParameter(ENGINE_WIDE_RECORDING, configuration.isRecording());
        for (ProcessModel model : njams.getProcessModels()) {
            final boolean recording;
            if (configuration.hasProcess(model.getPath())) {
                recording = configuration.getProcess(model.getPath()).isRecording();
            } else {
                recording = configuration.isRecording();
            }
            instructionSupport.setParameter(model.getPath().toString(), recording);
        }
    }

    private void record(final InstructionSupport instructionSupport) {
        if ("true".equalsIgnoreCase(njams.getSettings().getPropertyWithDeprecationWarning(
            NjamsSettings.PROPERTY_DISABLE_STARTDATA, NjamsSettings.OLD_DISABLE_STARTDATA))) {
            instructionSupport.error("Collecting start-data is disabled by configuration.");
            return;
        }

        //fetch parameters
        if (instructionSupport.hasParameter(ENGINE_WIDE_RECORDING)) {
            try {
                final boolean engineWideRecording = instructionSupport.getBoolParameter(ENGINE_WIDE_RECORDING);
                configuration.setRecording(engineWideRecording);
                //reset to default after logic change
                configuration.getProcesses().values().forEach(p -> p.setRecording(engineWideRecording));
            } catch (final Exception e) {
                instructionSupport.error("Unable to set client recording", e);
                return;
            }
        }

        final String processPath = instructionSupport.getProcessPath();
        if (processPath != null) {
            try {
                final ProcessConfiguration process = configuration.getProcess(processPath);
                final String doRecordParameter = instructionSupport.getParameter(RECORD);
                final boolean doRecord = "all".equalsIgnoreCase(doRecordParameter);
                process.setRecording(doRecord);
            } catch (final Exception e) {
                instructionSupport.error("Unable to set process recording", e);
                return;
            }
        }

        saveConfiguration(instructionSupport);
        LOG.debug("Recording for {}", processPath);
    }

    private void testExpression(final InstructionSupport instructionSupport) {
        if (!instructionSupport.validate(RULE_TYPE, EXPRESSION, DATA)) {
            return;
        }

        final RuleType ruleType = instructionSupport.getParameter(RULE_TYPE, RuleType::fromValue);
        final String expression = instructionSupport.getParameter(EXPRESSION);
        final String testdata = instructionSupport.getParameter(DATA);
        try {
            instructionSupport.setParameter(MATCH_RESULT,
                ExtractHandler.testExpression(ruleType, expression, testdata));
        } catch (Exception e) {
            instructionSupport.error("Expression test failed.", e, true);
        }

    }

    private void saveConfiguration(InstructionSupport instructionSupport) {
        try {
            configuration.save();
        } catch (final Exception e) {
            instructionSupport.error("Unable to save configuration", e);
        }
    }

    /**
     * Must not be <code>null</code> and no dot allowed at all and must not start or end with blanks.
     *
     * @param attrName
     * @return <code>true</code> if the given name is <code>null</code> or invalid.
     */
    private boolean isAttributeNameInvalid(String attrName) {
        if (StringUtils.isBlank(attrName)) {
            return true;
        }
        return !ATTRIBUTE_NAME_PATTERN.matcher(attrName).matches();
    }
}
