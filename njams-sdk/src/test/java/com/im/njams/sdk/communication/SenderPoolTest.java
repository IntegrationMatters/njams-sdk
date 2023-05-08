package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Test;

public class SenderPoolTest {

    @Test
    public void expireAll() throws Exception {
        CommunicationFactory mockedCF = mock(CommunicationFactory.class);
        AbstractSender mockedAC = mock(AbstractSender.class);
        AbstractSender mockedAC2 = mock(AbstractSender.class);
        SenderPool op = new SenderPool(mockedCF) {
            public boolean first = true;

            @Override
            protected AbstractSender create() {
                if (first) {
                    first = false;
                    return mockedAC;
                }
                return mockedAC2;
            }

        };
        //To fill the locked map
        AbstractSender get1 = op.get();
        assertEquals(mockedAC, get1);
        //To clear the locked map
        op.shutdown();
        verify(get1, times(1)).close();

        AbstractSender get2 = op.get();
        assertEquals(mockedAC2, get2);
        op.close(get2);
        op.shutdown();
        verify(get2, times(1)).close();
        //This hasn't been closed again, because it isn't in the maps anymore
        verify(get1, times(1)).close();
    }
}
