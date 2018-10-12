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

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.communication.InstructionListener;
import java.time.LocalDateTime;
import org.slf4j.LoggerFactory;

/**
 * InstructionListener implementation for all instructions which will modify
 * configuration values.
 *
 * @author pnientiedt
 */
public class ConfigurationInstructionListener implements InstructionListener {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ConfigurationInstructionListener.class);

    private final Configuration configuration;

    /**
     * Inititalize ConfigurationInstructionListener
     *
     * @param configuration which should be managed
     */
    public ConfigurationInstructionListener(Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * Configure configuration if a valid instruction will be given.
     *
     * @param instruction to validate
     */
    @Override
    public void onInstruction(Instruction instruction) {
        String command = instruction.getRequest().getCommand();
        if (command.equalsIgnoreCase(Command.GET_LOG_LEVEL.commandString())) {
            getLogLevel(instruction);
        } else if (command.equalsIgnoreCase(Command.SET_LOG_LEVEL.commandString())) {
            setLogLevel(instruction);
        } else if (command.equalsIgnoreCase(Command.GET_LOG_MODE.commandString())) {
            getLogMode(instruction);
        } else if (command.equalsIgnoreCase(Command.SET_LOG_MODE.commandString())) {
            setLogMode(instruction);
        } else if (command.equalsIgnoreCase(Command.SET_TRACING.commandString())) {
            setTracing(instruction);
        } else if (command.equalsIgnoreCase(Command.GET_TRACING.commandString())) {
            getTracing(instruction);
        } else if (command.equalsIgnoreCase(Command.CONFIGURE_EXTRACT.commandString())) {
            configureExtract(instruction);
        } else if (command.equalsIgnoreCase(Command.DELETE_EXTRACT.commandString())) {
            deleteExtract(instruction);
        } else if (command.equalsIgnoreCase(Command.GET_EXTRACT.commandString())) {
            getExtract(instruction);
        } else if (command.equalsIgnoreCase(Command.RECORD.commandString())) {
            record(instruction);
        }
    }

    private void getLogLevel(Instruction instruction) {
        String errorMsg = null;

        //fetch parameters
        String processPath = instruction.getRequest().getParameters().get("processPath");
        if (processPath == null) {
            errorMsg = "processPath not provided";
        }

        //execute action
        ProcessConfiguration process = null;
        if (errorMsg == null) {
            process = configuration.getProcess(processPath);
            if (process == null) {
                errorMsg = "Process " + processPath + " not found";
            }
        }

        //send response
        Response response = new Response();
        if (errorMsg == null) {
            response.getParameters().put("logLevel", process.getLogLevel().toString());
            response.getParameters().put("exclude", String.valueOf(process.isExclude()));
            //for compatibility reasons logmode will be returned
            response.getParameters().put("logMode", configuration.getLogMode().toString());
            response.setResultCode(0);
            response.setResultMessage("Success");
            LOG.debug("Return LogLevel for {}", processPath);
        } else {
            response.setResultCode(1);
            response.setResultMessage(errorMsg);
            LOG.error("Unable to return LogLevel for {}: {}", processPath, errorMsg);
        }
        instruction.setResponse(response);

    }

    private void setLogLevel(Instruction instruction) {
        String errorMsg = null;

        //fetch parameters
        String processPath = instruction.getRequest().getParameters().get("processPath");
        if (processPath == null) {
            errorMsg = "processPath not provided";
        }
        LogLevel loglevel = null;
        try {
            loglevel = LogLevel.valueOf(instruction.getRequest().getParameters().get("logLevel"));
        } catch (Exception e) {
            errorMsg = "Unable to parse LogLevel: " + e.getMessage();
            LOG.error("Unable to parse LogLevel", e);
        }
        LogMode logMode = null;
        try {
            logMode = LogMode.valueOf(instruction.getRequest().getParameters().get("logMode"));
        } catch (Exception e) {
            //do nothing, this is only for compatibility, if it is not set, do nothing
        }
        boolean exclude = false;
        try {
            exclude = Boolean.valueOf(instruction.getRequest().getParameters().get("exclude"));
        } catch (Exception e) {
            errorMsg = "Unable to parse exclude: " + e.getMessage();
            LOG.error("Unable to parse exclude", e);
        }

        //execute action
        ProcessConfiguration process = null;
        if (errorMsg == null) {
            process = configuration.getProcess(processPath);
            if (process == null) {
                process = new ProcessConfiguration();
                configuration.getProcesses().put(processPath, process);
            }
        }
        if (errorMsg == null) {
            try {
                if (logMode != null) {
                    configuration.setLogMode(logMode);
                }
                process.setLogLevel(loglevel);
                process.setExclude(exclude);
                configuration.save();
            } catch (Exception e) {
                errorMsg = "Unable to save LogLevel: " + e.getMessage();
                LOG.error("Unable to save LogLevel", e);
            }
        }

        //send response
        Response response = new Response();
        if (errorMsg == null) {
            response.setResultCode(0);
            response.setResultMessage("Success");
            LOG.debug("Set LogLevel for {}", processPath);
        } else {
            response.setResultCode(1);
            response.setResultMessage(errorMsg);
            LOG.error("Unable to set LogLevel for {}: {}", processPath, errorMsg);
        }
        instruction.setResponse(response);
    }

    private void getLogMode(Instruction instruction) {
        //send response
        Response response = new Response();
        response.getParameters().put("logMode", configuration.getLogMode().toString());
        response.setResultCode(0);
        response.setResultMessage("Success");
        LOG.debug("Return LogMode");
        instruction.setResponse(response);
    }

    private void setLogMode(Instruction instruction) {
        String errorMsg = null;

        //fetch parameters
        LogMode logMode = null;
        try {
            logMode = LogMode.valueOf(instruction.getRequest().getParameters().get("logMode"));
        } catch (Exception e) {
            errorMsg = "Unable to parse LogMode: " + e.getMessage();
            LOG.error("Unable to parse LogMode", e);
        }

        //execute action
        if (errorMsg == null) {
            try {
                configuration.setLogMode(logMode);
                configuration.save();
            } catch (Exception e) {
                errorMsg = "Unable to save LogMode: " + e.getMessage();
                LOG.error("Unable to save LogMode", e);
            }
        }

        //send response
        Response response = new Response();
        if (errorMsg == null) {
            response.setResultCode(0);
            response.setResultMessage("Success");
            LOG.debug("Set LogMode to {}", logMode);
        } else {
            response.setResultCode(1);
            response.setResultMessage(errorMsg);
            LOG.error("Unable to set LogMode: {}", errorMsg);
        }
        instruction.setResponse(response);
    }

    private void setTracing(Instruction instruction) {
        String errorMsg = null;

        //fetch parameters
        String processPath = instruction.getRequest().getParameters().get("processPath");
        if (processPath == null) {
            errorMsg = "processPath not provided";
        }
        String activityId = instruction.getRequest().getParameters().get("activityId");
        if (activityId == null) {
            errorMsg = "activityId not provided";
        }
        boolean enableTracing = true;
        try {
            enableTracing = Boolean.valueOf(instruction.getRequest().getParameters().get("enableTracing"));
        } catch (Exception e) {
            errorMsg = "Unable to parse enableTracing: " + e.getMessage();
            LOG.error("Unable to parse enableTracing", e);
        }

        if (enableTracing) {
            enableTracing(instruction, processPath, activityId, errorMsg);
        } else {
            disableTracing(instruction, processPath, activityId, errorMsg);
        }
    }

    private void enableTracing(Instruction instruction, String processPath, String activityId, String errorMsgParam) {
        String errorMsg = errorMsgParam;
        LocalDateTime starttime = null;
        try {
            starttime = LocalDateTime.parse(instruction.getRequest().getParameters().get("starttime"));
        } catch (Exception e) {
            errorMsg = "Unable to parse starttime: " + e.getMessage();
            LOG.error("Unable to parse starttime", e);
        }
        LocalDateTime endtime = null;
        try {
            endtime = LocalDateTime.parse(instruction.getRequest().getParameters().get("endtime"));
        } catch (Exception e) {
            errorMsg = "Unable to parse endtime: " + e.getMessage();
            LOG.error("Unable to parse endtime", e);
        }
        Integer iterations = 0;
        try {
            iterations = Integer.valueOf(instruction.getRequest().getParameters().get("iterations"));
        } catch (Exception e) {
            errorMsg = "Unable to parse iterations: " + e.getMessage();
            LOG.debug("Unable to parse iterations", e);
        }
        boolean deepTrace = false;
        try {
            deepTrace = Boolean.valueOf(instruction.getRequest().getParameters().get("deepTrace"));
        } catch (Exception e) {
            errorMsg = "Unable to parse deepTrace: " + e.getMessage();
            LOG.error("Unable to parse deepTrace", e);
        }

        //execute action
        ProcessConfiguration process = null;
        if (errorMsg == null) {
            process = configuration.getProcess(processPath);
            if (process == null) {
                process = new ProcessConfiguration();
                configuration.getProcesses().put(processPath, process);
            }
        }
        ActivityConfiguration activity = null;
        if (errorMsg == null) {
            activity = process.getActivity(activityId);
            if (activity == null) {
                activity = new ActivityConfiguration();
                process.getActivities().put(activityId, activity);
            }
        }
        if (errorMsg == null) {
            try {
                TracepointExt tp = new TracepointExt();
                tp.setStarttime(starttime);
                tp.setEndtime(endtime);
                tp.setIterations(iterations);
                tp.setDeeptrace(deepTrace);
                activity.setTracepoint(tp);
                configuration.save();
            } catch (Exception e) {
                errorMsg = "Unable to save Tracepoint: " + e.getMessage();
                LOG.error("Unable to save Tracepoint", e);
            }
        }

        //send response
        Response response = new Response();
        if (errorMsg == null) {
            response.setResultCode(0);
            response.setResultMessage("Success");
            LOG.debug("Set LogLevel for {}", processPath);
        } else {
            response.setResultCode(1);
            response.setResultMessage(errorMsg);
            LOG.error("Unable to set LogLevel for {}: {}", processPath, errorMsg);
        }
        instruction.setResponse(response);
    }

    private void disableTracing(Instruction instruction, String processPath, String activityId, String errorMsgParam) {
        String errorMsg = errorMsgParam;
        //execute action
        ProcessConfiguration process = null;
        if (errorMsg == null) {
            process = configuration.getProcess(processPath);
            if (process == null) {
                errorMsg = "Process " + processPath + " not found";
            }
        }
        ActivityConfiguration activity = null;
        if (errorMsg == null) {
            activity = process.getActivity(activityId);
            if (activity == null) {
                errorMsg = "Activity " + activityId + " not found";
            }
        }
        if (errorMsg == null) {
            try {
                activity.setTracepoint(null);
                configuration.save();
            } catch (Exception e) {
                errorMsg = "Unable to delete Tracepoint: " + e.getMessage();
                LOG.error("Unable to delete Tracepoint", e);
            }
        }

        //send response
        Response response = new Response();
        if (errorMsg == null) {
            response.setResultCode(0);
            response.setResultMessage("Success");
            LOG.debug("Delete Tracepoint for {} -> {}", processPath, activityId);
        } else {
            response.setResultCode(1);
            response.setResultMessage(errorMsg);
            LOG.error("Unable to delete Tracepoint for {}: {}", processPath, activityId, errorMsg);
        }
        instruction.setResponse(response);
    }

    private void configureExtract(Instruction instruction) {
        String errorMsg = null;

        //fetch parameters
        String processPath = instruction.getRequest().getParameters().get("processPath");
        if (processPath == null) {
            errorMsg = "processPath not provided";
        }
        String activityId = instruction.getRequest().getParameters().get("activityId");
        if (activityId == null) {
            errorMsg = "activityId not provided";
        }
        String extractString = instruction.getRequest().getParameters().get("extract");
        if (extractString == null) {
            errorMsg = "extract not provided";
        }

        //execute action
        ProcessConfiguration process = null;
        if (errorMsg == null) {
            process = configuration.getProcess(processPath);
            if (process == null) {
                process = new ProcessConfiguration();
                configuration.getProcesses().put(processPath, process);
            }
        }
        ActivityConfiguration activity = null;
        if (errorMsg == null) {
            activity = process.getActivity(activityId);
            if (activity == null) {
                activity = new ActivityConfiguration();
                process.getActivities().put(activityId, activity);
            }
        }
        Extract extract = null;
        if (errorMsg == null) {
            try {
                ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();
                extract = mapper.readValue(extractString, Extract.class);
            } catch (Exception e) {
                errorMsg = "Unable to deserialize Extract: " + e.getMessage();
                LOG.error("Unable to deserialize Extract", e);
            }
        }
        if (errorMsg == null) {
            try {
                activity.setExtract(extract);
                configuration.save();
            } catch (Exception e) {
                errorMsg = "Unable to set Extract: " + e.getMessage();
                LOG.error("Unable to set Extract", e);
            }
        }

        //send response
        Response response = new Response();
        if (errorMsg == null) {
            response.setResultCode(0);
            response.setResultMessage("Success");
            LOG.debug("Set LogLevel for {}", processPath);
        } else {
            response.setResultCode(1);
            response.setResultMessage(errorMsg);
            LOG.error("Unable to set LogLevel for {}: {}", processPath, errorMsg);
        }
        instruction.setResponse(response);
    }

    private void deleteExtract(Instruction instruction) {
        String errorMsg = null;

        //fetch parameters
        String processPath = instruction.getRequest().getParameters().get("processPath");
        if (processPath == null) {
            errorMsg = "processPath not provided";
        }
        String activityId = instruction.getRequest().getParameters().get("activityId");
        if (activityId == null) {
            errorMsg = "activityId not provided";
        }

        //execute action
        ProcessConfiguration process = null;
        if (errorMsg == null) {
            process = configuration.getProcess(processPath);
            if (process == null) {
                errorMsg = "Process " + processPath + " not found";
            }
        }
        ActivityConfiguration activity = null;
        if (errorMsg == null) {
            activity = process.getActivity(activityId);
            if (activity == null) {
                errorMsg = "Activity " + activityId + " not found";
            }
        }
        if (errorMsg == null) {
            try {
                activity.setExtract(null);
                configuration.save();
            } catch (Exception e) {
                errorMsg = "Unable to delete Extract: " + e.getMessage();
                LOG.error("Unable to delete Extract", e);
            }
        }

        //send response
        Response response = new Response();
        if (errorMsg == null) {
            response.setResultCode(0);
            response.setResultMessage("Success");
            LOG.debug("Delete Extract for {} -> {}", processPath, activityId);
        } else {
            response.setResultCode(1);
            response.setResultMessage(errorMsg);
            LOG.error("Unable to delete Extract for {}: {}", processPath, activityId, errorMsg);
        }
        instruction.setResponse(response);
    }

    private void getTracing(Instruction instruction) {
        String errorMsg = null;

        //fetch parameters
        String processPath = instruction.getRequest().getParameters().get("processPath");
        if (processPath == null) {
            errorMsg = "processPath not provided";
        }
        String activityId = instruction.getRequest().getParameters().get("activityId");
        if (activityId == null) {
            errorMsg = "activityId not provided";
        }

        //execute action
        ProcessConfiguration process = null;
        if (errorMsg == null) {
            process = configuration.getProcess(processPath);
            if (process == null) {
                errorMsg = "Process " + processPath + " not found";
            }
        }
        ActivityConfiguration activity = null;
        if (errorMsg == null) {
            activity = process.getActivity(activityId);
            if (activity == null) {
                errorMsg = "Activity " + activityId + " not found";
            }
        }
        TracepointExt tracepoint = null;
        if (errorMsg == null) {
            tracepoint = activity.getTracepoint();
            if (tracepoint == null) {
                errorMsg = "Tracepoint for actvitiy " + activityId + " not found";
            }
        }

        //send response
        Response response = new Response();
        if (errorMsg == null) {
            response.setResultCode(0);
            response.setResultMessage("Success");
            response.getParameters().put("starttime", tracepoint.getStarttime().toString());
            response.getParameters().put("endtime", tracepoint.getEndtime().toString());
            response.getParameters().put("iterations", String.valueOf(tracepoint.getIterations()));
            response.getParameters().put("deepTrace", String.valueOf(tracepoint.isDeeptrace()));
            LOG.debug("Get Tracepoint for {} -> {}", processPath, activityId);
        } else {
            response.setResultCode(1);
            response.setResultMessage(errorMsg);
            LOG.error("Unable to get Tracepoint for {}: {}", processPath, activityId, errorMsg);
        }
        instruction.setResponse(response);
    }

    private void getExtract(Instruction instruction) {
        String errorMsg = null;

        //fetch parameters
        String processPath = instruction.getRequest().getParameters().get("processPath");
        if (processPath == null) {
            errorMsg = "processPath not provided";
        }
        String activityId = instruction.getRequest().getParameters().get("activityId");
        if (activityId == null) {
            errorMsg = "activityId not provided";
        }

        //execute action
        ProcessConfiguration process = null;
        if (errorMsg == null) {
            process = configuration.getProcess(processPath);
            if (process == null) {
                errorMsg = "Process " + processPath + " not found";
            }
        }
        ActivityConfiguration activity = null;
        if (errorMsg == null) {
            activity = process.getActivity(activityId);
            if (activity == null) {
                errorMsg = "Activity " + activityId + " not found";
            }
        }
        Extract extract = null;
        if (errorMsg == null) {
            extract = activity.getExtract();
            if (extract == null) {
                errorMsg = "Extract for actvitiy " + activityId + " not found";
            }
        }
        String extractString = null;
        if (errorMsg == null) {
            try {
                ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();
                extractString = mapper.writeValueAsString(extract);
            } catch (Exception e) {
                errorMsg = "Unable to serialize Extract: " + e.getMessage();
                LOG.error("Unable to serialize Extract", e);
            }
        }

        //send response
        Response response = new Response();
        if (errorMsg == null) {
            response.setResultCode(0);
            response.setResultMessage("Success");
            response.getParameters().put("extract", extractString);
            LOG.debug("Get Extract for {} -> {}", processPath, activityId);
        } else {
            response.setResultCode(1);
            response.setResultMessage(errorMsg);
            LOG.error("Unable to get Extract for {}: {}", processPath, activityId, errorMsg);
        }
        instruction.setResponse(response);
    }

    private void record(Instruction instruction) {
        String errorMsg = null;

        //fetch parameters
        if (instruction.getRequest().getParameters().containsKey("EngineWideRecording")) {
            try {
                boolean engineWideRecording = Boolean.valueOf(instruction.getRequest().getParameters().get("EngineWideRecording"));
                configuration.setRecording(engineWideRecording);
                //reset to default after logic change
                configuration.getProcesses().values().forEach(p -> p.setRecording(engineWideRecording));
            } catch (Exception e) {
                errorMsg = "Unable to set client recording: " + e.getMessage();
                LOG.error("Unable to set client recording", e);
            }
        }

        String processPath = instruction.getRequest().getParameters().get("processPath");
        if (processPath != null) {
            try {
                ProcessConfiguration process = null;
                if (errorMsg == null) {
                    process = configuration.getProcess(processPath);
                    if (process == null) {
                        process = new ProcessConfiguration();
                        configuration.getProcesses().put(processPath, process);
                    }
                }
                final Object doRecordParameter = instruction.getRequest().getParameters().get("Record");
                final boolean doRecord;
                doRecord = doRecordParameter != null && "all".equalsIgnoreCase(doRecordParameter.toString());
                process.setRecording(doRecord);
            } catch (Exception e) {
                errorMsg = "Unable to set process recording: " + e.getMessage();
                LOG.error("Unable to set process recording", e);
            }
        }

        if (errorMsg == null) {
            try {
                configuration.save();
            } catch (Exception e) {
                errorMsg = "Unable to save recording: " + e.getMessage();
                LOG.error("Unable to save recording", e);
            }
        }

        //send response
        Response response = new Response();
        if (errorMsg == null) {
            response.setResultCode(0);
            response.setResultMessage("Success");
            LOG.debug("Recording for {}", processPath);
        } else {
            response.setResultCode(1);
            response.setResultMessage(errorMsg);
            LOG.error("Unable to set recording for {}: {}", processPath, errorMsg);
        }
        instruction.setResponse(response);

    }

}
