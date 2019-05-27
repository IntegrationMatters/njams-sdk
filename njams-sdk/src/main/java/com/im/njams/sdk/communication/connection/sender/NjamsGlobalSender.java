package com.im.njams.sdk.communication.connection.sender;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication.pools.SenderPool;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * This class is for sending messages to the server by getting the appropriate
 * senders out of a senderPool.
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.0
 */
public final class NjamsGlobalSender extends NjamsAbstractSender {

    private static final Map<NjamsGlobalSender, Njams> NJAMS_INSTANCES = new ConcurrentHashMap<>();

    //The executor to execute a send command
    private static ThreadPoolExecutor globalExecutor = null;

    //The senderPool where the senders will be safed.
    private static SenderPool globalPool = null;

    /**
     * This constructor initializes a NjamsSender. It safes the njams instance,
     * the settings and gets the name for the executor threads from the settings
     * with the key: njams.sdk.communication.
     *
     * @param njams the njamsInstance for which the messages will be send from.
     */
    public NjamsGlobalSender(Njams njams, Properties properties) {
        super(properties);
        synchronized (NJAMS_INSTANCES) {
            NJAMS_INSTANCES.put(this, njams);

            if (globalPool == null) {
                NjamsGlobalSender.globalPool = new SenderPool(njams, properties);
            }
            super.setConnectablePool(globalPool);

            if (globalExecutor == null) {
                NjamsGlobalSender.globalExecutor = new ThreadPoolExecutor(MINQUEUELENGTH, MAXQUEUELENGTH, IDLETIME, TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(MAXQUEUELENGTH), THREADFACTORY,
                        new MaxQueueLengthHandler(properties));
            }
            super.setExecutor(globalExecutor);
        }
    }

    @Override
    protected final void stopBeforeConnectablePoolStops(){
        if(executor != null){
            executor = null;
        }
    }

    @Override
    protected final void stopConnectablePool(){
        if(connectablePool != null){
            connectablePool = null;
        }
    }

    /**
     * This method closes the ThreadPoolExecutor safely. It awaits the
     * termination for 10 seconds, after that, an InterruptedException will be
     * thrown and the senders will be closed.
     */
    @Override
    public final void stop() {
        synchronized (NJAMS_INSTANCES) {
            NJAMS_INSTANCES.remove(this);
            if (NJAMS_INSTANCES.isEmpty()) {
                //This ensures that the executor and the connectablepool are only closed if no more instances are using them
                super.stopBeforeConnectablePoolStops();
                super.stopConnectablePool();
                super.stopAfterConnectablePoolStops();
                globalExecutor = null;
                globalPool = null;
            }else{
                super.stop();
            }
        }
    }
}