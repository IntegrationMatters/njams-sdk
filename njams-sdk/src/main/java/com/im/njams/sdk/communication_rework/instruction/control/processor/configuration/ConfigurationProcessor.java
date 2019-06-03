package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.communication_rework.instruction.control.processor.InstructionProcessor;
import com.im.njams.sdk.communication_rework.instruction.entity.Configuration;
import com.im.njams.sdk.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public abstract class ConfigurationProcessor extends InstructionProcessor {

    protected Configuration configuration;

    public ConfigurationProcessor(Configuration configuration, String commandToProcess) {
        super(commandToProcess);
        this.configuration = configuration;
    }

    @Override
    public final void processInstruction(Instruction instruction) {
        InstructionSupport instructionSupport = new InstructionSupport(instruction);
        this.processInstruction(instructionSupport);
    }

    protected void saveConfiguration(InstructionSupport instructionSupport) {
        try {
            configuration.save();
        } catch (final Exception e) {
            instructionSupport.error("Unable to save configuration", e);
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
            response = new Response();
            response.setResultCode(0);
            response.setResultMessage("Success");
            instruction.setResponse(response);
        }

        /**
         * Returns <code>true</code>, only if all given parameters exist and have none-blank values.
         * Sets according {@link #error(String)} response.
         * @param names
         * @return
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
         * @param name
         * @param enumeration
         * @return
         */
        public <T extends Enum<T>> boolean validate(final String name, final Class<T> enumeration) {
            if (getEnumParameter(name, enumeration) == null) {
                error("could not parse value of parameter [" + name + "] value=" + getParameter(name));
                return false;
            }
            return true;
        }

        /**
         * Creates an error response with the given message.
         * @param message
         */
        public void error(final String message) {
            error(message, null);
        }

        /**
         * Creates an error response with the given message and exception.
         * @param message
         * @param e
         */
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
