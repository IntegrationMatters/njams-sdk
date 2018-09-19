package com.im.njams.sdk.communication;

import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.im.njams.sdk.factories.ThreadFactoryBuilder;
import com.im.njams.sdk.settings.Settings;

public abstract class AbstractSender implements Sender, SenderFactory {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(AbstractSender.class);

    private SenderPool senderPool = null;
    private ThreadPoolExecutor executor = null;

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
    public void close() {
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

}
