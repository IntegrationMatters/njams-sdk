/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.im.njams.sdk.njams.metadata;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * NjamsMetadataFactory creates new instances of NjamsMetadata
 */
public class NjamsMetadataFactory {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NjamsMetadataFactory.class);

    private static String readMachine() {
        String machine = "unknown";
        try {
            machine = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            LOG.debug("Error getting machine name", e);
        }
        return machine;
    }

    public static NjamsMetadata createMetadataWith(Path clientPath, String defaultClientVersion, String category) {
        validate(clientPath);
        validateCategory(category);
        Map<String, String> classpathVersions = readNjamsVersionsFiles();
        final String CURRENT_YEAR = "currentYear";
        String currentYear = classpathVersions.remove(CURRENT_YEAR);
        NjamsVersions njamsVersions = fillNjamsVersions(classpathVersions, defaultClientVersion);

        return new NjamsMetadata(clientPath, njamsVersions, currentYear, readMachine(), category);
    }

    private static void validate(Path clientPath) {
        if(clientPath == null)
            throw new NjamsSdkRuntimeException("Client path need to be provided!");
    }

    private static void validateCategory(String category) {
        if(category == null)
            throw new NjamsSdkRuntimeException("Category need to be provided!");

    }


    private static Map<String, String> readNjamsVersionsFiles() {
        try {
            return readVersionsFromFilesWithName("njams.version");
        } catch (Exception e) {
            return handleFileError(e);
        }
    }

    private static Map<String, String> readVersionsFromFilesWithName(String fileName) throws IOException {
        Map<String, String> versions = new HashMap<>();
        final Enumeration<URL> urls = getAllUrlsForFileWithName(fileName);
        while (urls.hasMoreElements()) {
            final URL url = urls.nextElement();
            Map<String, String> propertiesOfOneFile = readAllPropertiesFromFileWithURL(url);
            versions.putAll(propertiesOfOneFile);
        }
        return versions;
    }

    private static Enumeration<URL> getAllUrlsForFileWithName(String fileName) throws IOException {
        return ClassLoader.getSystemClassLoader().getResources(fileName);
    }

    private static Map<String, String> readAllPropertiesFromFileWithURL(URL url) throws IOException {
        Map<String, String> keyValuePairs = new HashMap<>();
        final Properties prop = new Properties();
        prop.load(url.openStream());
        prop.forEach((key, value) -> keyValuePairs.put(String.valueOf(key), String.valueOf(value)));
        return keyValuePairs;
    }

    private static Map<String, String> handleFileError(Exception e) {
        LOG.error("Unable to load versions from njams.version files", e);
        return new HashMap<>();
    }

    private static NjamsVersions fillNjamsVersions(Map<String, String> classpathVersions, String defaultClientVersion) {
        final String sdkDefaultVersion = "4.0.0.alpha";
        return new NjamsVersions(classpathVersions).
            setSdkVersionIfAbsent(sdkDefaultVersion).
            setClientVersionIfAbsent(defaultClientVersion);
    }
}
