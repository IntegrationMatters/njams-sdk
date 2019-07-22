package com.im.njams.sdk.communication_to_merge.cloud.connector;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication_to_merge.cloud.CloudConstants;
import com.im.njams.sdk.communication_to_merge.cloud.connector.receiver.CloudReceiverConnector;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.Properties;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CloudReceiverConnectorTest {

    private static Njams njams;
    private CloudReceiverConnectorStub crc;

    private static final Path CLIENTPATH = new Path("Test");
    private static final String CLIENTVERSION = "4.0.0";
    private static final String SDKVERSION = "4.1.0";
    private static final String MACHINE = "localhost";
    private static final String INSTANCEID = "TestInstance";
    private static final String APIKEYPATH = "src" + File.separator + "test" + File.separator + "resources" + File.separator + "api.key";

    @BeforeClass
    public static void initNjams() {
        njams = mock(Njams.class);

        when(njams.getClientPath()).thenReturn(CLIENTPATH);
        when(njams.getClientVersion()).thenReturn(CLIENTVERSION);
        when(njams.getSdkVersion()).thenReturn(SDKVERSION);
        when(njams.getMachine()).thenReturn(MACHINE);
    }

    @Before
    public void init() {
        Properties props = new Properties();
        props.setProperty(CloudConstants.CLIENT_INSTANCEID, INSTANCEID);
        props.setProperty(CloudConstants.APIKEY, APIKEYPATH);

        crc = new CloudReceiverConnectorStub(props, "Cloud-Receiver-Test", njams);
    }

    @Test
    public void testGetInitialPayload() {
        String staticPayload = "{\"connectionId\":\"" + crc.getUuid().toString() + "\", \"instanceId\":\"" + INSTANCEID + "\", \"path\":\"" + CLIENTPATH.toString() + "\", \"clientVersion\":\"" + CLIENTVERSION + "\", \"sdkVersion\":\"" + SDKVERSION + "\", \"machine\":\"" + MACHINE + "\" }";
        String semiDynamicPayload = "{\"connectionId\":\"" + crc.getUuid().toString() + "\", \"instanceId\":\"" + crc.getInstanceId() + "\", \"path\":\"" + njams.getClientPath().toString() + "\", \"clientVersion\":\"" + njams.getClientVersion() + "\", \"sdkVersion\":\"" + njams.getSdkVersion() + "\", \"machine\":\"" + njams.getMachine() + "\" }";
        String payload = crc.getInitialPayload(njams);

        assertEquals(staticPayload, semiDynamicPayload);
        assertEquals(staticPayload, payload);
        assertEquals(semiDynamicPayload, payload);
    }

    @Test
    public void testGetTopicName() {
        String staticTopicName = "/" + INSTANCEID + "/commands/" + crc.getUuid().toString() + "/";
        String semiDynamicTopicName = "/" + crc.getInstanceId() + "/commands/" + crc.getUuid().toString() + "/";
        String topicName = crc.getTopicName();

        assertEquals(staticTopicName, semiDynamicTopicName);
        assertEquals(staticTopicName, topicName);
        assertEquals(semiDynamicTopicName, topicName);
    }

    private class CloudReceiverConnectorStub extends CloudReceiverConnector {

        public CloudReceiverConnectorStub(Properties properties, String name, Njams njams) {
            super(properties, name, njams);
        }

        @Override
        protected void setEndpoint() {
        }

        @Override
        protected void createKeyStorePasswordPair(String certificateFile, String privateKeyFile) {
        }
    }
}