package com.im.njams.sdk.njams;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.communication.InstructionListener;
import com.im.njams.sdk.communication.instructionListener.PingInstructionListener;
import com.im.njams.sdk.communication.Receiver;
import com.im.njams.sdk.communication.ShareableReceiver;
import com.im.njams.sdk.configuration.ConfigurationInstructionListener;
import com.im.njams.sdk.njams.metadata.NjamsMetadata;
import com.im.njams.sdk.settings.Settings;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NjamsReceiver {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NjamsReceiver.class);

    private final Settings njamsSettings;
    private final NjamsMetadata njamsMetadata;
    private final NjamsFeatures njamsFeatures;
    private final NjamsProjectMessage njamsProjectMessage;
    private final NjamsJobs njamsJobs;
    private final NjamsConfiguration njamsConfiguration;
    private final NjamsInstructionListeners njamsInstructionListeners;

    private Receiver receiver;

    public NjamsReceiver(Settings njamsSettings, NjamsMetadata njamsMetadata, NjamsFeatures njamsFeatures,
        NjamsProjectMessage njamsProjectMessage, NjamsJobs njamsJobs, NjamsConfiguration configuration) {
        this.njamsSettings = njamsSettings;
        this.njamsMetadata = njamsMetadata;
        this.njamsFeatures = njamsFeatures;
        this.njamsProjectMessage = njamsProjectMessage;
        this.njamsJobs = njamsJobs;
        this.njamsConfiguration = configuration;
        this.njamsInstructionListeners = new NjamsInstructionListeners();
    }

    /**
     * Start the receiver, which is used to retrieve instructions
     */
    public void start() {
        try {
            receiver = new CommunicationFactory(njamsSettings).getReceiver(njamsMetadata, this);
            njamsInstructionListeners.add(new PingInstructionListener(njamsMetadata, njamsFeatures));
            njamsInstructionListeners.add(njamsProjectMessage);
            njamsInstructionListeners.add(njamsJobs);
            njamsInstructionListeners.add(new ConfigurationInstructionListener(njamsConfiguration));
            receiver.start();
        } catch (Exception e) {
            LOG.error("Error starting Receiver", e);
            try {
                receiver.stop();
            } catch (Exception ex) {
                LOG.debug("Unable to stop receiver", ex);
            }
            receiver = null;
        }
    }

    public void stop() {
        if (receiver != null) {
            if (receiver instanceof ShareableReceiver) {
                ((ShareableReceiver) receiver).removeReceiver(receiver);
            } else {
                receiver.stop();
            }
        }
        njamsInstructionListeners.removeAll();
    }

    public List<InstructionListener> getInstructionListeners() {
        return njamsInstructionListeners.get();
    }

    public void addInstructionListener(InstructionListener instructionListener) {
        njamsInstructionListeners.add(instructionListener);
    }

    public void removeInstructionListener(InstructionListener listener) {
        njamsInstructionListeners.remove(listener);
    }

    public void distribute(Instruction instruction) {
        for (InstructionListener listener : njamsInstructionListeners.get()) {
            try {
                listener.onInstruction(instruction);
            } catch (Exception e) {
                LOG.error("Error in InstructionListener {}", listener.getClass().getSimpleName(), e);
            }
        }
    }
}
