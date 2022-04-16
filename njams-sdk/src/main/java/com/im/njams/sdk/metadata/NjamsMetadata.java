/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.im.njams.sdk.metadata;

import com.im.njams.sdk.common.Path;

import java.util.Map;
import java.util.Objects;

public class NjamsMetadata {
    private final Path clientPath;
    private final String machine;
    private final String category;
    private final NjamsVersions njamsVersion;
    private final String currentYear;

    NjamsMetadata(Path clientPath, NjamsVersions njamsVersions, String currentYear, String machine, String category) {
        this.clientPath = clientPath;
        this.njamsVersion = njamsVersions;
        this.currentYear = currentYear;
        this.machine = machine;
        this.category = category;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 83 * hash + Objects.hashCode(getClientPath());
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final NjamsMetadata other = (NjamsMetadata) obj;
        return Objects.equals(getClientPath(), other.getClientPath());
    }

    public String getClientVersion(){
        return njamsVersion.getClientVersion();
    }

    public String getSdkVersion(){
        return njamsVersion.getSdkVersion();
    }

    public Path getClientPath() {
        return clientPath;
    }

    public String getMachine() {
        return machine;
    }

    public String getCategory() {
        return category;
    }

    public NjamsVersions getNjamsVersion() {
        return njamsVersion;
    }

    public String getCurrentYear() {
        return currentYear;
    }

    public Map<String, String> getAllVersions(){
        return njamsVersion.getAllVersions();
    }
}
