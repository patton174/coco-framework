package io.github.coco.feature.audit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.feature.audit.core.CocoAuditEvent;
import io.github.coco.feature.audit.core.CocoAuditPublisher;
import io.github.coco.feature.audit.core.CocoAuditRecorder;
import io.github.coco.feature.lock.CocoLock;
import io.github.coco.feature.lock.CocoLockAutoConfiguration;
import io.github.coco.feature.lock.CocoLockManager;
import io.github.coco.feature.lock.CocoLocked;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CocoAuditedMethodIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoCommonAutoConfiguration.class,
                    CocoAuditAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages",
                    "coco.audit.logging.enabled=false");

    @Test
    void publishesStaticMethodAndTypeDeclarationsWithoutCapturingBusinessData() {
        CapturingPublisher publisher = new CapturingPublisher();
        CocoTraceContext.runWithTraceId(" trace-declarative ", () -> this.contextRunner
                .withUserConfiguration(AuditedServicesConfiguration.class)
                .withBean(CocoAuditPublisher.class, () -> publisher)
                .run(context -> {
                    AuditedService service = context.getBean(AuditedService.class);
                    assertThat(service.method("secret-argument")).isEqualTo("secret-result");
                    assertThat(service.typeOnly()).isEqualTo("type-result");
                    assertThat(service.plain()).isEqualTo("plain-result");
                    assertThat(context.getBean(UnannotatedService.class).run()).isEqualTo("unannotated-result");
                }));

        assertThat(publisher.events).hasSize(3);
        assertThat(publisher.events.get(0)).satisfies(event -> {
            assertThat(event.type()).isEqualTo("method-type");
            assertThat(event.action()).contains("save");
            assertThat(event.resourceType()).contains("order");
            assertThat(event.resourceId()).contains("fixed-7");
            assertThat(event.traceId()).contains("trace-declarative");
            assertThat(event.success()).isTrue();
            assertThat(event.attributes()).containsKey("durationMillis")
                    .doesNotContainKeys("args", "arguments", "result", "returnValue", "exceptionType");
        });
        assertThat(publisher.events.get(1)).satisfies(event -> {
            assertThat(event.type()).isEqualTo("type-type");
            assertThat(event.action()).contains("type-action");
        });
        assertThat(publisher.events.get(2)).extracting(CocoAuditEvent::type).isEqualTo("type-type");
    }

    @Test
    void publishesFailureWithOnlyExceptionClassNameAndPreservesThrowableIdentity() {
        CapturingPublisher publisher = new CapturingPublisher();
        this.contextRunner.withUserConfiguration(AuditedServicesConfiguration.class)
                .withBean(CocoAuditPublisher.class, () -> publisher)
                .run(context -> {
                    AuditedService service = context.getBean(AuditedService.class);
                    CheckedFailure checked = new CheckedFailure("sensitive checked failure");
                    RuntimeException runtime = new IllegalArgumentException("sensitive runtime failure");
                    AssertionError error = new AssertionError("sensitive error failure");

                    assertSameFailure(checked, () -> service.checked(checked));
                    assertSameFailure(runtime, () -> service.runtime(runtime));
                    assertSameFailure(error, () -> service.error(error));
                });

        assertThat(publisher.events).hasSize(3);
        assertThat(publisher.events).allSatisfy(event -> {
            assertThat(event.success()).isFalse();
            assertThat(event.attributes()).containsKey("exceptionType").containsKey("durationMillis")
                    .doesNotContainKeys("args", "arguments", "result", "returnValue", "message", "exception");
        });
        assertThat(publisher.events).extracting(event -> event.attributes().get("exceptionType"))
                .containsExactly(CheckedFailure.class.getName(), IllegalArgumentException.class.getName(),
                        AssertionError.class.getName());
    }

    @Test
    void publisherFailureOnSuccessPropagatesButBusinessFailureRemainsPrimary() {
        IllegalStateException publisherFailure = new IllegalStateException("publisher failure");
        AtomicInteger publicationAttempts = new AtomicInteger();
        List<Boolean> attemptedStates = new CopyOnWriteArrayList<>();
        this.contextRunner.withUserConfiguration(AuditedServicesConfiguration.class)
                .withBean(CocoAuditPublisher.class, () -> event -> {
                    publicationAttempts.incrementAndGet();
                    attemptedStates.add(event.success());
                    throw publisherFailure;
                })
                .run(context -> {
                    AuditedService service = context.getBean(AuditedService.class);
                    assertThatThrownBy(() -> service.method("ignored")).isSameAs(publisherFailure);
                    assertThat(publicationAttempts).hasValue(1);
                    assertThat(attemptedStates).containsExactly(true);

                    RuntimeException businessFailure = new IllegalStateException("business failure");
                    try {
                        service.runtime(businessFailure);
                    }
                    catch (RuntimeException ex) {
                        assertThat(ex).isSameAs(businessFailure);
                        assertThat(ex.getSuppressed()).containsExactly(publisherFailure);
                        assertThat(publicationAttempts).hasValue(2);
                        assertThat(attemptedStates).containsExactly(true, false);
                        return;
                    }
                    throw new AssertionError("expected business failure");
                });
    }

    @Test
    void eventFactoryFailureAfterBusinessSuccessPropagatesWithoutFailurePublication() {
        IllegalStateException factoryFailure = new IllegalStateException("factory failure");
        AtomicInteger factoryCalls = new AtomicInteger();
        AtomicInteger publicationAttempts = new AtomicInteger();
        this.contextRunner.withUserConfiguration(AuditedServicesConfiguration.class)
                .withBean(CocoAuditPublisher.class, () -> event -> publicationAttempts.incrementAndGet())
                .withBean(CocoAuditEventFactory.class, () -> invocation -> {
                    factoryCalls.incrementAndGet();
                    assertThat(invocation.success()).isTrue();
                    throw factoryFailure;
                })
                .run(context -> assertThatThrownBy(() -> context.getBean(AuditedService.class).method("ignored"))
                        .isSameAs(factoryFailure));
        assertThat(factoryCalls).hasValue(1);
        assertThat(publicationAttempts).hasValue(0);
    }

    @Test
    void doesNotSelfSuppressWhenFailurePublisherThrowsBusinessFailure() {
        RuntimeException businessFailure = new IllegalStateException("business failure");
        this.contextRunner.withUserConfiguration(AuditedServicesConfiguration.class)
                .withBean(CocoAuditPublisher.class, () -> event -> {
                    throw businessFailure;
                })
                .run(context -> {
                    assertThatThrownBy(() -> context.getBean(AuditedService.class).runtime(businessFailure))
                            .isSameAs(businessFailure);
                    assertThat(businessFailure.getSuppressed()).isEmpty();
                });
    }

    @Test
    void validatesBlankTypeBeforeCallingBusinessMethod() {
        AtomicInteger invocations = new AtomicInteger();
        this.contextRunner.withUserConfiguration(BlankTypeConfiguration.class)
                .withBean(CocoAuditPublisher.class, CapturingPublisher::new)
                .run(context -> assertThatThrownBy(() -> context.getBean(BlankTypeService.class).run(invocations))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("Coco audit type must not be blank"));
        assertThat(invocations).hasValue(0);
    }

    @Test
    void resolvesInterfaceMethodAnnotationThroughJdkProxyOnce() {
        CapturingPublisher publisher = new CapturingPublisher();
        this.contextRunner.withUserConfiguration(InterfaceServiceConfiguration.class)
                .withBean(CocoAuditPublisher.class, () -> publisher)
                .run(context -> {
                    Contract contract = context.getBean(Contract.class);
                    assertThat(AopUtils.isJdkDynamicProxy(contract)).isTrue();
                    assertThat(contract.interfaceAnnotated()).isEqualTo("interface");
                });
        assertThat(publisher.events).extracting(CocoAuditEvent::type)
                .containsExactly("interface-method");
    }

    @Test
    void resolvesInterfaceMethodAnnotationThroughCglibProxyOnce() {
        CapturingPublisher publisher = new CapturingPublisher();
        this.contextRunner.withUserConfiguration(InterfaceServiceConfiguration.class,
                        ForceCglibProxyConfiguration.class)
                .withBean(CocoAuditPublisher.class, () -> publisher)
                .run(context -> {
                    Contract contract = context.getBean(Contract.class);
                    assertThat(AopUtils.isCglibProxy(contract)).isTrue();
                    assertThat(AopProxyUtils.ultimateTargetClass(contract)).isEqualTo(ContractImpl.class);
                    assertThat(contract.interfaceAnnotated()).isEqualTo("interface");
                });
        assertThat(publisher.events).extracting(CocoAuditEvent::type)
                .containsExactly("interface-method");
    }

    @Test
    void implementationMethodAnnotationOverridesInterfaceAndTypeAnnotations() {
        CapturingPublisher publisher = new CapturingPublisher();
        this.contextRunner.withUserConfiguration(InterfaceServiceConfiguration.class)
                .withBean(CocoAuditPublisher.class, () -> publisher)
                .run(context -> {
                    Contract contract = context.getBean(Contract.class);
                    assertThat(contract.implementationAnnotated()).isEqualTo("implementation");
                });
        assertThat(publisher.events).extracting(CocoAuditEvent::type)
                .containsExactly("implementation-method");
    }

    @Test
    void customFactoryAndAdditionalAdvisorCoexistWithoutDuplicateAuditEvents() {
        CapturingPublisher publisher = new CapturingPublisher();
        AtomicInteger extraAdviceCalls = new AtomicInteger();
        this.contextRunner.withUserConfiguration(AuditedServicesConfiguration.class, ExtraAdvisorConfiguration.class)
                .withBean(CocoAuditPublisher.class, () -> publisher)
                .withBean(CocoAuditEventFactory.class,
                        () -> invocation -> CocoAuditEvent.builder("custom-" + invocation.annotation().type()).build())
                .withBean(AtomicInteger.class, () -> extraAdviceCalls)
                .run(context -> assertThat(context.getBean(AuditedService.class).method("value")).isEqualTo("secret-result"));
        assertThat(publisher.events).singleElement().extracting(CocoAuditEvent::type).isEqualTo("custom- method-type");
        assertThat(extraAdviceCalls).hasValue(1);
    }

    @Test
    void backsOffWhenDisabledPublisherMissingOrAdvisorIsOverridden() {
        this.contextRunner.withUserConfiguration(AuditedServicesConfiguration.class)
                .withBean(CocoAuditPublisher.class, CapturingPublisher::new)
                .withPropertyValues("coco.audit.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("cocoAuditAdvisor");
                    assertThat(context).doesNotHaveBean("cocoAuditAutoProxyCreator");
                });
        this.contextRunner.withUserConfiguration(AuditedServicesConfiguration.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean("cocoAuditAdvisor");
                    assertThat(context).doesNotHaveBean(CocoAuditEventFactory.class);
                });
        this.contextRunner.withUserConfiguration(AuditedServicesConfiguration.class, CustomAdvisorConfiguration.class)
                .withBean(CocoAuditPublisher.class, CapturingPublisher::new)
                .run(context -> assertThat(context.getBean("cocoAuditAdvisor")).isInstanceOf(DefaultPointcutAdvisor.class));
    }

    @Test
    void drainsDeclarativeEventsThroughAsyncPublisher() {
        List<CocoAuditEvent> events = new CopyOnWriteArrayList<>();
        this.contextRunner.withUserConfiguration(AuditedServicesConfiguration.class)
                .withBean(CocoAuditRecorder.class, () -> events::add)
                .withPropertyValues("coco.audit.async.enabled=true", "coco.audit.async.queue-capacity=8")
                .run(context -> assertThat(context.getBean(AuditedService.class).method("value")).isEqualTo("secret-result"));
        assertThat(events).singleElement().extracting(CocoAuditEvent::type).isEqualTo("method-type");
    }

    @Test
    void coexistsWithCocoLockAdvisorUsingOneProxyCreator() {
        CapturingPublisher publisher = new CapturingPublisher();
        AtomicInteger lockCalls = new AtomicInteger();
        this.contextRunner.withUserConfiguration(LockedAuditedServiceConfiguration.class,
                        CocoLockAutoConfiguration.class)
                .withBean(CocoAuditPublisher.class, () -> publisher)
                .withBean(CocoLockManager.class, () -> recordingLockManager(lockCalls))
                .run(context -> {
                    assertThat(context.getBean(LockedAuditedService.class).run()).isEqualTo("locked");
                    assertThat(context).hasBean("cocoAuditAdvisor").hasBean("cocoLockAdvisor");
                    assertThat(context.getBeansOfType(AbstractAdvisorAutoProxyCreator.class)).hasSize(1);
                });
        assertThat(lockCalls).hasValue(1);
        assertThat(publisher.events).singleElement().extracting(CocoAuditEvent::type).isEqualTo("locked-audit");
    }

    private static void assertSameFailure(Throwable expected, ThrowingOperation operation) {
        try {
            operation.run();
        }
        catch (Throwable actual) {
            assertThat(actual).isSameAs(expected);
            return;
        }
        throw new AssertionError("expected failure");
    }

    private static CocoLockManager recordingLockManager(AtomicInteger lockCalls) {
        return new CocoLockManager() {
            @Override
            public java.util.Optional<CocoLock> tryLock(String key, java.time.Duration waitTime,
                    java.time.Duration leaseTime) {
                lockCalls.incrementAndGet();
                return java.util.Optional.of(new CocoLock() {
                    @Override public String key() { return key; }
                    @Override public java.time.Instant acquiredAt() { return java.time.Instant.EPOCH; }
                    @Override public java.time.Instant expiresAt() { return java.time.Instant.EPOCH; }
                    @Override public void close() { }
                });
            }

            @Override public void close() { }
        };
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Throwable;
    }

    @Configuration(proxyBeanMethods = false)
    static class AuditedServicesConfiguration {
        @Bean AuditedService auditedService() { return new AuditedService(); }
        @Bean UnannotatedService unannotatedService() { return new UnannotatedService(); }
    }

    static class UnannotatedService {
        String run() { return "unannotated-result"; }
    }

    @Configuration(proxyBeanMethods = false)
    static class LockedAuditedServiceConfiguration {
        @Bean LockedAuditedService lockedAuditedService() { return new LockedAuditedService(); }
    }

    static class LockedAuditedService {
        @CocoAudited(type = "locked-audit")
        @CocoLocked("audit-lock")
        String run() { return "locked"; }
    }

    @CocoAudited(type = " type-type ", action = " type-action ")
    static class AuditedService {
        @CocoAudited(type = " method-type ", action = " save ", resourceType = " order ", resourceId = " fixed-7 ")
        String method(String ignored) { return "secret-result"; }
        String typeOnly() { return "type-result"; }
        String plain() { return "plain-result"; }
        @CocoAudited(type = "failure")
        void checked(CheckedFailure failure) throws CheckedFailure { throw failure; }
        @CocoAudited(type = "failure")
        void runtime(RuntimeException failure) { throw failure; }
        @CocoAudited(type = "failure")
        void error(AssertionError failure) { throw failure; }
    }

    static final class CheckedFailure extends Exception {
        CheckedFailure(String message) { super(message); }
    }

    @Configuration(proxyBeanMethods = false)
    static class BlankTypeConfiguration {
        @Bean BlankTypeService blankTypeService() { return new BlankTypeService(); }
    }

    static class BlankTypeService {
        @CocoAudited(type = "   ")
        void run(AtomicInteger invocations) { invocations.incrementAndGet(); }
    }

    interface Contract {
        @CocoAudited(type = "interface-method")
        String interfaceAnnotated();
        @CocoAudited(type = "interface-fallback")
        String implementationAnnotated();
    }

    @Configuration(proxyBeanMethods = false)
    static class InterfaceServiceConfiguration {
        @Bean Contract contract() { return new ContractImpl(); }
    }

    @CocoAudited(type = "contract-type")
    static class ContractImpl implements Contract {
        @Override public String interfaceAnnotated() { return "interface"; }
        @Override @CocoAudited(type = "implementation-method")
        public String implementationAnnotated() { return "implementation"; }
    }

    @Configuration(proxyBeanMethods = false)
    static class ForceCglibProxyConfiguration {
        @Bean
        static DefaultAdvisorAutoProxyCreator forcedCglibAutoProxyCreator() {
            DefaultAdvisorAutoProxyCreator autoProxyCreator = new DefaultAdvisorAutoProxyCreator();
            autoProxyCreator.setProxyTargetClass(true);
            return autoProxyCreator;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ExtraAdvisorConfiguration {
        @Bean DefaultPointcutAdvisor extraAdvisor(AtomicInteger extraAdviceCalls) {
            MethodInterceptor advice = invocation -> {
                extraAdviceCalls.incrementAndGet();
                return invocation.proceed();
            };
            return new DefaultPointcutAdvisor(
                    new AnnotationMatchingPointcut(null, CocoAudited.class, true), advice);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomAdvisorConfiguration {
        @Bean(name = "cocoAuditAdvisor") DefaultPointcutAdvisor customAuditAdvisor() {
            return new DefaultPointcutAdvisor((MethodInterceptor) invocation -> invocation.proceed());
        }
    }

    static class CapturingPublisher implements CocoAuditPublisher {
        private final List<CocoAuditEvent> events = new CopyOnWriteArrayList<>();
        @Override public void publish(CocoAuditEvent event) { this.events.add(event); }
    }
}
