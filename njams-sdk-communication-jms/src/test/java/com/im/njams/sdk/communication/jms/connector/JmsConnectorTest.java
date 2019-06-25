package com.im.njams.sdk.communication.jms.connector;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.verification.VerificationMode;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JmsConnectorTest {

    private JmsConnectorImpl connector;

    @Before
    public void setUpConnectorImpl(){
        connector = spy(new JmsConnectorImpl());
    }

    @Test
    public void connectWhileConnected() throws Exception {
        doReturn(true).when(connector).isConnected();
        connector.connect();
        verifyConnectionHasntBeenAffected();

    }

    private void verifyConnectionHasntBeenAffected() throws Exception {
        verifyConnectMethodsHaveBeenAffected(times(0));
    }

    private void verifyConnectMethodsHaveBeenAffected(VerificationMode times) throws Exception {
        verify(connector, times).getInitialContext();
        verify(connector, times).getConnectionFactory();
        verify(connector, times).createConnection(any());
        verify(connector, times).createSession();
        verify(connector, times).startConnection();
        verify(connector, times).extConnect();
    }

    @Test
    public void connectSuccess() throws Exception {
        doReturn(false).when(connector).isConnected();
        doReturn(null).when(connector).getInitialContext();
        doReturn(null).when(connector).getConnectionFactory();
        doReturn(null).when(connector).createConnection(any());
        doReturn(null).when(connector).createSession();
        doNothing().when(connector).startConnection();

        connector.connect();
        verifyConnectionHasBeenEstablished();

    }

    private void verifyConnectionHasBeenEstablished() throws Exception {
        verifyConnectMethodsHaveBeenAffected(times(1));
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void initialContextThrowsException() throws NamingException {
        doReturn(false).when(connector).isConnected();
        doThrow(new NamingException("Test")).when(connector).getInitialContext();
        connector.connect();
    }

    @Test
    public void close() throws JMSException, NamingException {
        Session sessionToCheck = connector.session = mock(Session.class);
        Connection connectionToCheck = connector.connection = mock(Connection.class);
        InitialContext initialContextToCheck = connector.context = mock(InitialContext.class);
        connector.close();
        verifyClose(sessionToCheck, connectionToCheck, initialContextToCheck);
        verifyNull();
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void closeWithAllExceptions() throws JMSException, NamingException {
        Session sessionToCheck = connector.session = mock(Session.class);
        Connection connectionToCheck = connector.connection = mock(Connection.class);
        InitialContext initialContextToCheck = connector.context = mock(InitialContext.class);

        doThrow(new JMSException("SessionTest")).when(sessionToCheck).close();
        doThrow(new JMSException("ConnectionTest")).when(connectionToCheck).close();
        doThrow(new NamingException("InitialContextTest")).when(initialContextToCheck).close();

        try{
            connector.close();
        }catch(NjamsSdkRuntimeException ex){
            throw ex;
        } finally{
            verifyClose(sessionToCheck, connectionToCheck, initialContextToCheck);
            verifyNull();
        }
    }

    private void verifyClose(Session sessionToCheck, Connection connectionToCheck, InitialContext initialContextToCheck) throws NamingException, JMSException {
        verify(connector).extClose();
        verify(sessionToCheck).close();
        verify(connectionToCheck).close();
        verify(initialContextToCheck).close();
    }

    private void verifyNull() {
        verify(connector).extClose();
        assertNull(connector.session);
        assertNull(connector.connection);
        assertNull(connector.context);
    }

    @Test
    public void librariesToCheck() {
        String[] libs = connector.librariesToCheck();
        Set<String> libsAsSet = new HashSet<>(Arrays.asList(libs));
        verify(connector).extLibrariesToCheck();
        Set<String> libsToCheck = new HashSet<>();

        libsToCheck.add("javax.jms.Connection");
        libsToCheck.add("javax.jms.ConnectionFactory");
        libsToCheck.add("javax.jms.ExceptionListener");
        libsToCheck.add("javax.jms.JMSException");
        libsToCheck.add("javax.jms.Session");
        libsToCheck.add("javax.naming.InitialContext");
        libsToCheck.add("javax.naming.NamingException");

        libsAsSet.stream().forEach(lib -> assertTrue(libsToCheck.contains(lib)));
        libsToCheck.stream().forEach(lib -> assertTrue(libsAsSet.contains(lib)));
    }

    @Test
    public void getSession() {
        assertEquals(connector.session, connector.getSession());
    }

    private class JmsConnectorImpl extends JmsConnector{

        public JmsConnectorImpl() {
            super(new Properties(), "Test");
        }

        @Override
        protected void extConnect() throws Exception {

        }

        @Override
        protected List<Exception> extClose() {
            return new ArrayList<>();
        }

        @Override
        protected Set<String> extLibrariesToCheck() {
            return new HashSet<>();
        }
    }
}