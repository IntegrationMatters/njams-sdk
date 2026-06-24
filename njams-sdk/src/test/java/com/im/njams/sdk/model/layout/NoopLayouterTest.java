package com.im.njams.sdk.model.layout;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.Test;

import com.im.njams.sdk.model.ProcessModel;

/**
 * Unit tests for {@link NoopLayouter}, which intentionally performs no layout so that coordinates
 * can be set by hand.
 */
public class NoopLayouterTest {

    @Test
    public void layoutDoesNotTouchTheProcessModel() {
        ProcessModel model = mock(ProcessModel.class);
        new NoopLayouter().layout(model);
        // the no-op layouter must not read from or mutate the model
        verifyNoInteractions(model);
    }

    @Test
    public void layoutAcceptsNullWithoutThrowing() {
        // it does nothing, so even a null model must not cause an error
        new NoopLayouter().layout(null);
    }
}
