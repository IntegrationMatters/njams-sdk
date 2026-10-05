package com.im.njams.sdk.it.support;

import java.time.Duration;
import java.time.LocalDateTime;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnectionFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.im.njams.sdk.communication.MessageHeaders;
import com.im.njams.sdk.utils.JsonUtils;

/** Publishes a server command straight to the broker (not through the proxy) and reads the client's reply. */
public final class JmsCommandClient implements AutoCloseable {
    private final Connection connection;
    private final Session session;
    private final Topic commands;

    public JmsCommandClient(DockerEnvironment env, String commandsTopic) throws JMSException {
        connection = new ActiveMQConnectionFactory(env.jmsUrlDirect()).createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        commands = session.createTopic(commandsTopic);
    }

    /** Sends one command; returns the reply, or {@code null} if none arrived within {@code timeout}. */
    public Instruction request(Command command, String receiverPath, String clientId, Duration timeout)
        throws JMSException {
        TemporaryQueue replyTo = session.createTemporaryQueue();
        try (MessageConsumer replies = session.createConsumer(replyTo);
            MessageProducer producer = session.createProducer(commands)) {
            Request request = new Request();
            request.setCommand(command.commandString());
            request.setDateTime(LocalDateTime.now());
            Instruction instruction = new Instruction();
            instruction.setRequest(request);
            TextMessage message = session.createTextMessage(JsonUtils.serialize(instruction));
            message.setStringProperty(MessageHeaders.NJAMS_RECEIVER_HEADER, receiverPath);
            message.setStringProperty(MessageHeaders.NJAMS_CONTENT_HEADER, MessageHeaders.CONTENT_TYPE_JSON);
            if (clientId != null) {
                message.setStringProperty(MessageHeaders.NJAMS_CLIENTID_HEADER, clientId);
            }
            message.setJMSReplyTo(replyTo);
            producer.send(message);
            Message reply = replies.receive(timeout.toMillis());
            return reply instanceof TextMessage
                ? JsonUtils.parse(((TextMessage) reply).getText(), Instruction.class) : null;
        } finally {
            replyTo.delete();
        }
    }

    /**
     * Repeats {@link #request} (the commands topic is non-durable, so a command sent before the receiver's
     * consumer is attached is lost) until a reply arrives or {@code timeout} elapsed.
     *
     * @return the reply, or {@code null} if none arrived in time.
     */
    public Instruction awaitReply(Command command, String receiverPath, String clientId, Duration timeout)
        throws JMSException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Instruction reply = request(command, receiverPath, clientId, Duration.ofSeconds(1));
            if (reply != null) {
                return reply;
            }
            Thread.sleep(500);
        }
        return null;
    }

    @Override
    public void close() throws JMSException {
        connection.close();
    }
}
