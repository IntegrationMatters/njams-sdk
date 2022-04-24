/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
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