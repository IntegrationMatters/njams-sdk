package com.im.njams.sdk;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.settings.Settings;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class NjamsInitializationTest {
    @Test
    public void withoutPath_throwsAnNjamsSdkRuntimeException(){
        NjamsSdkRuntimeException njamsSdkRuntimeException = assertThrows(NjamsSdkRuntimeException.class,
            () -> new Njams(null, "SDK", new Settings()));
        assertThat(njamsSdkRuntimeException.getMessage(), is(equalTo("Client path need to be provided!")));
    }

    /**
     * Is this correct? Shouldn't it be optional to set a category?
     */
    @Test
    public void withoutCategory_throwsAnNjamsSdkRuntimeException(){
        NjamsSdkRuntimeException njamsSdkRuntimeException = assertThrows(NjamsSdkRuntimeException.class,
            () -> new Njams(new Path(), null, new Settings()));
        assertThat(njamsSdkRuntimeException.getMessage(), is(equalTo("Category need to be provided!")));
    }

    @Test
    public void withoutSettings_throwsAnNjamsSdkRuntimeException(){
        NjamsSdkRuntimeException njamsSdkRuntimeException = assertThrows(NjamsSdkRuntimeException.class,
            () -> new Njams(new Path(), "SDK", null));
        assertThat(njamsSdkRuntimeException.getMessage(), is(equalTo("Settings need to be provided!")));
    }

    @Test
    public void njamsReceiverIsAvailable_afterInitialization(){
        Njams njams = new Njams(new Path(), "SDK", new Settings());
        assertNotNull(njams.getNjamsReceiver());
    }
}
