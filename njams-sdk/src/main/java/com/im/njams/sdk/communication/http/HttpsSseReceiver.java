package com.im.njams.sdk.communication.http;

import java.util.Properties;

import javax.net.ssl.SSLContext;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.sse.SseEventSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.ConnectionStatus;

/**
 * Receives SSE (server sent events) from nJAMS as HTTPS Client Communication
 *
 * @author bwand
 */
public class HttpsSseReceiver extends HttpSseReceiver {
    private static final Logger LOG = LoggerFactory.getLogger(HttpsSender.class);

    private SSLContext sslContext;

    protected static final String SSL_CERTIFIACTE_FILE = NjamsSettings.PROPERTY_HTTP_SSL_CERTIFICATE_FILE;

    /**
     * Name of the HTTP component
     */
    public static final String NAME = "HTTPS";

    @Override
    public void init(Properties properties) {
        try {
            sslContext =
                    HttpsSender.initializeSSLContext(properties.getProperty(SSL_CERTIFIACTE_FILE));
            url = createUrl(properties);
        } catch (final Exception ex) {
            throw new NjamsSdkRuntimeException("Unable to init HTTPS Receiver", ex);
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void connect() {
        if (isConnected()) {
            return;
        }
        try {
            connectionStatus = ConnectionStatus.CONNECTING;
            client = ClientBuilder.newBuilder().sslContext(sslContext).build();
            WebTarget target = client.target(url.toString() + "/subscribe");
            source = SseEventSource.target(target).build();
            source.register(this::onMessage, this::onError);
            source.open();
            LOG.debug("Subscribed SSL SSE receiver to {}", target.getUri());
            connectionStatus = ConnectionStatus.CONNECTED;
        } catch (Exception e) {
            LOG.error("Exception during registering SSL Server Sent Event Endpoint.", e);
            throw new NjamsSdkRuntimeException("Exception during registering SSL Server Sent Event Endpoint.", e);
        }
    }
}
