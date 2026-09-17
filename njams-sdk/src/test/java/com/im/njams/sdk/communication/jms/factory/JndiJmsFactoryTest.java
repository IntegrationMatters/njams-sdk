package com.im.njams.sdk.communication.jms.factory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;

import org.junit.Test;

import com.im.njams.sdk.settings.ClientSettings;

/**
 * Unit tests for {@link JndiJmsFactory}: name, not-initialized guard, JNDI-less topic/queue
 * delegation and idempotent init.
 */
public class JndiJmsFactoryTest {

    private static ClientSettings emptySettings() {
        return ClientSettings.from(new HashMap<>());
    }

    @Test
    public void nameAndToString() {
        JndiJmsFactory factory = new JndiJmsFactory();
        assertEquals(JndiJmsFactory.NAME, factory.getName());
        assertTrue(factory.toString().contains(JndiJmsFactory.NAME));
    }

    @Test
    public void createConnectionFactoryBeforeInitThrows() {
        try {
            new JndiJmsFactory().createConnectionFactory();
            fail("expected IllegalStateException when not initialized");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    @Test
    public void createTopicWithoutContextDelegatesToSession() throws Exception {
        JndiJmsFactory factory = new JndiJmsFactory();
        Session session = mock(Session.class);
        Topic topic = mock(Topic.class);
        when(session.createTopic("t")).thenReturn(topic);

        // no JNDI context (not initialized) -> falls back to the session
        assertSame(topic, factory.createTopic(session, "t"));
    }

    @Test
    public void createQueueWithoutContextDelegatesToSession() throws Exception {
        JndiJmsFactory factory = new JndiJmsFactory();
        Session session = mock(Session.class);
        Queue queue = mock(Queue.class);
        when(session.createQueue("q")).thenReturn(queue);

        assertSame(queue, factory.createQueue(session, "q"));
    }

    @Test
    public void closeWithoutContextIsNoOp() {
        // must not throw even though it was never initialized
        new JndiJmsFactory().close();
    }

    @Test
    public void initWithoutConnectionFactoryLeavesFactoryUnset() throws Exception {
        JndiJmsFactory factory = new JndiJmsFactory();
        factory.init(emptySettings());
        // a context exists now, but no connection factory was looked up
        try {
            factory.createConnectionFactory();
            fail("expected IllegalStateException; no connection factory configured");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    @Test
    public void initIsIdempotent() throws Exception {
        JndiJmsFactory factory = new JndiJmsFactory();
        factory.init(emptySettings());
        // second call must return early without error
        factory.init(emptySettings());
    }
}
