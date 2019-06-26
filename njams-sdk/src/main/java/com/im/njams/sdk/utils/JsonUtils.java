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
package com.im.njams.sdk.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.common.JsonSerializerFactory;

import java.io.IOException;

/**
 * Utilities for serializing to and parsing from JSON.
 *
 * @author cwinkler
 *
 */
public class JsonUtils {

    private JsonUtils() {
        // static only
    }

    /**
     * Parses the given JSON string and initializes a new object of the given type from that JSON.
     *
     * @param <T>
     *            The type of the object to return
     * @param json
     *            The JSON string to parse.
     * @param type
     *            The type of the object to be created from the given JSON.
     * @return New instance of the given type.
     * @throws Exception
     *             If parsing the given JSON into the target type failed.
     */
    public static <T> T parse(String json, Class<T> type) throws IOException {
        return getMapper().readValue(json, type);
    }

    /**
     * Serializes the given object to a JSON string-
     *
     * @param object
     *            The object to serialize.
     * @return JSON string representing the given object.
     * @throws Exception
     *             If serializing the object to JSON failed.
     */
    public static String serialize(Object object) throws JsonProcessingException {
        return getMapper().writeValueAsString(object);
    }

    private static ObjectMapper getMapper(){
        return JsonSerializerFactory.getDefaultMapper();
    }
}
