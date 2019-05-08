/*
 */

/*
 */
package com.im.njams.sdk.communication.jms;

import com.im.njams.sdk.communication.jms.connectable.JmsReceiver;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.when;

/**
 * This class is a extended version of the JmsReceiver to get its fields.
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.5
 */
class JmsReceiverMock extends JmsReceiver {
    /*
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(JmsReceiverMock.class);
    
    private static final String MESSAGESELECTORSTRING = "NJAMS_RECEIVER = '>SDK4>' OR NJAMS_RECEIVER = '>SDK4>TEST>'";

    public JmsReceiverMock() {
        Njams njamsImpl = mock(Njams.class);
        Path path = mock(Path.class);
        when(njamsImpl.getClientPath()).thenReturn(path);
        when(path.toString()).thenReturn("SDK4>TEST");
        super.setNjams(njamsImpl);
    }

    //Getter
    public Connection getConnection() {
        return (Connection) this.getThisObjectPrivateField("connection");
    }

    public Session getSession() {
        return (Session) this.getThisObjectPrivateField("session");
    }

    public Properties getProperties() {
        return (Properties) this.getThisObjectPrivateField("properties");
    }

    public MessageConsumer getConsumer() {
        return (MessageConsumer) this.getThisObjectPrivateField("consumer");
    }

    public String getTopicName() {
        return (String) this.getThisObjectPrivateField("topicName");
    }

    public ObjectMapper getMapper() {
        return (ObjectMapper) this.getThisObjectPrivateField("mapper");
    }

    public String getMessageSelector() {
        return (String) this.getThisObjectPrivateField("messageSelector");
    }
    
    public void setConnectionStatus(ConnectionStatus connectionStatus){
        this.connectionStatus = connectionStatus;
    }

    *//**
     * This method gets a private field of this class
     *
     * @param fieldName the name of the field
     *//*
    private Object getThisObjectPrivateField(String fieldName) {
        try {
            Class clazz = JmsReceiver.class;
            Field privateField = clazz.getDeclaredField(fieldName);
            privateField.setAccessible(true);
            return privateField.get(this);
        } catch (NullPointerException | NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
            return null;
        }
    }
    
    *//**
     * This method tests if the properties that are set in init are null.
     * @param impl the instance to look in
     *//*
    public static void testBeforeInit(JmsReceiverMock impl) {
        assertNull(impl.getProperties());
        assertNull(impl.getTopicName());
        assertNull(impl.getMapper());
        assertNull(impl.getMessageSelector());
        testBeforeConnect(impl);
    }
    
    *//**
     * This method tests if the properties that are set in init are filled,
     * that the status is DISCONNECTED and that the properties that are set in 
     * connect() are null.
     * @param impl the instance to look in
     * @param props the properties to compare with
     *//*
    public static void testAfterInit(JmsReceiverMock impl, Properties props) {
        assertTrue(impl.isDisconnected());
        testBeforeConnect(impl);
        //Stays the same
        assertNotNull(impl.getMapper());
        assertEquals(props, impl.getProperties());
        if (props.containsKey(JmsConstants.COMMANDS_DESTINATION)) {
            assertEquals(props.getProperty(JmsConstants.COMMANDS_DESTINATION), impl.getTopicName());
        } else {
            assertEquals(props.getProperty(JmsConstants.DESTINATION) + ".commands", impl.getTopicName());
        }
        assertEquals(MESSAGESELECTORSTRING, impl.getMessageSelector());
    }

    *//**
     * This method tests if the properties that are set in connect() are null
     * @param impl the instance to look in
     *//*
    public static void testBeforeConnect(JmsReceiverMock impl) {
        assertNull(impl.getConnection());
        assertNull(impl.getSession());
        assertNull(impl.getConsumer());
    }

    *//**
     * This method tests if the properties that are set in connect() are set
     * @param impl the instance to look in
     *//*
    public static void testAfterConnect(JmsReceiverMock impl) {
        assertTrue(impl.isConnected());
        assertNotNull(impl.getConnection());
        assertNotNull(impl.getSession());
        assertNotNull(impl.getConsumer());
    }
    
    *//**
     * This method is overridden to avoid reconnecting with a connection that 
     * doesn't exist.
     *//*
    @Override
    public void reconnect(NjamsSdkRuntimeException ex){
        LOG.info("reconnected");
        connectionStatus = ConnectionStatus.CONNECTED;
    }*/
}
