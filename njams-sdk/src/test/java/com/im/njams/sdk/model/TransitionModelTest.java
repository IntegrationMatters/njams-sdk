package com.im.njams.sdk.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;

import org.junit.Test;

/**
 * Specifies {@link TransitionModel#equals(Object)} and {@link TransitionModel#hashCode()}.
 */
public class TransitionModelTest {

    private final ProcessModel processModel = mock(ProcessModel.class);

    @Test
    public void sameProcessModelAndIdAreEqualWithSameHashCode() {
        TransitionModel a = new TransitionModel(processModel, "t1", "a");
        TransitionModel b = new TransitionModel(processModel, "t1", "b");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void differentIdsAreNotEqual() {
        assertNotEquals(new TransitionModel(processModel, "t1"), new TransitionModel(processModel, "t2"));
    }

    @Test
    public void nullAndOtherTypesAreNotEqual() {
        TransitionModel a = new TransitionModel(processModel, "t1");
        assertNotEquals(a, null);
        assertNotEquals(a, "t1");
        assertEquals(a, a);
    }

    /** SDK-500: equal transitions must have equal hash codes, so the process model is part of equality. */
    @Test
    public void sameIdInDifferentProcessModelsIsNotEqual() {
        TransitionModel a = new TransitionModel(processModel, "t1");
        TransitionModel b = new TransitionModel(mock(ProcessModel.class), "t1");
        assertNotEquals(a, b);
    }
}
