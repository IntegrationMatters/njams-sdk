package com.im.njams.sdk.communication;

import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.factories.ThreadFactoryBuilder;
import com.im.njams.sdk.settings.Settings;

/**
 * This class enforces the maxQueueLength setting. 
 * It uses the maxQueueLengthHandler to enforce the discardPolicy, if the maxQueueLength is exceeded
 * All message sending is funneled through this class, which creates and uses a pool of senders to multi-thread message sending
 * 
 * @author hsiegeln
 *
 */
public class NjamsSender implements Sender, SenderFactory {

    private SenderPool senderPool = null;
    private ThreadPoolExecutor executor = null;
    private Njams njams;
    private Settings settings;
    private String name;

    public NjamsSender(Njams njams, Settings settings) {
        this.njams = njams;
        this.settings = settings;
        this.name = settings.getProperties().getProperty(CommunicationFactory.COMMUNICATION);
        init(settings.getProperties());
    }

    @Override
    public void init(Properties properties) {
        int maxQueueLength = Integer.parseInt(properties.getProperty(Settings.PROPERTY_MAX_QUEUE_LENGTH, "8"));
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNamePrefix(getName() + "-Sender-Thread").setDaemon(true).build();
        this.executor = new ThreadPoolExecutor(maxQueueLength, maxQueueLength, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(maxQueueLength), threadFactory,
                new MaxQueueLengthHandler(properties));
        senderPool = new SenderPool(this, properties);
    }

    @Override
    public void send(CommonMessage msg) {
        executor.execute(new Runnable() {

            @Override
            public void run() {
                Sender sender = senderPool.get();
                if (sender != null) {
                    sender.send(msg);
                }
                senderPool.close(sender);
            }
        });
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * this is called by the SenderPool, if a new sender is required
     */
    @Override
    public Sender getSenderImpl() {
        return new CommunicationFactory(njams, settings).getSender();
    }

}
