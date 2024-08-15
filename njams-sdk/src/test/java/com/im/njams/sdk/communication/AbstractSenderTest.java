package com.im.njams.sdk.communication;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.jms.JmsSender;
import com.im.njams.sdk.settings.Settings;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ThreadPoolExecutor;

import static com.im.njams.sdk.communication.AbstractSender.hasConnected;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class AbstractSenderTest {

    @Test
    public void logDiscardMessageTest() {
        final JmsSender sender = spy(new JmsSender());
        doThrow(new NjamsSdkRuntimeException("Unable to connect", new Exception())).when(sender).connect();
        doNothing().when(sender).reconnect(any());
        sender.startup();
        verify(sender, times(1)).getDiscardPolicyWarning();
    }


    @Test
    public void discardMessagesOnConnectionLossTest(){
        final JmsSender sender = spy(new JmsSender());
        doThrow(new NjamsSdkRuntimeException("Unable to connect", new Exception())).when(sender).connect();
        doNothing().when(sender).reconnect(any());
        final MaxQueueLengthHandler maxQueueLengthHandler = spy(new MaxQueueLengthHandler(new Properties()));
        Whitebox.setInternalState(maxQueueLengthHandler, "discardPolicy", DiscardPolicy.ON_CONNECTION_LOSS);
        doNothing().when(maxQueueLengthHandler).blockRuntime(any(), any());
        sender.startup();
        maxQueueLengthHandler.rejectedExecution(any(), any());
        verify(maxQueueLengthHandler, atLeast(1)).discard();
        verify(maxQueueLengthHandler, times(0)).blockRuntime(any(), any());

    }

}
