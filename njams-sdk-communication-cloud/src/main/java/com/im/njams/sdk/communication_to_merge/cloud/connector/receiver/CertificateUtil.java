/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.communication_to_merge.cloud.connector.receiver;

import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.List;
import java.util.Properties;

/**
 * Todo: write doc
 * @author pnientiedt
 */
public class CertificateUtil {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(CertificateUtil.class);

    private static final String PropertyFile = "aws-iot-sdk-samples.properties";
    /**
     * Todo: Write Doc
     */
    public static class KeyStorePasswordPair {
        /**
         * Todo: Write Doc
         */
        public KeyStore keyStore;
        /**
         * Todo: Write Doc
         */
        public String keyPassword;

        public KeyStorePasswordPair(KeyStore keyStore, String keyPassword) {
            this.keyStore = keyStore;
            this.keyPassword = keyPassword;
        }
    }

    public static String getConfig(String name) {
        Properties prop = new Properties();
        URL resource = CertificateUtil.class.getResource(PropertyFile);
        if (resource == null) {
            return null;
        }
        try (InputStream stream = resource.openStream()) {
            prop.load(stream);
        } catch (IOException e) {
            return null;
        }
        String value = prop.getProperty(name);
        if (value == null || value.trim().length() == 0) {
            return null;
        } else {
            return value;
        }
    }

    public static KeyStorePasswordPair getKeyStorePasswordPair(final String certificateFile,
            final String privateKeyFile) {
        return getKeyStorePasswordPair(certificateFile, privateKeyFile, null);
    }

    public static KeyStorePasswordPair getKeyStorePasswordPair(final String certificateFile,
            final String privateKeyFile, String keyAlgorithm) {
        if (certificateFile == null || privateKeyFile == null) {
            LOG.debug("Certificate or private key file missing");
            return null;
        }
        LOG.debug("Cert file:" + certificateFile + " Private key: " + privateKeyFile);

        final PrivateKey privateKey = loadPrivateKeyFromFile(privateKeyFile, keyAlgorithm);

        final List<Certificate> certChain = loadCertificatesFromFile(certificateFile);

        if (certChain == null || privateKey == null) {
            return null;
        }

        return getKeyStorePasswordPair(certChain, privateKey);
    }

    public static KeyStorePasswordPair getKeyStorePasswordPair(final List<Certificate> certificates,
            final PrivateKey privateKey) {
        KeyStore keyStore;
        String keyPassword;
        try {
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null);

            // randomly generated key password for the key in the KeyStore
            keyPassword = new BigInteger(128, new SecureRandom()).toString(32);

            Certificate[] certChain = new Certificate[certificates.size()];
            certChain = certificates.toArray(certChain);
            keyStore.setKeyEntry("alias", privateKey, keyPassword.toCharArray(), certChain);
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            LOG.error("Failed to create key store");
            return null;
        }

        return new KeyStorePasswordPair(keyStore, keyPassword);
    }

    private static List<Certificate> loadCertificatesFromFile(final String filename) {
        File file = new File(filename);
        if (!file.exists()) {
            try (BufferedInputStream stream = new BufferedInputStream(
                    Thread.currentThread().getContextClassLoader().getResourceAsStream(filename))) {
                if (stream != null) {
                    final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                    return (List<Certificate>) certFactory.generateCertificates(stream);
                } else {
                    LOG.error("Certificat resource {} not found", filename);
                }
            } catch (Exception e) {
                LOG.error("Failed to load certificate from resource " + filename, e);
            }
        } else {
            try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(file))) {
                if (stream != null) {
                    final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                    return (List<Certificate>) certFactory.generateCertificates(stream);
                } else {
                    LOG.error("Certificat file {} not found", filename);
                }
            } catch (Exception e) {
                LOG.error("Failed to load certificate file " + filename, e);
            }
        }
        return null;
    }

    private static PrivateKey loadPrivateKeyFromFile(final String filename, final String algorithm) {
        File file = new File(filename);
        if (!file.exists()) {
            try (DataInputStream stream
                    = new DataInputStream(Thread.currentThread().getContextClassLoader().getResourceAsStream(filename))) {
                if (stream != null) {
                    return PrivateKeyReader.getPrivateKey(stream, algorithm);
                } else {
                    LOG.error("PrivateKey resource {} not found", filename);
                }
            } catch (Exception e) {
                LOG.error("Failed to load private key from resource " + filename, e);
            }
        } else {
            try (DataInputStream stream = new DataInputStream(new FileInputStream(file))) {
                if (stream != null) {
                    return PrivateKeyReader.getPrivateKey(stream, algorithm);
                } else {
                    LOG.error("PrivateKey file {} not found", filename);
                }
            } catch (Exception e) {
                LOG.error("Failed to load private key from file " + filename, e);
            }
        }
        return null;
    }
}
