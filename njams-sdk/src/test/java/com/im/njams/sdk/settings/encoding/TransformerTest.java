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

import com.im.njams.sdk.settings.encoding.Transformer;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author krautenberg@integrationmatters.com
 */
public class TransformerTest {

    /**
     * Test of encode and decode method
     */
    @Test
    public void testEncodeAndDecode() {
        String wordToTest = "ThisStringWillBeEncodedAndDecoded";
        String firstEncodeThenDecode = Transformer.decode(Transformer.encode(wordToTest));
        assertEquals(wordToTest, firstEncodeThenDecode);
    }
    
    
    /**
     * Test of decode and encode method
     */
    @Test
    public void testDecodeAndEncode() {
        String wordToTest = "ThisStringWillBeDecodedAndEncoded";
        String firstDecodeThenEncode = Transformer.encode(Transformer.decode(wordToTest));
        //This shouldn't be equal because the decode does nothing with the not encoded word
        assertNotEquals(wordToTest, firstDecodeThenEncode);
        //It should only be encoded
        assertEquals(Transformer.encode(wordToTest), firstDecodeThenEncode);
        //And both can be decoded aswell
        assertEquals(Transformer.decode(Transformer.encode(wordToTest)), Transformer.decode(firstDecodeThenEncode));
    }
    
    /**
     * This tests double encoding
     */
    @Test
    public void testDoubleEncode(){
        String wordToTest = "ThisStringWillBeDoubleEncoded";
        String firstTime = Transformer.encode(wordToTest);
        String secondTime = Transformer.encode(firstTime);
        assertEquals(firstTime, secondTime);
    }
    
    /**
     * This tests some special characters
     */
    @Test
    public void testSomeSpecialCharacters(){
        String wordToTest = "!\"§$%&/()=?*+~#'-_@€<>|^°²³{[]}\\";
        String encoded = Transformer.encode(wordToTest);
        String decoded = Transformer.decode(encoded);
        assertEquals(wordToTest, decoded);
    }
}
