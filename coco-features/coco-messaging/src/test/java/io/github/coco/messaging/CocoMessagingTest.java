package io.github.coco.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.messaging.internal.LocalCocoMessageTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.aop.framework.ProxyFactory;

class CocoMessagingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoMessagingAutoConfiguration.class));

    @AfterEach
    void clearTraceContext() {
        CocoTraceContext.clear();
    }

    @Test
    void envelopeDefensivelyCopiesAndFreezesHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("source", "test");
        CocoMessageEnvelope envelope = new CocoMessageEnvelope("message-1", "order.created", java.time.Instant.now(),
                "trace-1", headers, "payload");

        headers.put("changed", "later");

        assertEquals(Map.of("source", "test"), envelope.headers());
        assertThrows(UnsupportedOperationException.class, () -> envelope.headers().put("x", "y"));
    }

    @Test
    void publishesToAnnotatedListenersInStableOrder() {
        this.contextRunner.withUserConfiguration(OrderedListeners.class).run(context -> {
            List<String> received = context.getBean(ReceivedMessages.class).values;

            context.getBean(CocoMessagePublisher.class).publish("order.created", "A");

            assertEquals(List.of("first:A", "second:A", "third:A"), received);
        });
    }

    @Test
    void rejectsInvalidAnnotatedListenerSignatureAtStartup() {
        this.contextRunner.withUserConfiguration(InvalidListener.class).run(context -> {
            assertTrue(context.getStartupFailure() != null);
            assertInstanceOf(CocoMessagingException.class, context.getStartupFailure());
        });
    }

    @Test
    void appliesNoSubscriberAndHandlerFailurePolicies() {
        CocoMessagingProperties noSubscriberProperties = new CocoMessagingProperties();
        noSubscriberProperties.setNoSubscriberPolicy(CocoMessageNoSubscriberPolicy.FAIL);
        LocalCocoMessageTransport noSubscriberTransport = new LocalCocoMessageTransport(noSubscriberProperties);
        assertThrows(CocoMessagingException.class, () -> noSubscriberTransport.publish(CocoMessageEnvelope.create("missing", null)));

        CocoMessagingProperties failFastProperties = new CocoMessagingProperties();
        LocalCocoMessageTransport failFastTransport = new LocalCocoMessageTransport(failFastProperties);
        List<String> failFastReceived = new ArrayList<>();
        failFastTransport.subscribe("failure", handler("failure", envelope -> {
            throw new IllegalStateException("expected");
        }));
        failFastTransport.subscribe("failure", handler("failure", envelope -> failFastReceived.add("later")));
        assertThrows(IllegalStateException.class, () -> failFastTransport.publish(CocoMessageEnvelope.create("failure", null)));
        assertTrue(failFastReceived.isEmpty());

        CocoMessagingProperties continueProperties = new CocoMessagingProperties();
        continueProperties.setFailurePolicy(CocoMessageFailurePolicy.LOG_AND_CONTINUE);
        LocalCocoMessageTransport continueTransport = new LocalCocoMessageTransport(continueProperties);
        List<String> continueReceived = new ArrayList<>();
        continueTransport.subscribe("failure", handler("failure", envelope -> {
            throw new IllegalStateException("expected");
        }));
        continueTransport.subscribe("failure", handler("failure", envelope -> continueReceived.add("later")));
        continueTransport.publish(CocoMessageEnvelope.create("failure", null));
        assertEquals(List.of("later"), continueReceived);
    }

    @Test
    void rejectsWhenAsyncQueueIsFullAndRejectsPublishingAfterClose() throws Exception {
        CocoMessagingProperties properties = asyncProperties(1, CocoMessageAsyncShutdownPolicy.DISCARD);
        LocalCocoMessageTransport transport = new LocalCocoMessageTransport(properties);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        transport.subscribe("async", handler("async", envelope -> {
            started.countDown();
            await(release);
        }));

        transport.publish(CocoMessageEnvelope.create("async", "one"));
        assertTrue(started.await(2, TimeUnit.SECONDS));
        transport.publish(CocoMessageEnvelope.create("async", "two"));

        assertThrows(CocoMessagingException.class,
                () -> transport.publish(CocoMessageEnvelope.create("async", "three")));
        transport.close();
        release.countDown();
        assertThrows(CocoMessagingException.class, () -> transport.publish(CocoMessageEnvelope.create("async", "four")));
    }

    @Test
    void drainsAcceptedAsyncMessagesDuringClose() throws Exception {
        CocoMessagingProperties properties = asyncProperties(2, CocoMessageAsyncShutdownPolicy.DRAIN);
        LocalCocoMessageTransport transport = new LocalCocoMessageTransport(properties);
        CountDownLatch received = new CountDownLatch(2);
        transport.subscribe("drain", handler("drain", envelope -> received.countDown()));

        transport.publish(CocoMessageEnvelope.create("drain", "one"));
        transport.publish(CocoMessageEnvelope.create("drain", "two"));
        transport.close();

        assertEquals(0, received.getCount());
        assertThrows(CocoMessagingException.class, () -> transport.publish(CocoMessageEnvelope.create("drain", "three")));
    }

    @Test
    void reportsDrainTimeoutWithoutDiscardingAcceptedMessages() throws Exception {
        CocoMessagingProperties properties = asyncProperties(2, CocoMessageAsyncShutdownPolicy.DRAIN);
        properties.getAsync().setShutdownAwait(Duration.ofMillis(50));
        LocalCocoMessageTransport transport = new LocalCocoMessageTransport(properties);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch delivered = new CountDownLatch(2);
        transport.subscribe("drain.timeout", handler("drain.timeout", envelope -> {
            started.countDown();
            await(release);
            delivered.countDown();
        }));

        transport.publish(CocoMessageEnvelope.create("drain.timeout", "one"));
        assertTrue(started.await(2, TimeUnit.SECONDS));
        transport.publish(CocoMessageEnvelope.create("drain.timeout", "two"));

        CocoMessagingException exception = assertThrows(CocoMessagingException.class, transport::close);
        assertEquals("coco.messaging.error.drain-timeout", exception.messageCode());
        release.countDown();
        assertTrue(delivered.await(2, TimeUnit.SECONDS));
    }

    @Test
    void linearizesPublishAndCloseWithoutSilentlyDroppingAcceptedMessages() throws Exception {
        LocalCocoMessageTransport transport = new LocalCocoMessageTransport(asyncProperties(4,
                CocoMessageAsyncShutdownPolicy.DRAIN));
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<Throwable> publishFailure = new AtomicReference<>();
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        transport.subscribe("close.race", handler("close.race", envelope -> delivered.countDown()));
        Thread publisher = new Thread(() -> {
            await(start);
            try {
                transport.publish(CocoMessageEnvelope.create("close.race", "payload"));
            }
            catch (Throwable exception) {
                publishFailure.set(exception);
            }
            finally {
                done.countDown();
            }
        });
        Thread closer = new Thread(() -> {
            await(start);
            try {
                transport.close();
            }
            catch (Throwable exception) {
                closeFailure.set(exception);
            }
            finally {
                done.countDown();
            }
        });

        publisher.start();
        closer.start();
        start.countDown();

        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertTrue(closeFailure.get() == null);
        if (publishFailure.get() == null) {
            assertTrue(delivered.await(2, TimeUnit.SECONDS));
        }
        else {
            assertInstanceOf(CocoMessagingException.class, publishFailure.get());
        }
        assertThrows(CocoMessagingException.class, () -> transport.publish(CocoMessageEnvelope.create("close.race", "later")));
    }

    @Test
    void restoresEnvelopeTraceContextAroundEachHandler() {
        CocoMessagingProperties properties = new CocoMessagingProperties();
        LocalCocoMessageTransport transport = new LocalCocoMessageTransport(properties);
        AtomicReference<String> observedTrace = new AtomicReference<>();
        transport.subscribe("trace", handler("trace", envelope -> {
            observedTrace.set(CocoTraceContext.currentTraceId().orElse(null));
            CocoTraceContext.setTraceId("handler-trace");
        }));
        CocoTraceContext.setTraceId("publisher-trace");

        CocoMessageEnvelope envelope = CocoMessageEnvelope.create("trace", "payload");
        CocoTraceContext.setTraceId("caller-changed");
        transport.publish(envelope);

        assertEquals("publisher-trace", observedTrace.get());
        assertEquals("caller-changed", CocoTraceContext.currentTraceId().orElseThrow());
    }

    @Test
    void invokesTargetDiscoveredListenerThroughJdkDynamicProxy() {
        this.contextRunner.withUserConfiguration(JdkProxyListenerConfiguration.class).run(context -> {
            ReceivedMessages received = context.getBean(ReceivedMessages.class);

            context.getBean(CocoMessagePublisher.class).publish("proxy.listener", "payload");

            assertEquals(List.of("proxy:payload"), received.values);
        });
    }

    @Test
    void invokesGenericJdkProxyListenerThroughErasedInterfaceMethodAndKeepsPayloadType() {
        this.contextRunner.withUserConfiguration(GenericJdkProxyListenerConfiguration.class).run(context -> {
            ReceivedMessages received = context.getBean(ReceivedMessages.class);
            CocoMessagePublisher publisher = context.getBean(CocoMessagePublisher.class);

            publisher.publish("generic.proxy.listener", "payload");

            assertEquals(List.of("generic-proxy:payload"), received.values);
            CocoMessagingException exception = assertThrows(CocoMessagingException.class,
                    () -> publisher.publish("generic.proxy.listener", 42));
            assertEquals("coco.messaging.error.payload-type-mismatch", exception.messageCode());
        });
    }

    @Test
    void failsClosedWhenGenericProxyMethodResolutionIsAmbiguous() {
        this.contextRunner.withUserConfiguration(AmbiguousProxyListenerConfiguration.class).run(context -> {
            assertInstanceOf(CocoMessagingException.class, context.getStartupFailure());
            CocoMessagingException exception = (CocoMessagingException) context.getStartupFailure();
            assertEquals("coco.messaging.error.listener-proxy-method-ambiguous", exception.messageCode());
        });
    }

    @Test
    void registersGenericInterfaceAndImplementationListenerOnlyOnce() {
        this.contextRunner.withUserConfiguration(GenericListenerConfiguration.class).run(context -> {
            GenericReceivedMessages received = context.getBean(GenericReceivedMessages.class);

            context.getBean(CocoMessagePublisher.class).publish("generic.listener", "payload");

            assertEquals(List.of("payload"), received.values);
        });
    }

    @Test
    void clearsTraceBeforeReusedAsyncWorkerDeliversMessageWithoutTrace() throws Exception {
        LocalCocoMessageTransport transport = new LocalCocoMessageTransport(asyncProperties(2,
                CocoMessageAsyncShutdownPolicy.DRAIN));
        List<String> observed = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch delivered = new CountDownLatch(2);
        transport.subscribe("async.trace", handler("async.trace", envelope -> {
            observed.add(CocoTraceContext.currentTraceId().orElse(null));
            delivered.countDown();
        }));
        CocoTraceContext.setTraceId("first-trace");
        CocoMessageEnvelope first = CocoMessageEnvelope.create("async.trace", "first");
        CocoTraceContext.clear();
        CocoMessageEnvelope second = CocoMessageEnvelope.create("async.trace", "second");

        transport.publish(first);
        transport.publish(second);

        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        assertEquals(Arrays.asList("first-trace", null), observed);
        transport.close();
    }

    @Test
    void usesHandlerBeansAndKeepsCurrentDeliverySnapshotStable() {
        this.contextRunner.withUserConfiguration(HandlerBeanConfiguration.class).run(context -> {
            HandlerBean handler = context.getBean(HandlerBean.class);

            context.getBean(CocoMessagePublisher.class).publish("handler.bean", "payload");

            assertEquals(List.of("payload"), handler.received);
        });

        CocoMessagingProperties properties = new CocoMessagingProperties();
        LocalCocoMessageTransport transport = new LocalCocoMessageTransport(properties);
        List<String> received = new ArrayList<>();
        AtomicReference<CocoMessageSubscription> laterSubscription = new AtomicReference<>();
        transport.subscribe("snapshot", handler("snapshot", envelope -> {
            received.add("first");
            laterSubscription.compareAndSet(null,
                    transport.subscribe("snapshot", handler("snapshot", later -> received.add("later"))));
        }));

        transport.publish(CocoMessageEnvelope.create("snapshot", null));
        transport.publish(CocoMessageEnvelope.create("snapshot", null));

        assertEquals(List.of("first", "first", "later"), received);
        laterSubscription.get().close();
    }

    @Test
    void usesCustomTransportAndCanBeDisabled() {
        CapturingTransport transport = new CapturingTransport();
        this.contextRunner.withBean(CocoMessageTransport.class, () -> transport).run(context -> {
            assertEquals(transport, context.getBean(CocoMessageTransport.class));
            context.getBean(CocoMessagePublisher.class).publish("custom.transport", "payload");
            assertEquals("custom.transport", transport.published.get().topic());
        });

        this.contextRunner.withPropertyValues("coco.messaging.enabled=false").run(context -> {
            assertFalse(context.containsBean("cocoMessageTransport"));
            assertFalse(context.containsBean("cocoMessagePublisher"));
        });
    }

    private static CocoMessagingProperties asyncProperties(int capacity, CocoMessageAsyncShutdownPolicy shutdownPolicy) {
        CocoMessagingProperties properties = new CocoMessagingProperties();
        properties.setDeliveryMode(CocoMessageDeliveryMode.ASYNC);
        properties.getAsync().setQueueCapacity(capacity);
        properties.getAsync().setShutdownAwait(Duration.ofSeconds(2));
        properties.getAsync().setShutdownPolicy(shutdownPolicy);
        return properties;
    }

    private static CocoMessageHandler handler(String topic, MessageConsumer consumer) {
        return new CocoMessageHandler() {
            @Override
            public String topic() {
                return topic;
            }

            @Override
            public void handle(CocoMessageEnvelope envelope) {
                consumer.accept(envelope);
            }
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface MessageConsumer {
        void accept(CocoMessageEnvelope envelope);
    }

    @Configuration(proxyBeanMethods = false)
    static class OrderedListeners {

        @Bean
        ReceivedMessages receivedMessages() {
            return new ReceivedMessages();
        }

        @Bean
        FirstListener firstListener(ReceivedMessages received) {
            return new FirstListener(received);
        }

        @Bean
        SecondListener secondListener(ReceivedMessages received) {
            return new SecondListener(received);
        }

        @Bean
        ThirdListener thirdListener(ReceivedMessages received) {
            return new ThirdListener(received);
        }
    }

    static class ReceivedMessages {
        private final List<String> values = new ArrayList<>();
    }

    static class FirstListener {
        private final ReceivedMessages received;

        FirstListener(ReceivedMessages received) {
            this.received = received;
        }

        @CocoMessageListener(topic = "order.created", order = -10)
        public void receive(String payload) {
            this.received.values.add("first:" + payload);
        }
    }

    static class SecondListener {
        private final ReceivedMessages received;

        SecondListener(ReceivedMessages received) {
            this.received = received;
        }

        @CocoMessageListener(topic = "order.created")
        public void receive(CocoMessageEnvelope envelope) {
            this.received.values.add("second:" + envelope.payload());
        }
    }

    static class ThirdListener {
        private final ReceivedMessages received;

        ThirdListener(ReceivedMessages received) {
            this.received = received;
        }

        @CocoMessageListener(topic = "order.created", order = 10)
        public void receive(String payload) {
            this.received.values.add("third:" + payload);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InvalidListener {
        @Bean
        InvalidSignatureListener invalidSignatureListener() {
            return new InvalidSignatureListener();
        }
    }

    static class InvalidSignatureListener {
        @CocoMessageListener(topic = "invalid")
        public String invalid(String payload) {
            return payload;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class JdkProxyListenerConfiguration {
        @Bean
        ReceivedMessages receivedMessages() {
            return new ReceivedMessages();
        }

        @Bean
        JdkProxyListener jdkProxyListener(ReceivedMessages received) {
            ProxyFactory factory = new ProxyFactory();
            factory.setTarget(new JdkProxyListenerTarget(received));
            factory.setInterfaces(JdkProxyListener.class);
            return (JdkProxyListener) factory.getProxy();
        }
    }

    public interface JdkProxyListener {
        void receive(String payload);
    }

    public static class JdkProxyListenerTarget implements JdkProxyListener {
        private final ReceivedMessages received;

        public JdkProxyListenerTarget(ReceivedMessages received) {
            this.received = received;
        }

        @Override
        @CocoMessageListener(topic = "proxy.listener")
        public void receive(String payload) {
            this.received.values.add("proxy:" + payload);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class GenericJdkProxyListenerConfiguration {
        @Bean
        ReceivedMessages receivedMessages() {
            return new ReceivedMessages();
        }

        @Bean
        GenericJdkProxyListener<?> genericJdkProxyListener(ReceivedMessages received) {
            ProxyFactory factory = new ProxyFactory();
            factory.setTarget(new GenericJdkProxyListenerTarget(received));
            factory.setInterfaces(GenericJdkProxyListener.class);
            return (GenericJdkProxyListener<?>) factory.getProxy();
        }
    }

    public interface GenericJdkProxyListener<T> {
        void receive(T payload);
    }

    public static class GenericJdkProxyListenerTarget implements GenericJdkProxyListener<String> {
        private final ReceivedMessages received;

        public GenericJdkProxyListenerTarget(ReceivedMessages received) {
            this.received = received;
        }

        @Override
        @CocoMessageListener(topic = "generic.proxy.listener")
        public void receive(String payload) {
            this.received.values.add("generic-proxy:" + payload);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AmbiguousProxyListenerConfiguration {
        @Bean
        Object ambiguousProxyListener() {
            ProxyFactory factory = new ProxyFactory();
            factory.setTarget(new AmbiguousProxyListenerTarget());
            factory.setInterfaces(CharSequenceProxyListener.class, SerializableProxyListener.class);
            return factory.getProxy();
        }
    }

    public interface CharSequenceProxyListener {
        void receive(CharSequence payload);
    }

    public interface SerializableProxyListener {
        void receive(Serializable payload);
    }

    public static class AmbiguousProxyListenerTarget {
        @CocoMessageListener(topic = "ambiguous.proxy.listener")
        public void receive(String payload) {
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class GenericListenerConfiguration {
        @Bean
        GenericReceivedMessages genericReceivedMessages() {
            return new GenericReceivedMessages();
        }

        @Bean
        GenericStringListener genericStringListener(GenericReceivedMessages received) {
            return new GenericStringListener(received);
        }
    }

    interface GenericListener<T> {
        @CocoMessageListener(topic = "generic.listener")
        void receive(T payload);
    }

    static class GenericStringListener implements GenericListener<String> {
        private final GenericReceivedMessages received;

        GenericStringListener(GenericReceivedMessages received) {
            this.received = received;
        }

        @Override
        @CocoMessageListener(topic = "generic.listener")
        public void receive(String payload) {
            this.received.values.add(payload);
        }
    }

    static class GenericReceivedMessages {
        private final List<String> values = new ArrayList<>();
    }

    @Configuration(proxyBeanMethods = false)
    static class HandlerBeanConfiguration {
        @Bean
        HandlerBean handlerBean() {
            return new HandlerBean();
        }
    }

    static class HandlerBean implements CocoMessageHandler {
        private final List<String> received = new ArrayList<>();

        @Override
        public String topic() {
            return "handler.bean";
        }

        @Override
        public void handle(CocoMessageEnvelope envelope) {
            this.received.add((String) envelope.payload());
        }
    }

    static class CapturingTransport implements CocoMessageTransport {
        private final AtomicReference<CocoMessageEnvelope> published = new AtomicReference<>();

        @Override
        public void publish(CocoMessageEnvelope envelope) {
            this.published.set(envelope);
        }

        @Override
        public CocoMessageSubscription subscribe(String topic, CocoMessageHandler handler) {
            return () -> {
            };
        }

        @Override
        public void close() {
        }
    }
}
