package com.im.njams.sdk.communication.http;

import java.util.Properties;

import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Receives SSE (server sent events) from nJAMS as HTTPS Client Communication
 *
 * @author bwand
 */
public class HttpsSseReceiver extends HttpSseReceiver {
    private static final Logger LOG = LoggerFactory.getLogger(HttpsSender.class);

    private SSLContext sslContext = null;

    /**
     * Name of the HTTP component
     */
    public static final String NAME = "HTTPS";

    @Override
    public void init(Properties properties) {
        super.init(properties);
        try {
            sslContext = SSLContextFactory.createSSLContext(
                    properties.getProperty(NjamsSettings.PROPERTY_HTTP_SSL_CERTIFICATE_FILE));
        } catch (final Exception ex) {
            throw new NjamsSdkRuntimeException("Unable to init HTTPS Receiver", ex);
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected Client createClient() {
        LOG.debug("Creating new https client.");
        return ClientBuilder.newBuilder().sslContext(sslContext).build();
    }
}
