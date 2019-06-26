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
package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.communication_rework.instruction.control.processor.InstructionProcessor;
import com.im.njams.sdk.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public abstract class ConfigurationProcessor extends InstructionProcessor {

    protected static final String UNABLE_TO_SAVE_CONFIGURATION = "Unable to save configuration";

    protected Njams njams;

    public ConfigurationProcessor(Njams njams, String commandToProcess) {
        super(commandToProcess);
        this.njams = njams;
    }

    @Override
    public final void processInstruction(Instruction instruction) {
        InstructionSupport instructionSupport = new InstructionSupport(instruction);
        this.processInstruction(instructionSupport);
    }

    protected void saveConfiguration(InstructionSupport instructionSupport) {
        try {
            njams.saveConfigurationFromMemoryToStorage();
        } catch (final Exception e) {
            instructionSupport.error(UNABLE_TO_SAVE_CONFIGURATION, e);
        }
    }

    protected abstract void processInstruction(InstructionSupport instructionSupport);

    protected static class InstructionSupport {

        private static final Logger LOG = LoggerFactory.getLogger(InstructionSupport.class);

        public static final String PROCESS_PATH = "processPath";
        public static final String ACTIVITY_ID = "activityId";
        public static final String LOG_LEVEL = "logLevel";
        public static final String LOG_MODE = "logMode";

        private Response response;
        private Instruction instruction;

        public InstructionSupport(Instruction instruction) {
            this.instruction = instruction;

            // initialize success response; overwritten by error(...) methods-
            if(instruction.getResponse() == null) {
                response = new Response();
                response.setResultCode(0);
                response.setResultMessage("Success");
                instruction.setResponse(response);
            }else{
                response = instruction.getResponse();
            }

        }

        /**
         * Returns <code>true</code>, only if all given parameters exist and have none-blank values.
         * Sets according {@link #error(String)} response.
         * @param names names to validate
         * @return true, if all names exists, otherwise false
         */
        public boolean validate(String... names) {
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
         * @param name names to validate
         * @param enumeration the enumeration to check for
         * @return true, if the names exist in the enumeration, otherwise false
         */
        public <T extends Enum<T>> boolean validate(final String name, final Class<T> enumeration) {
            if (getEnumParameter(name, enumeration) == null) {
                error("could not parse value of parameter [" + name + "] value=" + getParameter(name));
                return false;
            }
            return true;
        }

        public void error(final String message) {
            error(message, null);
        }

        public void error(final String message, final Exception e) {
            LOG.error("Failed to execute command: [{}] on process: {}{}. Reason: {}", instruction.getCommand(),
                    getProcessPath(), getActivityId() != null ? "#" + getActivityId() : "", message, e);
            response.setResultCode(1);
            response.setResultMessage(message + (e != null ? ": " + e.getMessage() : ""));
            return;
        }

        public InstructionSupport setParameter(final String name, final Object value) {
            if (value instanceof LocalDateTime) {
                response.getParameters().put(name, DateTimeUtility.toString((LocalDateTime) value));
            } else {
                response.getParameters().put(name, String.valueOf(value));
            }
            return this;
        }

        public String getProcessPath() {
            return getParameter(PROCESS_PATH);
        }

        public String getActivityId() {
            return getParameter(ACTIVITY_ID);
        }

        public boolean hasParameter(final String name) {
            return instruction.getRequest().getParameters().containsKey(name);
        }

        public String getParameter(final String name) {
            return instruction.getRequest().getParameters().get(name);
        }

        public int getIntParameter(final String name) {
            final String s = getParameter(name);
            try {
                return s == null ? 0 : Integer.parseInt(s);
            } catch (final NumberFormatException e) {
                LOG.error("Failed to parse parameter {} from request.", name, e);
                return 0;
            }
        }

        public boolean getBoolParameter(final String name) {
            return Boolean.parseBoolean(getParameter(name));
        }

        public <T extends Enum<T>> T getEnumParameter(final String name, final Class<T> enumeration) {
            final String constantName = getParameter(name);
            if (constantName == null || enumeration == null || enumeration.getEnumConstants() == null) {
                return null;
            }
            return Arrays.stream(enumeration.getEnumConstants()).filter(c -> c.name().equalsIgnoreCase(constantName))
                    .findAny().orElse(null);
        }
    }
}
