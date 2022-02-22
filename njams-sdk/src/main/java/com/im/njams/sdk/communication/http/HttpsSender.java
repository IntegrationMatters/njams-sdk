package com.im.njams.sdk.communication.http;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Properties;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.ws.rs.client.ClientBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.ConnectionStatus;

/**
 * Sends Messages via HTTPS to nJAMS
 *
 * @author bwand
 */
public class HttpsSender extends HttpSender {
    private static final Logger LOG = LoggerFactory.getLogger(HttpsSender.class);

    protected static final String SSL_CERTIFIACTE_FILE = ".ssl.certificate.file";

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
     * <li>{@value com.im.njams.sdk.communication.http.HttpSender#BASE_URL}
     * <li>{@value com.im.njams.sdk.communication.http.HttpSender#INGEST_ENDPOINT}
     * </ul>
     *
     * @param properties the properties needed to initialize
     */
    @Override
    public void init(Properties properties) {
        this.properties = properties;
        mapper = JsonSerializerFactory.getDefaultMapper();
        try {
            sslContext = initializeSSLContext(properties.getProperty(PROPERTY_PREFIX + SSL_CERTIFIACTE_FILE));
            url = createUrl(properties);
            connect();
            LOG.debug("Initialized HTTPS Sender with url {}", url);
        } catch (final Exception e) {
            LOG.error("Could not initialize HTTPS Sender with url {}\n", url, e);
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
            target = client.target(String.valueOf(url));
            connectionStatus = ConnectionStatus.CONNECTED;
        } catch (final Exception e) {
            connectionStatus = ConnectionStatus.DISCONNECTED;
            if (client != null) {
                client.close();
                client = null;
                target = null;
            }
            throw new NjamsSdkRuntimeException("Unable to connect", e);
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * This Method gets a certificate and uses it to initalize a SSLContext with it.
     *
     * @param certificateFile the certificate to connect to nJAMS
     * @return the ssl context
     */
    public static SSLContext initializeSSLContext(String certificateFile) {
        try {
            InputStream fis = new FileInputStream(certificateFile);
            if (fis == null) {
                throw new RuntimeException("Certificate File not found: " + certificateFile);
            }

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate cert = cf.generateCertificate(fis);
            // load the keystore that includes self-signed cert as a "trusted" entry
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            keyStore.setCertificateEntry("cert-alias", cert);
            tmf.init(keyStore);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);
            return sslContext;
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Could not initialize SSLContext.", e);
        }

    }
}
