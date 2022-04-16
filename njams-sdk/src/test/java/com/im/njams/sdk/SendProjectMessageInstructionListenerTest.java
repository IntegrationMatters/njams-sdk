package com.im.njams.sdk;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.logmessage.NjamsFeatures;
import com.im.njams.sdk.logmessage.NjamsJobs;
import com.im.njams.sdk.logmessage.NjamsProjectMessage;
import com.im.njams.sdk.logmessage.NjamsState;
import com.im.njams.sdk.metadata.NjamsMetadataFactory;
import com.im.njams.sdk.settings.Settings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SendProjectMessageInstructionListenerTest {

    @Test
    public void testOnCorrectSendProjectMessageInstruction() {
        NjamsProjectMessage projectMessage = new NjamsProjectMessage(
            NjamsMetadataFactory.createMetadataWith(new Path(), "blub", "bla2"), new NjamsFeatures(),
            new NjamsConfiguration(new Configuration(), null, null, null), new NjamsSender(new TestSender()),
            new NjamsState(), new NjamsJobs(null, new NjamsState(), new NjamsFeatures(), null), new Settings());

        Instruction inst = new Instruction();
        Request req = new Request();
        req.setCommand(Command.SEND_PROJECTMESSAGE.commandString());
        inst.setRequest(req);
        assertNull(inst.getResponse());
        projectMessage.onInstruction(inst);
        Response resp = inst.getResponse();
        assertTrue(resp.getResultCode() == 0);
        assertEquals("Successfully sent ProjectMessage via NjamsClient", resp.getResultMessage());
    }
}