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
package com.im.njams.sdk.settings.encoding;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author krautenberg@integrationmatters.com
 */
public class CMDHandlerTest {

    /**
     * Tests if the encoded words from cmd were encoded correctly.
     */
    @Test
    public void testWordInputs() {
        String[] words = {"foo", "bar", "admin"};
        String[] encodedWords = new String[words.length];
        for(int i = 0; i < words.length; i++){
            encodedWords[i] = Transformer.encode(words[i]);
        }
        String[] wordsFromCMDEncoded = {"??0190029006d0048004c0055000c0017002d00090005006a004d007100b500bd",
            "??04800730044004a0070004f0004004c0034006c000c0021005a006400590076",
            "??0270070006f004a002a003d00630040000c001f004900810046008d00700082"};
        assertArrayEquals(encodedWords, wordsFromCMDEncoded);
    }
}
