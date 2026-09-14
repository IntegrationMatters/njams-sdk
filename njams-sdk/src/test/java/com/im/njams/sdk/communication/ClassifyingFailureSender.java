package com.im.njams.sdk.communication;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;

/** Sender whose sends always fail with a failure of a chosen, self-classified kind. */
public class ClassifyingFailureSender extends AbstractSender {

    /** Which classification the produced failure carries. */
    public enum Kind {
        /** Classified by {@link #isCongestion(Throwable)}. */
        CONGESTION,
        /** Classified by {@link #isMessageRejected(Throwable)}. */
        REJECTED,
        /** Classified by neither, i.e. treated as a real connection problem. */
        OTHER
    }

    private final Kind kind;

    public ClassifyingFailureSender(Kind kind) {
        this.kind = kind;
    }

    @Override
    public String getName() {
        return "classifying-failure";
    }

    @Override
    protected void send(LogMessage msg, String clientSessionId) {
        throw failure();
    }

    @Override
    protected void send(ProjectMessage msg, String clientSessionId) {
        throw failure();
    }

    @Override
    protected void send(TraceMessage msg, String clientSessionId) {
        throw failure();
    }

    private RuntimeException failure() {
        return new IllegalStateException(kind.name());
    }

    @Override
    protected boolean isCongestion(Throwable failure) {
        return kind == Kind.CONGESTION && failure != null && Kind.CONGESTION.name().equals(failure.getMessage());
    }

    @Override
    protected boolean isMessageRejected(Throwable failure) {
        return kind == Kind.REJECTED && failure != null && Kind.REJECTED.name().equals(failure.getMessage());
    }
}
