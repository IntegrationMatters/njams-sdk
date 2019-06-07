package com.im.njams.sdk.communication.connection.sender;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.im.njams.sdk.communication.connectable.sender.Sender;
import com.im.njams.sdk.communication.connection.NjamsConnectable;
import com.im.njams.sdk.settings.Settings;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class NjamsAbstractSender extends NjamsConnectable {

    //The logger to log messages.
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NjamsAbstractSender.class);

    //The executor Threadpool that send the messages to the right senders.
    protected ThreadPoolExecutor executor = null;

    protected final int MINQUEUELENGTH, MAXQUEUELENGTH;
    protected final long IDLETIME;
    protected final ThreadFactory THREADFACTORY;

    public NjamsAbstractSender(Properties properties) {
        MINQUEUELENGTH = Integer.parseInt(properties.getProperty(Settings.PROPERTY_MIN_QUEUE_LENGTH, "1"));
        MAXQUEUELENGTH = Integer.parseInt(properties.getProperty(Settings.PROPERTY_MAX_QUEUE_LENGTH, "8"));
        IDLETIME = Long.parseLong(properties.getProperty(Settings.PROPERTY_SENDER_THREAD_IDLE_TIME, "10000"));
        THREADFACTORY = new ThreadFactoryBuilder()
                .setNamePrefix(this.getClass().getSimpleName() + "-Thread").setDaemon(true).build();
    }

    protected void setExecutor(ThreadPoolExecutor executor){
        this.executor = executor;
    }

    protected ThreadPoolExecutor getExecutor(){
        return this.executor;
    }

    /**
     * This method closes the ThreadPoolExecutor safely. It awaits the
     * termination for 10 seconds, after that, an InterruptedException will be
     * thrown and the senders will be closed.
     */
    @Override
    protected void stopBeforeConnectablePoolStops() {
        if(executor != null) {
            try {
                int waitTime = 10;
                TimeUnit unit = TimeUnit.SECONDS;
                executor.shutdown();
                boolean awaitTermination = executor.awaitTermination(waitTime, unit);
                if (!awaitTermination) {
                    LOG.error("The termination time of the executor has been exceeded ({} {}).", waitTime, unit);
                }
            } catch (InterruptedException ex) {
                LOG.error("The shutdown of the sender's threadpool has been interrupted. {}", ex);
            }
        }
    }

    /**
     * This method starts a thread that sends the message to a sender in the
     * senderpool.
     *
     * @param msg the message that will be send to the server.
     */
    public void send(CommonMessage msg) {
        if(executor != null && !executor.isShutdown()) {
            executor.execute(() -> {
                Sender sender = null;
                try {
                    sender = (Sender) connectablePool.get();
                    if (sender != null) {
                        sender.send(msg);
                    }
                } catch (Exception e) {
                    LOG.error("could not send message {}, {}", msg, e);
                } finally {
                    if (sender != null) {
                        connectablePool.release(sender);
                    }
                }
            });
        }
    }
}
