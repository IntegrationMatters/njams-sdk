/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
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
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
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
