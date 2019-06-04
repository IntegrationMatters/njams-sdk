/*
 */
package com.im.njams.sdk.communication.connectable.receiver;


import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication.connectable.Connectable;
import com.im.njams.sdk.service.NjamsService;

import java.util.Properties;

/**
 * This interface must be implenmented by a new Receiver for a given
 * communication
 *
 * @author pnientiedt
 */
public interface Receiver extends Connectable, NjamsService {

    /**
     * Set njams instance
     *
     * @param njams instance
     */
    void setNjams(Njams njams);


    /**
     * This function should be called by a implementation of the Receiver class
     * with the previously read instruction
     *
     * @param instruction will be executed for all listeners
     */
    void onInstruction(Instruction instruction);

    /**
     * This method is deprecated. {@link #init(Properties) init(Properties)} should establish the connection.
     */
    @Deprecated
    default void start(){}


}
