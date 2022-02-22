package com.im.njams.sdk.communication.http;

import java.util.Properties;

import javax.net.ssl.SSLContext;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.sse.SseEventSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Receives SSE (server sent events) from nJAMS as HTTPS Client Communication
 *
 * @author bwand
 */
public class HttpsSseReceiver extends HttpSseReceiver {
    private static final Logger LOG = LoggerFactory.getLogger(HttpsSender.class);

    private SSLContext sslContext;

    protected static final String SSL_CERTIFIACTE_FILE = ".ssl.certificate.file";

    /**
     * Name of the HTTP component
     */
    public static final String NAME = "HTTPS";

    @Override
    public void init(Properties properties) {
        try {
            sslContext =
                    HttpsSender.initializeSSLContext(properties.getProperty(PROPERTY_PREFIX + SSL_CERTIFIACTE_FILE));
            url = createUrl(properties);
        } catch (final Exception ex) {
            throw new NjamsSdkRuntimeException("Unable to init HTTPS Receiver", ex);
        }
        mapper = JsonSerializerFactory.getDefaultMapper();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void connect() {
        try {
            client = ClientBuilder.newBuilder().sslContext(sslContext).build();
            target = client.target(url.toString() + "/subscribe");
            source = SseEventSource.target(target).build();
            source.register(this::onMessage);
            source.open();
            LOG.debug("Subscribed SSE receiver to {}", target.getUri());
        } catch (Exception e) {
            LOG.error("Exception during registering Server Sent Event Endpoint.", e);
        }
    }
}
