package com.im.njams.sdk.communication.http;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.utils.StringUtils;

public class SSLContextFactory {
    private SSLContextFactory() {
        // static only
    }

    /**
     * Builds a SSL context for https connection.
     *
     * @param certificateFile If given, the according certificate is added to the context.
     * @return the SSL context
     */
    public static SSLContext createSSLContext(String certificateFile) {
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            Certificate cert = loadCertificate(certificateFile);
            if (cert != null) {
                // add to keystore as trusted entry
                keyStore.setCertificateEntry("cert-alias", cert);
            }
            tmf.init(keyStore);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);
            return sslContext;
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Could not initialize SSLContext.", e);
        }

    }

    /**
     * Loads a certificate given as path.
     * @param certificateFile The path to the certificate to load.
     * @return <code>null</code> if the given file path is <code>null</code> or empty.
     * @throws IOException
     * @throws CertificateException
     */
    private static Certificate loadCertificate(String certificateFile) throws IOException, CertificateException {
        if (StringUtils.isBlank(certificateFile)) {
            return null;
        }
        // load the keystore that includes self-signed cert as a "trusted" entry
        try (InputStream fis = new FileInputStream(certificateFile)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return cf.generateCertificate(fis);
        }
    }
}
