package com.im.njams.sdk.communication.http;

import java.util.Properties;

import javax.net.ssl.SSLContext;
import javax.ws.rs.client.ClientBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.ConnectionStatus;

/**
 * Sends Messages via HTTPS to nJAMS
 *
 * @author bwand
 */
public class HttpsSender extends HttpSender {
    private static final Logger LOG = LoggerFactory.getLogger(HttpsSender.class);

    /**
     * Name of the HTTP component
     */
    public static final String NAME = "HTTPS";

    private SSLContext sslContext;

    /**
     * Initializes this Sender via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value NjamsSettings#PROPERTY_HTTP_SSL_CERTIFICATE_FILE}
     * </ul>
     *
     * @param properties the properties needed to initialize
     */
    @Override
    public void init(Properties properties) {
        try {
            sslContext = SSLContextFactory
                    .createSSLContext(properties.getProperty(NjamsSettings.PROPERTY_HTTP_SSL_CERTIFICATE_FILE));
            super.init(properties);
            LOG.debug("Initialized HTTPS Sender with url {}", uri);
        } catch (final Exception e) {
            LOG.error("Could not initialize HTTPS Sender with url {}\n", uri, e);
        }
    }

    /**
     * Create the HTTPS Client for Events.
     */
    @Override
    public synchronized void connect() {
        if (isConnected()) {
            return;
        }
        try {
            connectionStatus = ConnectionStatus.CONNECTING;
            client = ClientBuilder.newBuilder().sslContext(sslContext).build();
            target = client.target(uri);
            testConnection();
            connectionStatus = ConnectionStatus.CONNECTED;
        } catch (final Exception e) {
            close();
            if (e instanceof NjamsSdkRuntimeException) {
                throw e;
            }
            throw new NjamsSdkRuntimeException("Failed to connect", e);
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

}
