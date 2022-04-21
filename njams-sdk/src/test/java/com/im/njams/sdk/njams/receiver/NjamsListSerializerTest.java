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
 *
 */

package com.im.njams.sdk.njams.receiver;

import com.im.njams.sdk.njams.NjamsSerializers;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

public class NjamsListSerializerTest {

    private NjamsSerializers njamsSerializers;

    @Before
    public void setUp(){
        njamsSerializers = new NjamsSerializers();

        njamsSerializers.add(ArrayList.class, list -> "Found the right " + list.getClass().getSimpleName() + " implementation!");
        njamsSerializers.add(List.class, list -> "Found the more general List serializer!");
    }

    @Test
    public void arrayListSerializer_wasFoundAndUsed(){
        final ArrayList<Object> arrayList = new ArrayList<>();
        assertEquals("Found the right ArrayList implementation!", njamsSerializers.serialize(arrayList));
    }

    @Test
    public void linkedListSerializer_findsAndUsesTheMoreGeneralListSerializer(){
        final LinkedList<Object> linkedList = new LinkedList<>();
        assertEquals("Found the more general List serializer!", njamsSerializers.serialize(linkedList));
    }

    @Test
    public void notAList_butAHashMap_canOnlyFindAStringSerializer() {
        final HashMap<Object, Object> hashMap = new HashMap<>();
        assertEquals("{}", njamsSerializers.serialize(hashMap));
    }
}