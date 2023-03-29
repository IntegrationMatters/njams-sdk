/*
 * Copyright (c) 2023 Integration Matters GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.http;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Factory for creating {@link SSLContext} when https is configured.
 *  
 * @author cwinkler
 *
 */
public class SSLContextFactory {
    private SSLContextFactory() {
        // static only
    }

    /**
     * Returns whether or not the given URI uses <code>https</code> scheme.
     * @param uri The URI to test
     * @return <code>true</code> only if the given URI uses https
     */
    public static boolean isSslUri(URI uri) {
        if (uri == null) {
            return false;
        }
        return "https".equalsIgnoreCase(uri.getScheme());
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
