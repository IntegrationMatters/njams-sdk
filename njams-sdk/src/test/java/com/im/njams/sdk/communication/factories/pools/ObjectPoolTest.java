package com.im.njams.sdk.communication.factories.pools;

import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ObjectPoolTest {

    @Test
    public void expireAll() throws Exception {
        AutoCloseable mockedAC = mock(AutoCloseable.class);
        AutoCloseable mockedAC2 = mock(AutoCloseable.class);
        ObjectPool<AutoCloseable> op = new ObjectPool<AutoCloseable>() {
            public boolean first = true;
            @Override
            protected AutoCloseable create() {
                if(first){
                    first = false;
                    return mockedAC;
                }else{
                    return mockedAC2;
                }
            }

            @Override
            public void expire(AutoCloseable o) {
                try {
                    o.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        //To fill the locked map
        AutoCloseable get1 = op.get();
        assertEquals(mockedAC, get1);
        //To clear the locked map
        op.expireAll();
        verify(get1, times(1)).close();

        AutoCloseable get2 = op.get();
        assertEquals(mockedAC2, get2);
        op.release(get2);
        op.expireAll();
        verify(get2, times(1)).close();
        //This hasn't been closed again, because it isn't in the maps anymore
        verify(get1, times(1)).close();
    }
}