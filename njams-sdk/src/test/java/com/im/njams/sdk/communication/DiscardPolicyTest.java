package com.im.njams.sdk.communication;

import static org.junit.Assert.*;

import org.junit.Test;

public class DiscardPolicyTest {

    @Test
    public void defaultPolicyIsDiscard() {
        assertEquals(DiscardPolicy.DISCARD, DiscardPolicy.DEFAULT);
    }

    @Test
    public void byValueWithUnknownReturnsDiscard() {
        assertEquals(DiscardPolicy.DISCARD, DiscardPolicy.byValue("unknown-value"));
    }

    @Test
    public void byValueWithNullReturnsDiscard() {
        assertEquals(DiscardPolicy.DISCARD, DiscardPolicy.byValue(null));
    }

    @Test
    public void byValueNone() {
        assertEquals(DiscardPolicy.NONE, DiscardPolicy.byValue("none"));
    }

    @Test
    public void byValueOnConnectionLoss() {
        assertEquals(DiscardPolicy.ON_CONNECTION_LOSS, DiscardPolicy.byValue("onconnectionloss"));
    }

    @Test
    public void byValueDiscard() {
        assertEquals(DiscardPolicy.DISCARD, DiscardPolicy.byValue("discard"));
    }
}
