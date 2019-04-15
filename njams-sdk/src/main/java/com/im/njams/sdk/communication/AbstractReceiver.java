/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication;

import com.im.njams.sdk.communication.connection.Connector;
import com.im.njams.sdk.communication.connection.NjamsConnection;
import com.im.njams.sdk.communication.jms.JmsSenderConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.Njams;

import java.util.Properties;

/**
 * This class should be extended when implementing an new Receiver for a new
 * communication type.
 *
 * @author pnientiedt/krautenberg
 * @version 4.0.6
 */
public abstract class AbstractReceiver implements Receiver {

    //The Logger
    private static final Logger LOG = LoggerFactory.getLogger(AbstractReceiver.class);

    protected Connector connector;

    /**
     * Njams to hold as instructionListener
     */
    protected Njams njams;

    /**
     * Initializes this Sender via the given Properties.
     *
     * @param properties the properties needed to initialize
     */
    @Override
    public final void init(Properties properties){
        connector = initialize(properties);
        connector.start();
    }

    protected abstract Connector initialize(Properties properties);

    /**
     * This constructor sets the njams instance for getting the instruction
     * listeners.
     *
     * @param njams the instance that holds the instructionListeners.
     */
    @Override
    public void setNjams(Njams njams) {
        this.njams = njams;
    }

    /**
     * This function should be called by a implementation of the Receiver class
     * with the previously read instruction.
     *
     * @param instruction the instruction that will be handed to all
     *                    instructionListeners
     */
    @Override
    public void onInstruction(Instruction instruction) {
        if (njams == null) {
            LOG.error("Njams should not be null");
            return;
        }
        if (instruction == null) {
            LOG.error("Instruction should not be null");
            return;
        }
        if (instruction.getRequest() == null || instruction.getRequest().getCommand() == null) {
            LOG.error("Instruction should have a valid request with a command");
            Response response = new Response();
            response.setResultCode(1);
            response.setResultMessage("Instruction should have a valid request with a command");
            instruction.setResponse(response);
            return;
        }
        //Extend your request here. If something doesn't work as expected,
        //you can return a response that will be sent back to the server without further processing.
        Response exceptionResponse = this.extendRequest(instruction.getRequest());
        if (exceptionResponse != null) {
            //Set the exception response
            instruction.setResponse(exceptionResponse);
        } else {
            for (InstructionListener listener : njams.getInstructionListeners()) {
                try {
                    listener.onInstruction(instruction);
                } catch (Exception e) {
                    LOG.error("Error in InstructionListener {}", listener.getClass().getSimpleName(), e);
                }
            }
            //If response is empty, no InstructionListener found. Set default Response indicating this.
            if (instruction.getResponse() == null) {
                LOG.warn("No InstructionListener for {} found", instruction.getRequest().getCommand());
                Response response = new Response();
                response.setResultCode(1);
                response.setResultMessage("No InstructionListener for " + instruction.getRequest().getCommand() + " found");
                instruction.setResponse(response);
            }
        }
    }

    /**
     * This method is for extending the incoming request if it is needed for the
     * concrete receiver.
     *
     * @param request request to extend
     * @return A response that will be sent back without further processing
     * of the request. If null is returned (as default), the request
     * has been extended successfully and can be processed normally.
     */
    protected Response extendRequest(Request request) {
        //Doesn't extend the request as default.
        //This can be used by the subclasses to alter the request.
        return null;
    }

    public final void stop(){
        connector.close();
        extStop();
    }

    protected abstract void extStop();
}
