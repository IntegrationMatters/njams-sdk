package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.model.Statement;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsMetadata;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.utils.CapturingLogAppender;
import com.im.njams.sdk.utils.ScopedSpiRule;

/**
 * Verifies that {@link CommunicationFactory} tries every provider matching the configured communication type, in
 * order, and that only {@link TransportNotApplicableException} makes it move on to the next one.
 */
public class CommunicationFactoryCandidateTest {

    private static final String CANDIDATE_NAME = "CANDIDATE_TEST";
    private static final int THREADS = 8;

    /** Records which candidates were initialised, in order; shared by all fixtures. */
    private static final List<String> INITIALISED = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger DECLINER_INITS = new AtomicInteger();
    private static volatile long declinerDelayMillis;

    @FunctionalInterface
    private interface Body {
        void run() throws Throwable;
    }

    public static class DecliningSender extends TestSender {
        @Override
        public String getName() {
            return CANDIDATE_NAME;
        }

        @Override
        public void init(ClientSettings settings) {
            DECLINER_INITS.incrementAndGet();
            INITIALISED.add("DecliningSender");
            sleep(declinerDelayMillis);
            throw new TransportNotApplicableException("decliner cannot work here");
        }
    }

    public static class SecondDecliningSender extends TestSender {
        @Override
        public String getName() {
            return CANDIDATE_NAME;
        }

        @Override
        public void init(ClientSettings settings) {
            INITIALISED.add("SecondDecliningSender");
            throw new TransportNotApplicableException("second decliner cannot work here either");
        }
    }

    public static class WorkingSender extends TestSender {
        @Override
        public String getName() {
            return CANDIDATE_NAME;
        }

        @Override
        public void init(ClientSettings settings) {
            INITIALISED.add("WorkingSender");
        }
    }

    public static class OtherWorkingSender extends TestSender {
        @Override
        public String getName() {
            return CANDIDATE_NAME;
        }

        @Override
        public void init(ClientSettings settings) {
            INITIALISED.add("OtherWorkingSender");
        }
    }

    public static class BrokenSender extends TestSender {
        @Override
        public String getName() {
            return CANDIDATE_NAME;
        }

        @Override
        public void init(ClientSettings settings) {
            INITIALISED.add("BrokenSender");
            throw new IllegalArgumentException("broken configuration");
        }
    }

    public static class DecliningReceiver extends TestReceiver {
        @Override
        public String getName() {
            return CANDIDATE_NAME;
        }

        @Override
        public void init(ClientSettings settings) {
            INITIALISED.add("DecliningReceiver");
            throw new TransportNotApplicableException("receiver decliner cannot work here");
        }
    }

    public static class WorkingReceiver extends TestReceiver {
        @Override
        public String getName() {
            return CANDIDATE_NAME;
        }

        @Override
        public void init(ClientSettings settings) {
            INITIALISED.add("WorkingReceiver");
        }
    }

    /** A shareable receiver whose first initialisation fails hard, the following ones succeed. */
    public static class FlakySharedReceiver extends TestReceiver implements ShareableReceiver<Object> {
        static final AtomicInteger INITS = new AtomicInteger();

        @Override
        public String getName() {
            return CANDIDATE_NAME;
        }

        @Override
        public void init(ClientSettings settings) {
            if (INITS.incrementAndGet() == 1) {
                throw new IllegalStateException("first init fails");
            }
        }

        @Override
        public boolean removeNjams(Njams njams) {
            return true;
        }

        @Override
        public Path getReceiverPath(Object requestMessage, Instruction instruction) {
            return null;
        }

        @Override
        public String getClientId(Object requestMessage, Instruction instruction) {
            return null;
        }

        @Override
        public void sendReply(Object requestMessage, Instruction reply, String clientId) {
            // not needed
        }
    }

    private static void sleep(long millis) {
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Njams njams;

    @Before
    public void setUp() {
        INITIALISED.clear();
        DECLINER_INITS.set(0);
        FlakySharedReceiver.INITS.set(0);
        declinerDelayMillis = 0;
        TestSender.setSenderMock(null);
        TestReceiver.setReceiverMock(null);
        CommunicationFactory.clearSharedReceiversForTesting();
        njams = mock(Njams.class);
        NjamsMetadata metadata = mock(NjamsMetadata.class);
        when(njams.metadata()).thenReturn(metadata);
        when(metadata.getClientPath()).thenReturn(Path.of("test"));
    }

    @After
    public void tearDown() {
        CommunicationFactory.clearSharedReceiversForTesting();
    }

    /** Runs the body with exactly the given providers registered, in the given order, in addition to the regular. */
    private static void withProviders(Class<?> serviceType, Class<?>[] providers, Body body) throws Throwable {
        new ScopedSpiRule(serviceType, providers).apply(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                body.run();
            }
        }, null).evaluate();
    }

    private static void withSenders(Body body, Class<?>... providers) throws Throwable {
        withProviders(AbstractSender.class, providers, body);
    }

    private static void withReceivers(Body body, Class<?>... providers) throws Throwable {
        withProviders(AbstractReceiver.class, providers, body);
    }

    private ClientSettings settings(String communicationType) {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, communicationType);
        return settings;
    }

    @Test
    public void theFirstCandidateWins_whenItInitialisesCleanly() throws Throwable {
        withSenders(() -> {
            AbstractSender sender = new CommunicationFactory(settings(CANDIDATE_NAME)).getSender();

            assertEquals(WorkingSender.class, sender.getClass());
            assertEquals(List.of("WorkingSender"), INITIALISED);
        }, WorkingSender.class, OtherWorkingSender.class);
    }

    @Test
    public void theSelectedSenderIsReportedAtInfo_byFullyQualifiedName() throws Throwable {
        withSenders(() -> {
            CapturingLogAppender appender = CapturingLogAppender.attach(CommunicationFactory.class);
            try {
                CommunicationFactory factory = new CommunicationFactory(settings(CANDIDATE_NAME));
                factory.getSender();

                List<LoggingEvent> infos = appender.events(Level.INFO);
                assertEquals(1, infos.size());
                String message = infos.get(0).getRenderedMessage();
                assertTrue(message, message.contains(WorkingSender.class.getName()));
                assertTrue(message, message.contains("sender"));
                assertTrue(message, message.contains(CANDIDATE_NAME));
                // the candidate that declined is a debug-level detail, not part of the success report
                assertTrue(message, !message.contains(DecliningSender.class.getName()));

                // the resolution is memoized, so a second sender must not repeat the report: the pool creates
                // one sender per thread and this would otherwise be logged once per thread
                factory.getSender();
                assertEquals(1, appender.events(Level.INFO).size());
            } finally {
                appender.detach();
            }
        }, DecliningSender.class, WorkingSender.class);
    }

    @Test
    public void theSelectedReceiverIsReportedAtInfo_byFullyQualifiedName() throws Throwable {
        withReceivers(() -> {
            CapturingLogAppender appender = CapturingLogAppender.attach(CommunicationFactory.class);
            try {
                new CommunicationFactory(settings(CANDIDATE_NAME)).getReceiver(njams);

                List<LoggingEvent> infos = appender.events(Level.INFO);
                assertEquals(1, infos.size());
                String message = infos.get(0).getRenderedMessage();
                assertTrue(message, message.contains(WorkingReceiver.class.getName()));
                assertTrue(message, message.contains("receiver"));
                assertTrue(message, message.contains(CANDIDATE_NAME));
            } finally {
                appender.detach();
            }
        }, DecliningReceiver.class, WorkingReceiver.class);
    }

    @Test
    public void aCandidateThrowingTransportNotApplicable_fallsThroughToTheNext() throws Throwable {
        withSenders(() -> {
            AbstractSender sender = new CommunicationFactory(settings(CANDIDATE_NAME)).getSender();

            assertEquals(WorkingSender.class, sender.getClass());
            assertEquals(List.of("DecliningSender", "WorkingSender"), INITIALISED);
        }, DecliningSender.class, WorkingSender.class);
    }

    @Test
    public void aCandidateFailingForAnyOtherReason_isSkipped_andTheNextIsTried() throws Throwable {
        withSenders(() -> {
            AbstractSender sender = new CommunicationFactory(settings(CANDIDATE_NAME)).getSender();

            assertEquals(WorkingSender.class, sender.getClass());
            assertEquals(List.of("BrokenSender", "WorkingSender"), INITIALISED);
        }, BrokenSender.class, WorkingSender.class);
    }

    @Test
    public void whenEveryCandidateDeclines_theErrorNamesEveryCause() throws Throwable {
        withSenders(() -> {
            try {
                new CommunicationFactory(settings(CANDIDATE_NAME)).getSender();
                fail("IllegalStateException expected");
            } catch (IllegalStateException e) {
                String message = e.getMessage();
                assertTrue(message, message.contains(CANDIDATE_NAME));
                assertTrue(message, message.contains("decliner cannot work here"));
                assertTrue(message, message.contains("second decliner cannot work here either"));
                assertEquals("every decline must survive as suppressed", 2, e.getSuppressed().length);
                assertTrue(message, message.contains(DecliningSender.class.getName()));
                assertTrue(message, message.contains(SecondDecliningSender.class.getName()));
            }
            assertEquals(List.of("DecliningSender", "SecondDecliningSender"), INITIALISED);
        }, DecliningSender.class, SecondDecliningSender.class);
    }

    @Test
    public void theResolvedSenderIsMemoized_soASecondGetSenderDoesNotReResolve() throws Throwable {
        withSenders(() -> {
            CommunicationFactory factory = new CommunicationFactory(settings(CANDIDATE_NAME));

            AbstractSender first = factory.getSender();
            AbstractSender second = factory.getSender();

            assertEquals(1, DECLINER_INITS.get());
            assertEquals(WorkingSender.class, second.getClass());
            assertNotSame("every call must return a separately initialised sender", first, second);
            assertEquals(List.of("DecliningSender", "WorkingSender", "WorkingSender"), INITIALISED);
        }, DecliningSender.class, WorkingSender.class);
    }

    @Test
    public void concurrentGetSenderCalls_resolveExactlyOnce() throws Throwable {
        declinerDelayMillis = 100;
        withSenders(() -> {
            CommunicationFactory factory = new CommunicationFactory(settings(CANDIDATE_NAME));
            // the pool threads are created here and so inherit the context class loader with the registration
            ExecutorService executor = Executors.newFixedThreadPool(THREADS);
            try {
                CountDownLatch ready = new CountDownLatch(THREADS);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<AbstractSender>> results = new ArrayList<>();
                for (int i = 0; i < THREADS; i++) {
                    results.add(executor.submit(() -> {
                        ready.countDown();
                        go.await();
                        return factory.getSender();
                    }));
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                go.countDown();

                List<AbstractSender> senders = new ArrayList<>();
                for (Future<AbstractSender> result : results) {
                    senders.add(result.get(30, TimeUnit.SECONDS));
                }
                assertEquals("the candidate loop must run exactly once", 1, DECLINER_INITS.get());
                for (AbstractSender sender : senders) {
                    assertEquals(WorkingSender.class, sender.getClass());
                }
                for (int i = 1; i < senders.size(); i++) {
                    assertNotSame(senders.get(0), senders.get(i));
                }
                assertEquals(THREADS, INITIALISED.stream().filter("WorkingSender"::equals).count());
            } finally {
                executor.shutdownNow();
            }
        }, DecliningSender.class, WorkingSender.class);
    }

    @Test
    public void whenNoProviderMatchesTheName_theErrorIsUnchangedFromToday() throws Throwable {
        withSenders(() -> {
            try {
                new CommunicationFactory(settings("doesNotExist")).getSender();
                fail("IllegalStateException expected");
            } catch (IllegalStateException e) {
                assertEquals("Unable to find sender implementation for communication type 'doesNotExist' configured"
                    + " by setting " + NjamsSettings.PROPERTY_COMMUNICATION + ". See the log for the available and"
                    + " unavailable implementations.", e.getMessage());
            }
            assertTrue(INITIALISED.isEmpty());
        }, DecliningSender.class, WorkingSender.class);
    }

    @Test
    public void aReceiverThrowingTransportNotApplicable_fallsThroughToTheNext() throws Throwable {
        withReceivers(() -> {
            AbstractReceiver receiver = new CommunicationFactory(settings(CANDIDATE_NAME)).getReceiver(njams);

            assertEquals(WorkingReceiver.class, receiver.getClass());
            assertEquals(List.of("DecliningReceiver", "WorkingReceiver"), INITIALISED);
        }, DecliningReceiver.class, WorkingReceiver.class);
    }

    @Test
    public void aSharedReceiverFailingToInitialise_isNotLeftCachedForTheNextLookup() throws Throwable {
        withReceivers(() -> {
            ClientSettings settings = settings(CANDIDATE_NAME);
            settings.put(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, "true");
            CommunicationFactory factory = new CommunicationFactory(settings);

            try {
                factory.getReceiver(njams);
                fail("IllegalStateException expected");
            } catch (IllegalStateException e) {
                // the first initialisation fails by design
            }
            AbstractReceiver receiver = factory.getReceiver(njams);

            assertEquals(2, FlakySharedReceiver.INITS.get());
            assertSame(receiver, factory.getReceiver(njams));
        }, FlakySharedReceiver.class);
    }
}
