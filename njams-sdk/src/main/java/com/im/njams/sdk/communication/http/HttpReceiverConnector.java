/*
 */

package com.im.njams.sdk.communication.http;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.settings.encoding.Transformer;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class HttpReceiverConnector extends HttpConnector {

    private static final Logger LOG = LoggerFactory.getLogger(HttpReceiverConnector.class);

    private HttpServer httpServer;

    private HttpReceiver httpReceiver;

    private final int port;

    public HttpReceiverConnector(Properties properties, String name, HttpReceiver httpReceiver) {
        super(properties, name);
        this.httpReceiver = httpReceiver;
        this.port = Integer.parseInt(Transformer.decode(properties.getProperty(HttpConstants.RECEIVER_PORT)));
    }

    @Override
    public final void connect() {
        final InetSocketAddress isa = new InetSocketAddress(port);
        try {
            httpServer = HttpServer.create(isa, 5);
            LOG.debug("The HttpServer was created successfully.");

            httpServer.createContext("/command", httpReceiver);
            LOG.debug("The HttpContext was created successfully.");

            httpServer.start();
            LOG.debug("The HttpServer was started successfully.");
        } catch (final IOException ex) {
            throw new NjamsSdkRuntimeException("unable to create http server", ex);
        }
    }

    @Override
    protected List<Exception> extClose() {
        List<Exception> exceptions = new ArrayList<>();
        if (httpServer != null) {
            try {
                httpServer.stop(0);
            }catch(Exception ex){
                exceptions.add(new NjamsSdkRuntimeException("Unable to close httpServer correctly", ex));
            }finally{
                httpServer = null;
            }
        }
        return exceptions;
    }
}
