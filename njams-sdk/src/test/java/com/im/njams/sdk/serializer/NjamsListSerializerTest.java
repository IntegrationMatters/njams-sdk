package com.im.njams.sdk.serializer;

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