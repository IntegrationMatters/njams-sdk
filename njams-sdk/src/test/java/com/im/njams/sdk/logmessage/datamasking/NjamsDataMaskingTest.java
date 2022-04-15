package com.im.njams.sdk.logmessage.datamasking;

import com.im.njams.sdk.NjamsDataMasking;
import com.im.njams.sdk.logmessage.DataMasking;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class NjamsDataMaskingTest {

    private NjamsDataMasking njamsDatamasking;

    @Before
    public void init() {
        DataMasking.removePatterns();
        njamsDatamasking = new NjamsDataMasking();
    }

    @Test
    public void withDataMasking_enabled_perDefault_andMaskEverythingPattern_masksString() {
        njamsDatamasking.put("MaskAll", ".*");

        assertEquals("*****", njamsDatamasking.maskString("Hello"));
    }

    @Test
    public void withDataMasking_enabled_andMaskEverythingPattern_masksString() {
        njamsDatamasking.enableMasking();
        njamsDatamasking.put("MaskAll", ".*");

        assertEquals("*****", njamsDatamasking.maskString("Hello"));
    }

    @Test
    public void withDataMasking_disabled_andMaskEverythingPattern_doesNotAffectTheString() {
        njamsDatamasking.disableMasking();
        njamsDatamasking.put("MaskAll", ".*");

        assertEquals("Hello", njamsDatamasking.maskString("Hello"));
    }

    @Test
    public void withDataMasking_disabled_patternsCanStillBeAdded_andUsedAfterEnabling() {
        njamsDatamasking.disableMasking();
        njamsDatamasking.put("MaskAll", ".*");
        njamsDatamasking.enableMasking();

        assertEquals("*****", njamsDatamasking.maskString("Hello"));
    }

    @Test
    public void withDataMasking_enabled_withoutAnyMaskingPattern_doesNotAffectTheString() {
        assertEquals("Hello", njamsDatamasking.maskString("Hello"));
    }

    //This should probably not happen
    @Test
    public void anotherDataMasking_hasTheSameMaskingPatterns() {

        njamsDatamasking.put("MaskHello", "Hello");
        assertEquals("*****", njamsDatamasking.maskString("Hello"));

        assertEquals("*****", new NjamsDataMasking().maskString("Hello"));
//        assertEquals("hello", new NjamsDataMasking().maskString("HelloAloneShouldNotBeAffectedHere")); This should probably happen instead
    }

    //This should probably not happen
    @Test
    public void anotherDataMasking_withTheSameValue_isEffectedByTheEarlierDataMasking() {
        final NjamsDataMasking overwrittenMasking = new NjamsDataMasking();

        njamsDatamasking.put("MaskHello", "Hello");
        assertEquals("*****", njamsDatamasking.maskString("Hello"));

        overwrittenMasking.put("AnotherHelloMask", "HelloAloneShouldNotBeAffectedHere");
        assertEquals("*****AloneShouldNotBeAffectedHere", overwrittenMasking.maskString("HelloAloneShouldNotBeAffectedHere"));
//        assertEquals("*********************************", overwrittenMasking.maskString("HelloAloneShouldNotBeAffectedHere")); This should probably happen instead
    }
}