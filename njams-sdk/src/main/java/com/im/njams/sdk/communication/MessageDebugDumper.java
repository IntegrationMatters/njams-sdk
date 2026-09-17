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
package com.im.njams.sdk.communication;

import static com.im.njams.sdk.communication.MessageHeaders.MESSAGETYPE_EVENT;
import static com.im.njams.sdk.communication.MessageHeaders.MESSAGETYPE_PROJECT;
import static com.im.njams.sdk.communication.MessageHeaders.MESSAGETYPE_TRACE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.utils.JsonUtils;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Writes outbound messages to individual files on disk for development/debug purposes.
 * <p>
 * Activated by the {@link NjamsSettings#PROPERTY_DEBUG_MESSAGE_DIR} setting. When the setting is absent,
 * {@link #dump} returns immediately after a single null-check — no overhead on the send path.
 */
class MessageDebugDumper {

    private static final Logger LOG = LoggerFactory.getLogger(MessageDebugDumper.class);
    private static final DateTimeFormatter RUN_DIR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final String outputDir;
    private final AtomicLong sequence = new AtomicLong();

    MessageDebugDumper() {
        outputDir = null;
    }

    MessageDebugDumper(ClientSettings settings) {
        outputDir = resolveOutputDir(settings.getProperty(NjamsSettings.PROPERTY_DEBUG_MESSAGE_DIR));
    }

    private static String resolveOutputDir(String dir) {
        if (StringUtils.isBlank(dir)) {
            return null;
        }
        String runDir = LocalDateTime.now().format(RUN_DIR_FORMAT);
        Path outputPath = Path.of(dir, runDir);
        try {
            Files.createDirectories(outputPath);
            LOG.info("Debug message dumping enabled, writing to: {}", outputPath);
            return outputPath.toString();
        } catch (IOException e) {
            LOG.warn("Failed to create debug output directory '{}', dumping disabled.", outputPath, e);
            return null;
        }
    }

    void dump(CommonMessage msg, String clientSessionId) {
        if (outputDir == null) {
            return;
        }
        long seq = sequence.incrementAndGet();
        try {
            Files.writeString(
                Path.of(outputDir, buildFilename(seq, msg)),
                buildContent(msg, clientSessionId));
        } catch (Exception e) {
            LOG.debug("Debug dump failed for seq {}", seq, e);
        }
    }

    private static String buildFilename(long seq, CommonMessage msg) {
        return String.format("%08d_%s_%s.json", seq, messageType(msg), sanitize(messageId(msg)));
    }

    // @Deprecated flags external API consumers only; internal use of Jackson factory is intentional.
    @SuppressWarnings("deprecation")
    private static String buildContent(CommonMessage msg, String clientSessionId) throws Exception {
        ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();
        ObjectNode root = mapper.createObjectNode();

        ObjectNode headers = root.putObject("headers");
        headers.put("NJAMS_MESSAGETYPE", messageType(msg));
        headers.put("NJAMS_PATH", msg.getPath());
        if (msg instanceof LogMessage) {
            headers.put("NJAMS_LOGID", ((LogMessage) msg).getLogId());
        }
        headers.put("NJAMS_MESSAGEVERSION", MessageVersion.V4.toString());
        if (clientSessionId != null) {
            headers.put("NJAMS_CLIENTSESSIONID", clientSessionId);
        }

        root.set("body", mapper.readTree(JsonUtils.serialize(msg)));

        return mapper.writeValueAsString(root);
    }

    private static String messageType(CommonMessage msg) {
        if (msg instanceof LogMessage) {
            return MESSAGETYPE_EVENT;
        }
        if (msg instanceof ProjectMessage) {
            return MESSAGETYPE_PROJECT;
        }
        return MESSAGETYPE_TRACE;
    }

    private static String messageId(CommonMessage msg) {
        if (msg instanceof LogMessage) {
            return ((LogMessage) msg).getLogId();
        }
        return lastPathSegment(msg.getPath());
    }

    private static String lastPathSegment(String path) {
        if (StringUtils.isBlank(path)) {
            return "unknown";
        }
        String s = path.endsWith(">") ? path.substring(0, path.length() - 1) : path;
        int idx = s.lastIndexOf('>');
        return idx >= 0 ? s.substring(idx + 1) : s;
    }

    private static String sanitize(String s) {
        return s == null ? "unknown" : s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
