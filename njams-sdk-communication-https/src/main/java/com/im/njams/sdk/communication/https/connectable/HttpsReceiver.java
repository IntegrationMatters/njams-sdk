package com.im.njams.sdk.communication.https.connectable;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.communication.connectable.AbstractReceiver;
import com.im.njams.sdk.communication.connector.Connector;
import com.im.njams.sdk.communication.https.HttpsConstants;
import com.im.njams.sdk.communication.https.connector.HttpsReceiverConnector;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import static java.net.HttpURLConnection.*;
import static java.nio.charset.Charset.defaultCharset;

public class HttpsReceiver extends AbstractReceiver implements HttpHandler {

    /**
     * Content type json
     */
    private static final String CONTENT_TYPE_JSON = "application/json";
    /**
     * Content type
     */
    private static final String HEADER_PARAMETER_CONTENT_TYPE = "Content-Type";
    /**
     * Content type text plain
     */
    private static final String CONTENT_TYPE_TEXT = "text/plain";

    //The Logger
    private static final Logger LOG = LoggerFactory.getLogger(HttpsReceiver.class);

    @Override
    public String getName() {
        return HttpsConstants.COMMUNICATION_NAME;
    }

    @Override
    protected Connector initialize(Properties properties) {
        return new HttpsReceiverConnector(properties, getName() + Connector.RECEIVER_NAME_ENDING, this);
    }

    /**
     * Handle commands
     *
     * @param he HttpExchange
     * @throws IOException some exception
     */
    @Override
    public void handle(HttpExchange he) throws IOException {
        try {
            if ("POST".equals(he.getRequestMethod())) {
                final String contentType = getContentType(he);
                final Instruction instruction;
                if (CONTENT_TYPE_JSON.equals(contentType)) {
                    instruction = readJsonInstruction(he);
                } else {
                    throw new UnsupportedOperationException("Unexpected Content-Type " + contentType);
                }
                this.onInstruction(instruction);
                instruction.getResponse().setResultCode(1);
                final Iterator<String> acceptIterator = he.getRequestHeaders().get("Accept").iterator();
                while (acceptIterator.hasNext()) {
                    final String accept = acceptIterator.next();
                    if (CONTENT_TYPE_JSON.equals(accept)) {
                        writeJsonInstruction(he, instruction);
                        break;
                    }
                }
                he.close();
            } else {
                writeError(he, HTTP_BAD_METHOD, "Unsupported method " + he.getRequestMethod());
            }
        } catch (final Exception ex) {
            LOG.error("Error handling command", ex);
            ex.printStackTrace(System.err);
            writeError(he, HTTP_BAD_REQUEST, ex.getMessage());
        } finally {
            he.close();
        }
    }

    private String getContentType(final HttpExchange he) {
        List<String> contentTypes = he.getRequestHeaders().get(HEADER_PARAMETER_CONTENT_TYPE);
        return contentTypes.isEmpty() ? null : contentTypes.get(0);
    }

    private void writeError(final HttpExchange he, final int returnCode, final String message) throws IOException {
        he.getResponseHeaders().add(HEADER_PARAMETER_CONTENT_TYPE, CONTENT_TYPE_TEXT);
        he.sendResponseHeaders(returnCode, 0L);
        he.getResponseBody().write(message.getBytes(defaultCharset()));
    }

    private void writeJsonInstruction(final HttpExchange he, final Instruction instruction) throws IOException {
        he.getResponseHeaders().add(HEADER_PARAMETER_CONTENT_TYPE, CONTENT_TYPE_JSON);
        he.sendResponseHeaders(HTTP_OK, 0L);
        final OutputStream responseBody = he.getResponseBody();
        util.writeJson(responseBody, instruction);
    }

    private Instruction readJsonInstruction(final HttpExchange he) throws IOException {
        try (final InputStream requestBody = he.getRequestBody()) {
            return util.readJson(requestBody, Instruction.class);
        }
    }
}
