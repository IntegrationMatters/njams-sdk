package com.im.njams.sdk.njams.datamasking;

import com.im.njams.sdk.njams.NjamsDataMasking;
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
        njamsDatamasking.add("MaskAll", ".*");

        assertEquals("*****", njamsDatamasking.mask("Hello"));
    }

    @Test
    public void withDataMasking_enabled_andMaskEverythingPattern_masksString() {
        njamsDatamasking.enable();
        njamsDatamasking.add("MaskAll", ".*");

        assertEquals("*****", njamsDatamasking.mask("Hello"));
    }

    @Test
    public void withDataMasking_disabled_andMaskEverythingPattern_doesNotAffectTheString() {
        njamsDatamasking.disable();
        njamsDatamasking.add("MaskAll", ".*");

        assertEquals("Hello", njamsDatamasking.mask("Hello"));
    }

    @Test
    public void withDataMasking_disabled_patternsCanStillBeAdded_andUsedAfterEnabling() {
        njamsDatamasking.disable();
        njamsDatamasking.add("MaskAll", ".*");
        njamsDatamasking.enable();

        assertEquals("*****", njamsDatamasking.mask("Hello"));
    }

    @Test
    public void withDataMasking_enabled_withoutAnyMaskingPattern_doesNotAffectTheString() {
        assertEquals("Hello", njamsDatamasking.mask("Hello"));
    }

    @Test
    public void njamsDataMasking_shouldNotBeAffectedByOtherInstancesOfNjamsDataMasking() {
        njamsDatamasking.add("MaskHello", "Hello");
        assertEquals("*****", njamsDatamasking.mask("Hello"));

        final NjamsDataMasking anotherNjamsDataMasking = new NjamsDataMasking();
        assertEquals("HelloAloneShouldNotBeAffectedHere", anotherNjamsDataMasking.mask("HelloAloneShouldNotBeAffectedHere"));
    }

    @Test
    public void anotherDataMasking_withTheSameValue_isNotAffectedByOtherInstanceOfNjamsDataMasking() {
        njamsDatamasking.add("MaskHello", "Hello");
        assertEquals("*****", njamsDatamasking.mask("Hello"));

        final NjamsDataMasking anotherMasking = new NjamsDataMasking();
        anotherMasking.add("AnotherHelloMask", "ThisHelloShouldBeOverwrittenCompletelyInsteadOfOnlyHello");
        assertEquals("********************************************************", anotherMasking.mask("ThisHelloShouldBeOverwrittenCompletelyInsteadOfOnlyHello"));
    }

    @Test
    public void withDataMasking_disabled_andAddedPattern_shouldNotBeUsableFromDataMaskingClass() {
        njamsDatamasking.disable();
        njamsDatamasking.add("MaskHello", "Hello");

        DataMasking.maskString("Hello");

        assertEquals("Hello", DataMasking.maskString("Hello"));
    }

}