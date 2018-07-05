/* 
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.im.njams.sdk.common.JsonSerializerFactory;
import java.io.StringWriter;

/**
 * Json Serializer
 * @author stkniep
 * @param <T> generic
 */
public class JsonSerializer<T> implements Serializer<T> {

    private final ObjectMapper objectMapper = JsonSerializerFactory.getDefaultMapper();
    private final ObjectWriter objectWriter = this.objectMapper.writer();

    /**
     * Serialize
     *
     * @param object to serialize
     * @return serielized object
     * @throws Exception exception
     */
    @Override
    public String serialize(final T object) throws Exception {
        if (object == null) {
            return "{}";
        }
        final StringWriter writer = new StringWriter();
        objectWriter.writeValue(writer, object);
        return writer.toString();
    }

}
