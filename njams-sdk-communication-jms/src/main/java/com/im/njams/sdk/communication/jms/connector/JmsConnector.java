/*
 */

package com.im.njams.sdk.communication.jms.connector;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.connector.AbstractConnector;
import com.im.njams.sdk.communication.jms.JmsConstants;
import com.im.njams.sdk.communication.validator.ClasspathValidatable;
import com.im.njams.sdk.settings.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * JMS implementation for Receiver and Sender
 *
 * @author krautenberg@integrationmatters.ocm
 * @version 4.1.0
 */
public abstract class JmsConnector extends AbstractConnector implements ExceptionListener, ClasspathValidatable {

    private static final Logger LOG = LoggerFactory.getLogger(JmsConnector.class);

    protected InitialContext context;
    protected Connection connection;
    protected Session session;

    public JmsConnector(Properties properties, String name) {
        super(properties, name);
    }

    @Override
    public final void connect() {
        try {
            if (njamsConnection.isConnected()) {
                LOG.warn("Can't connect while being connected.");
                return;
            }
            context = this.getInitialContext();
            LOG.trace("The InitialContext was created successfully.");

            ConnectionFactory factory = this.getConnectionFactory();
            LOG.trace("The ConnectionFactory was created successfully.");

            this.connection = this.createConnection(factory);
            LOG.trace("The connection was created successfully.");

            this.session = this.createSession();
            LOG.trace("The Session was created successfully.");

            this.startConnection();
            LOG.trace("The connection was started successfully.");

            extConnect();
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Unable to initialize", e);
        }
    }

    protected abstract void extConnect() throws Exception;

    /**
     * This method creates a new InitialContext out of the properties that are
     * given as parameter.
     *
     * @return the InitialContext that has been created.
     * @throws NamingException is thrown if something with the name is wrong.
     */
    private InitialContext getInitialContext() throws NamingException {
        return new InitialContext(PropertyUtil.filterAndCut(properties, JmsConstants.PROPERTY_PREFIX + "."));
    }

    /**
     * This method gets a ConnectionFactory out of the properties value for
     * JmsConstants.CONNECTION_FACTORY and the context that looks up for the
     * factory.
     *
     * @return the ConnectionFactory, if found.
     * @throws NamingException is thrown if something with the name is wrong.
     */
    private ConnectionFactory getConnectionFactory() throws NamingException {
        return (ConnectionFactory) context.lookup(properties.getProperty(JmsConstants.CONNECTION_FACTORY));
    }

    /**
     * This method creates a connection out of the properties and the factory.
     * It established a secure connection with JmsConstants.USERNAME and
     * JmsConstants.PASSWORD, or if they are not provided, creates a connection
     * that uses the default username and password.
     *
     * @param factory the factory where the connection will be created from.
     * @return the connection if it can be created.
     * @throws JMSException is thrown if something is wrong with the username or
     *                      password.
     */
    private Connection createConnection(ConnectionFactory factory) throws JMSException {
        Connection con;
        if (properties.containsKey(JmsConstants.USERNAME) && properties.containsKey(JmsConstants.PASSWORD)) {
            con = factory.createConnection(properties.getProperty(JmsConstants.USERNAME),
                    properties.getProperty(JmsConstants.PASSWORD));
        } else {
            con = factory.createConnection();
        }
        return con;
    }

    /**
     * This method creates a session to the given connection. The transacted
     * boolean has been set to false and the acknowledgeMode is
     * CLIENT_ACKNOWLEDGE for the created session.
     *
     * @return the session if it can be created
     * @throws JMSException is thrown if something failed.
     */
    private Session createSession() throws JMSException {
        return connection.createSession(false, 2);
    }

    /**
     * This method starts the provided connection and sets this object as
     * exceptionListener.
     *
     * @throws JMSException is thrown if either setting the exceptionListener
     *                      didn't work or the connection didn't start.
     */
    private void startConnection() throws JMSException {
        connection.setExceptionListener(this);
        connection.start();
    }

    /**
     * This method logs all JMS Exceptions and tries to reconnect the
     * connection.
     *
     * @param exception The jmsException to be logged.
     */
    @Override
    public void onException(JMSException exception) {
        njamsConnection.onException(new NjamsSdkRuntimeException("Transport error", exception));
    }

    /**
     * This method closes all resources.
     */
    @Override
    public final void close() {
        List<Exception> exceptions = new ArrayList<>();
        exceptions.addAll(extClose());
        if (session != null) {
            try {
                session.close();
            } catch (JMSException ex) {
                exceptions.add(new NjamsSdkRuntimeException("Unable to close session correctly", ex));
            } finally {
                session = null;
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (JMSException ex) {
                exceptions.add(new NjamsSdkRuntimeException("Unable to close connection correctly", ex));
            } finally {
                connection = null;
            }
        }
        if (context != null) {
            try {
                context.close();
            } catch (NamingException ex) {
                exceptions.add(new NjamsSdkRuntimeException("Unable to close initial context correctly", ex));
            } finally {
                context = null;
            }
        }
        if (!exceptions.isEmpty()) {
            exceptions.forEach(exception -> LOG.error(exception.getMessage()));
            throw new NjamsSdkRuntimeException("Unable to close jms connector");
        } else {
            LOG.info("JmsConnector has been closed.");
        }
    }

    protected abstract List<Exception> extClose();

    /**
     * This method gets all libraries that need to be checked.
     *
     * @return an array of Strings of fully qualified class names.
     */
    public String[] librariesToCheck() {
        Set<String> libs = new HashSet<>();

        libs.add("javax.jms.Connection");
        libs.add("javax.jms.ConnectionFactory");
        libs.add("javax.jms.ExceptionListener");
        libs.add("javax.jms.JMSException");
        libs.add("javax.jms.Session");
        libs.add("javax.naming.InitialContext");
        libs.add("javax.naming.NamingException");
        Set<String> additionalLibs = extLibrariesToCheck();
        if (additionalLibs != null && !additionalLibs.isEmpty()) {
            libs.addAll(additionalLibs);
        }
        String[] toRet = new String[libs.size()];
        return libs.toArray(toRet);
    }

    protected abstract Set<String> extLibrariesToCheck();

    public Session getSession() {
        return session;
    }
}

