/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.im.njams.sdk.communication.receiver.instruction.entity;

import com.im.njams.sdk.communication.receiver.instruction.control.InstructionProcessor;
import com.im.njams.sdk.communication.receiver.instruction.control.processors.FallbackProcessor;
import com.im.njams.sdk.communication.receiver.instruction.control.processors.test.TestInstructionProcessor;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class InstructionProcessorCollectionTest {

    private InstructionProcessorCollection instructionProcessorCollection;

    private final TestInstructionProcessor testInstructionProcessor = new TestInstructionProcessor();

    @Before
    public void initialize() {
        instructionProcessorCollection = new InstructionProcessorCollection();
        instructionProcessorCollection.instructionProcessors = spy(
                instructionProcessorCollection.instructionProcessors);
        instructionProcessorCollection.setDefaultIfNotNull(spy(instructionProcessorCollection.getDefault()));
    }

    @Test
    public void getDefaultIsNotNullAfterInitialize() {
        final InstructionProcessor fallbackProcessor = instructionProcessorCollection.getDefault();
        assertNotNull(fallbackProcessor);
        assertTrue(instructionProcessorCollection.getDefault() instanceof FallbackProcessor);
    }

    @Test
    public void getDefaultDoesntUseTheMap() {
        instructionProcessorCollection.getDefault();
        verifyZeroInteractionsWithMap();
    }

    private void verifyZeroInteractionsWithMap() {
        verifyZeroInteractions(instructionProcessorCollection.instructionProcessors);
    }

    private void verifyZeroInteractionWithFallbackProcessor() {
        verifyZeroInteractions(instructionProcessorCollection.getDefault());
    }

    @Test
    public void setDefaultWithNullDoesntChangeTheFallbackProcessor() {
        final InstructionProcessor fallbackProcessor = instructionProcessorCollection.getDefault();
        assertNotNull(fallbackProcessor);
        instructionProcessorCollection.setDefaultIfNotNull(null);
        assertEquals(fallbackProcessor, instructionProcessorCollection.getDefault());
        verifyZeroInteractionsWithMap();
        verifyZeroInteractionWithFallbackProcessor();
    }

    @Test
    public void setDefaultProcessor() {
        final InstructionProcessor fallbackProcessor = instructionProcessorCollection.getDefault();
        assertNotNull(fallbackProcessor);
        instructionProcessorCollection.setDefaultIfNotNull(testInstructionProcessor);
        assertNotEquals(fallbackProcessor, instructionProcessorCollection.getDefault());
        assertTrue(instructionProcessorCollection.getDefault() instanceof TestInstructionProcessor);
        verifyZeroInteractionsWithMap();
    }

    @Test
    public void setDefaultProcessorDoesntUseTheMap() {
        instructionProcessorCollection.setDefaultIfNotNull(testInstructionProcessor);
        verifyZeroInteractionsWithMap();
    }

    @Test
    public void putOneProcessor() {
        instructionProcessorCollection.putIfNotNull(TestInstructionProcessor.TEST_COMMAND, testInstructionProcessor);
        verify(instructionProcessorCollection.instructionProcessors)
                .put(TestInstructionProcessor.TEST_COMMAND, testInstructionProcessor);
        verifyZeroInteractionWithFallbackProcessor();
    }

    @Test
    public void putManyProcessors() {
        final String command2 = "b";
        final InstructionProcessor processor2 = new TestInstructionProcessor();
        assertNull(instructionProcessorCollection.get(TestInstructionProcessor.TEST_COMMAND));
        assertNull(instructionProcessorCollection.get(command2));
        instructionProcessorCollection.putIfNotNull(TestInstructionProcessor.TEST_COMMAND, testInstructionProcessor);
        instructionProcessorCollection.putIfNotNull(command2, processor2);
        assertEquals(testInstructionProcessor,
                instructionProcessorCollection.get(TestInstructionProcessor.TEST_COMMAND));
        assertEquals(processor2, instructionProcessorCollection.get(command2));
        verifyZeroInteractionWithFallbackProcessor();
    }

    @Test
    public void putWithSameKeyOverridesOldValue() {
        final InstructionProcessor processor2 = new TestInstructionProcessor();
        assertNull(instructionProcessorCollection.get(TestInstructionProcessor.TEST_COMMAND));
        instructionProcessorCollection.putIfNotNull(TestInstructionProcessor.TEST_COMMAND, testInstructionProcessor);
        assertEquals(testInstructionProcessor,
                instructionProcessorCollection.get(TestInstructionProcessor.TEST_COMMAND));
        instructionProcessorCollection.putIfNotNull(TestInstructionProcessor.TEST_COMMAND, processor2);
        assertEquals(processor2, instructionProcessorCollection.get(TestInstructionProcessor.TEST_COMMAND));
        verifyZeroInteractionWithFallbackProcessor();
    }

    @Test
    public void putWithNullKeyDoesntWork() {
        instructionProcessorCollection.putIfNotNull(null, testInstructionProcessor);

        verifyZeroInteractionsWithMap();
        verifyZeroInteractionWithFallbackProcessor();
    }

    @Test
    public void putWithNullValueDoesntWork() {
        instructionProcessorCollection.putIfNotNull(TestInstructionProcessor.TEST_COMMAND, null);

        verifyZeroInteractionsWithMap();
        verifyZeroInteractionWithFallbackProcessor();
    }

    @Test
    public void getUsesTheMap() {
        instructionProcessorCollection.get(TestInstructionProcessor.TEST_COMMAND);
        verify(instructionProcessorCollection.instructionProcessors).get(TestInstructionProcessor.TEST_COMMAND);

        verifyZeroInteractionWithFallbackProcessor();
    }

    @Test
    public void getDoesntGetTheFallbackProcessor() {
        assertTrue(instructionProcessorCollection.instructionProcessors.isEmpty());
        InstructionProcessor fallback = instructionProcessorCollection.getDefault();
        assertNotNull(fallback);
        assertTrue(instructionProcessorCollection.instructionProcessors.isEmpty());
    }

    @Test
    public void removeReturnsNullIfNothingWasThereToRemove() {
        assertTrue(instructionProcessorCollection.instructionProcessors.isEmpty());
        InstructionProcessor shouldBeNull = instructionProcessorCollection
                .remove(TestInstructionProcessor.TEST_COMMAND);
        assertNull(shouldBeNull);

        verifyZeroInteractionWithFallbackProcessor();
    }

    @Test
    public void removeReturnsTheCorrectProcessorAndRemovesIt() {
        instructionProcessorCollection.putIfNotNull(TestInstructionProcessor.TEST_COMMAND, testInstructionProcessor);
        assertEquals(testInstructionProcessor,
                instructionProcessorCollection.get(TestInstructionProcessor.TEST_COMMAND));
        InstructionProcessor shouldBeTheAddedProcessor = instructionProcessorCollection
                .remove(TestInstructionProcessor.TEST_COMMAND);
        assertEquals(testInstructionProcessor, shouldBeTheAddedProcessor);
        assertNull(instructionProcessorCollection.get(TestInstructionProcessor.TEST_COMMAND));

        verifyZeroInteractionWithFallbackProcessor();
    }

    @Test
    public void removeRemovesOnlyTheCorrectProcessor() {
        instructionProcessorCollection.putIfNotNull(TestInstructionProcessor.TEST_COMMAND, testInstructionProcessor);
        assertEquals(testInstructionProcessor,
                instructionProcessorCollection.get(TestInstructionProcessor.TEST_COMMAND));
        InstructionProcessor shouldBeNull = instructionProcessorCollection.remove("b");
        assertNull(shouldBeNull);
        assertEquals(instructionProcessorCollection.get(TestInstructionProcessor.TEST_COMMAND),
                testInstructionProcessor);

        verifyZeroInteractionWithFallbackProcessor();
    }

    @Test
    public void removeUsesTheMap() {
        instructionProcessorCollection.remove(TestInstructionProcessor.TEST_COMMAND);
        verify(instructionProcessorCollection.instructionProcessors).remove(TestInstructionProcessor.TEST_COMMAND);

        verifyZeroInteractionWithFallbackProcessor();
    }

    @Test
    public void clearClearsOnlyTheMap() {
        instructionProcessorCollection.clear();
        verify(instructionProcessorCollection.instructionProcessors).clear();

        verifyZeroInteractionWithFallbackProcessor();
    }
}