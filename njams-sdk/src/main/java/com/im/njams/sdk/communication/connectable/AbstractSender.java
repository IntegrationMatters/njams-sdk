/*
 */
package com.im.njams.sdk.communication.connectable;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.connector.Connector;
import com.im.njams.sdk.communication.connector.NjamsConnection;
import com.im.njams.sdk.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Superclass for all Senders. When writing your own Sender, extend this class and overwrite methods, when needed.
 * All Sender will be automatically pooled by the SDK; you must not implement your own connection pooling!
 *
 * @author hsiegeln/krautenberg
 * @version 4.0.6
 */
public abstract class AbstractSender implements Sender {

    //The Logger
    private static final Logger LOG = LoggerFactory.getLogger(AbstractSender.class);
    //The discard policy for messages that couldn't be delivered, default is "none".
    protected String discardPolicy = "none";
    //The connector for this sender.
    protected Connector connector;
    //The connection for this sender.
    private NjamsConnection njamsConnection;

    protected SenderUtil util;


    /**
     * Initializes this Sender via the given Properties.
     *
     * @param properties the properties needed to initialize
     */
    @Override
    public final void init(Properties properties) {
        discardPolicy = properties.getProperty(Settings.PROPERTY_DISCARD_POLICY, "none").toLowerCase();
        connector = initialize(properties);
        connector.start();
        njamsConnection = connector.getNjamsConnection();
        util = new SenderUtil();
    }

    protected abstract Connector initialize(Properties properties);

    /**
     * This method sends the given message. It applies the discardPolicy onConnectionLoss, if set respectively in the
     * properties.
     *
     * @param msg the message to send
     */
    @Override
    public void send(CommonMessage msg) {
        // do this until message is sent or discard policy onConnectionLoss is satisfied
        do {
            if (njamsConnection.isConnected()) {
                try {
                    if (msg instanceof LogMessage) {
                        send((LogMessage) msg);
                    } else if (msg instanceof ProjectMessage) {
                        send((ProjectMessage) msg);
                    } else if (msg instanceof TraceMessage) {
                        send((TraceMessage) msg);
                    }
                    break;
                } catch (NjamsSdkRuntimeException e) {
                    //Try to reconnect
                    if (!njamsConnection.isError()) {
                        njamsConnection.onException(e);
                    }
                }
            }
            if (njamsConnection.isDisconnected()) {
                // discard message, if onConnectionLoss is used
                if (discardPolicy.equalsIgnoreCase("onconnectionloss")) {
                    LOG.debug("Applying discard policy [{}]. Message discarded.", discardPolicy);
                    break;
                }
            }
            // wait for reconnect
            if (njamsConnection.isConnecting()) {
                try {
                    Thread.sleep(njamsConnection.getReconnectInterval());
                } catch (InterruptedException e) {
                    break;
                }
            } else if (!njamsConnection.isError()) {
                // trigger reconnect
                njamsConnection.onException(null);
            }
        } while (true);
    }

    /**
     * Implement this method to send LogMessages
     *
     * @param msg the message to send
     * @throws NjamsSdkRuntimeException NjamsSdkRuntimeException
     */
    protected abstract void send(LogMessage msg) throws NjamsSdkRuntimeException;

    /**
     * Implement this method to send ProjectMessages
     *
     * @param msg the message to send
     * @throws NjamsSdkRuntimeException NjamsSdkRuntimeException
     */
    protected abstract void send(ProjectMessage msg) throws NjamsSdkRuntimeException;

    /**
     * Implement this method to send TraceMessages
     *
     * @param msg the message to send
     * @throws NjamsSdkRuntimeException NjamsSdkRuntimeException
     */
    protected abstract void send(TraceMessage msg) throws NjamsSdkRuntimeException;

    @Override
    public final void stop(){
        connector.close();
    }

    @Override
    public Connector getConnector(){
        return connector;
    }
}
