/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.communication.cloud;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ApiKeyReader {
    
    /**
     * Get a Api Key from InputStream.
     *
     * @param fileName file name
     * @return Api key
     * @throws IOException IOException resulted from invalid file IO
     */
    public static String getApiKey(String fileName) throws IOException {
        try (InputStream stream = new FileInputStream(fileName)) {
            return getApiKey(stream);
        }
    }
    
    /**
     * Get a Api Key for the file.
     *
     * @param stream InputStream object
     * @return Api key
     * @throws IOException IOException resulted from invalid file IO
     */
    public static String getApiKey(InputStream stream) throws IOException {
       
        BufferedReader br = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder builder = new StringBuilder();
        
        for (String line = br.readLine(); line != null; line = br.readLine()) {
          builder.append(line);
        }
        
        return builder.toString();
    }

    
}
