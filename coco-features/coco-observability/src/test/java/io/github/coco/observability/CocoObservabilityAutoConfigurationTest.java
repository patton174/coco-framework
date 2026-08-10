package io.github.coco.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.audit.core.CocoAuditEvent;
import io.github.coco.feature.audit.core.CocoAuditRecorder;
import io.github.coco.feature.model.CocoFeatureSelection;
import io.github.coco.feature.model.CocoFeaturePlan;
import io.github.coco.feature.model.StandardCocoFeatures;
import io.github.coco.common.logging.autoconfigure.CocoCommonLoggingAutoConfiguration;
import io.github.coco.logging.core.AsyncCocoLogSink;
import io.github.coco.logging.core.CocoAsyncLogDropListener;
import io.github.coco.logging.core.CocoLogHandle;
import io.github.coco.logging.core.CocoLogLevel;
import io.github.coco.logging.core.CocoLogRecord;
import io.github.coco.logging.core.CocoLogSink;
import io.github.coco.observability.logging.CocoObservabilityAsyncLogDropListener;
import io.github.coco.observability.actuator.CocoActuatorHealthEndpoint;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class CocoObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoObservabilityAutoConfiguration.class))
            .withUserConfiguration(MeterRegistryConfiguration.class);

    private final ApplicationContextRunner standardLoggingContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoObservabilityAutoConfiguration.class,
                    CocoObservabilityLoggingAutoConfiguration.class, CocoCommonLoggingAutoConfiguration.class))
            .withUserConfiguration(MeterRegistryConfiguration.class);

    @Test
    void recordsSafeBoundedSignalsThroughRealMeterRegistry() {
        this.contextRunner.run(context -> {
            SimpleMeterRegistry meterRegistry = context.getBean(SimpleMeterRegistry.class);
            CocoAuditRecorder auditRecorder = context.getBean("cocoObservabilityAuditRecorder", CocoAuditRecorder.class);
            auditRecorder.record(CocoAuditEvent.builder("user-password-reset")
                    .actor("user-9842")
                    .tenantId("tenant-secret")
                    .resourceId("/accounts/9842/reset?nonce=secret")
                    .attribute("key", "secret")
                    .success(false)
                    .build());
            context.getBean(CocoReplayObservation.class).record(CocoObservationOutcome.DUPLICATE);
            context.getBean(CocoRateLimitObservation.class).record(CocoObservationOutcome.REJECTED);
            context.getBean(CocoLogOverflowObservation.class).recordDrop();

            assertThat(meterRegistry.get("coco.audit.events").tag("outcome", "failure").counter().count()).isEqualTo(1.0);
            assertThat(meterRegistry.get("coco.replay.reservations").tag("outcome", "duplicate").counter().count())
                    .isEqualTo(1.0);
            assertThat(meterRegistry.get("coco.rate_limit.decisions").tag("outcome", "rejected").counter().count())
                    .isEqualTo(1.0);
            assertThat(meterRegistry.get("coco.logging.dropped").tag("outcome", "dropped").counter().count())
                    .isEqualTo(1.0);

            List<Meter.Id> meterIds = meterRegistry.getMeters().stream().map(Meter::getId).toList();
            assertThat(meterIds).allSatisfy(id -> assertThat(id.getTags()).extracting(tag -> tag.getKey())
                    .containsOnly("outcome"));
            assertThat(meterIds).allSatisfy(id -> assertThat(id.getTags()).extracting(tag -> tag.getValue())
                    .doesNotContain("tenant-secret", "user-9842", "secret", "request-9842"));
        });
    }

    @Test
    void collapsesHighCardinalityAndSensitiveAuditInputsIntoBoundedMeters() {
        this.contextRunner.run(context -> {
            SimpleMeterRegistry meterRegistry = context.getBean(SimpleMeterRegistry.class);
            CocoAuditRecorder auditRecorder = context.getBean("cocoObservabilityAuditRecorder", CocoAuditRecorder.class);
            for (int index = 0; index < 128; index++) {
                auditRecorder.record(CocoAuditEvent.builder("operation-" + index)
                        .actor("user-" + index)
                        .tenantId("tenant-" + index)
                        .resourceId("/customers/" + index + "?nonce=nonce-" + index)
                        .traceId("trace-" + index)
                        .attribute("authorization", "Bearer key-" + index)
                        .success(index % 2 == 0)
                        .build());
            }

            List<Meter.Id> auditMeters = meterRegistry.find("coco.audit.events").meters().stream()
                    .map(Meter::getId)
                    .toList();
            assertThat(auditMeters).hasSize(2);
            assertThat(auditMeters).allSatisfy(id -> assertThat(id.getTags()).extracting(tag -> tag.getKey())
                    .containsOnly("outcome"));
            assertThat(auditMeters).extracting(id -> id.getTag("outcome")).containsExactlyInAnyOrder("success", "failure");
            assertThat(auditMeters).allSatisfy(id -> assertThat(id.getTags()).extracting(tag -> tag.getValue())
                    .doesNotContain("user-0", "tenant-0", "nonce-0", "trace-0", "Bearer key-0"));
        });
    }

    @Test
    void exposesSafeStartupAndFeaturePlanStatusThroughActuatorTypes() {
        this.contextRunner.withUserConfiguration(FeaturePlanConfiguration.class).run(context -> {
            CocoActuatorHealthEndpoint healthEndpoint = context.getBean("cocoObservabilityHealthEndpoint",
                    CocoActuatorHealthEndpoint.class);
            assertThat(healthEndpoint.health()).containsEntry("status", "UP").containsKeys("startup", "featurePlan");
            assertThat(healthEndpoint.health().get("featurePlan"))
                    .isEqualTo(Map.of(
                            "status", "available",
                            "enabledCount", 4,
                            "disabledCount", 4,
                            "disabledByDependencyCount", 3));

            Info.Builder builder = new Info.Builder();
            context.getBean("cocoObservabilityInfoContributor", InfoContributor.class).contribute(builder);
            assertThat(builder.build().getDetails()).containsKey("coco");
        });
    }

    @Test
    void permitsEndpointReplacementAndDisablesMetricBinders() {
        this.contextRunner.withPropertyValues("coco.observability.metrics.enabled=false")
                .withUserConfiguration(CustomHealthConfiguration.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CocoObservationRecorder.class);
                    assertThat(context).doesNotHaveBean(CocoReplayObservation.class);
                    assertThat(context).doesNotHaveBean(CocoRateLimitObservation.class);
                    assertThat(context).doesNotHaveBean(CocoLogOverflowObservation.class);
                    CocoActuatorHealthEndpoint healthEndpoint = context.getBean("cocoObservabilityHealthEndpoint",
                            CocoActuatorHealthEndpoint.class);
                    assertThat(healthEndpoint.health()).containsEntry("replacement", true);
                });
    }

    @Test
    void hasNoAutoConfigurationEffectWithoutMicrometer() {
        this.contextRunner.withClassLoader(new FilteredClassLoader("io.micrometer.core.instrument"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CocoObservationRecorder.class);
                    assertThat(context).doesNotHaveBean(CocoObservabilityStatusProvider.class);
                });
    }

    @Test
    void keepsMetricsAvailableWithoutActuatorButDoesNotCreateEndpointBeans() {
        this.contextRunner.withClassLoader(new FilteredClassLoader("org.springframework.boot.actuate"))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(CocoObservationRecorder.class);
                    assertThat(context.getBeanFactory().containsBean("cocoObservabilityHealthEndpoint")).isFalse();
                    assertThat(context.getBeanFactory().containsBean("cocoObservabilityInfoContributor")).isFalse();
                });
    }

    @Test
    void disablesActuatorEndpointExposureWhenBothEndpointFlagsAreFalse() {
        this.contextRunner.withPropertyValues(
                "coco.observability.health.enabled=false",
                "coco.observability.info.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoObservationRecorder.class);
                    assertThat(context.getBeanFactory().containsBean("cocoObservabilityHealthEndpoint")).isFalse();
                    assertThat(context.getBeanFactory().containsBean("cocoObservabilityInfoContributor")).isFalse();
                });
    }

    @Test
    void backsOffForCustomObservationRecorderAndLogDropListener() {
        CustomObservationConfiguration.recordCount.set(0);
        CustomLogListenerConfiguration.dropCount.set(0);
        this.contextRunner.withUserConfiguration(CustomObservationConfiguration.class, CustomLogListenerConfiguration.class)
                .run(context -> {
                    assertThat(context.getBean(CocoObservationRecorder.class))
                            .isInstanceOf(CustomObservationConfiguration.RecordingObservationRecorder.class);
                    context.getBean(CocoReplayObservation.class).record(CocoObservationOutcome.ACCEPTED);
                    context.getBean(CocoAsyncLogDropListener.class).onDropped(CocoLogLevel.INFO, "ignored-handle", 1L);

                    assertThat(CustomObservationConfiguration.recordCount).hasValue(1);
                    assertThat(CustomLogListenerConfiguration.dropCount).hasValue(1);
                    assertThat(context.getBean(SimpleMeterRegistry.class).find("coco.replay.reservations").meters())
                            .isEmpty();
                });
    }

    @Test
    void disablesOnlyAuditBinderWithoutDisablingOtherObservationSpis() {
        this.contextRunner.withPropertyValues("coco.observability.metrics.audit-enabled=false")
                .run(context -> {
                    assertThat(context.getBeanFactory().containsBean("cocoObservabilityAuditRecorder")).isFalse();
                    assertThat(context).hasSingleBean(CocoReplayObservation.class)
                            .hasSingleBean(CocoRateLimitObservation.class)
                            .hasSingleBean(CocoLogOverflowObservation.class);
                });
    }

    @Test
    void keepsLegacyLogDropListenerConstructorLinkableAndBehaviorCompatible() throws Throwable {
        AtomicInteger dropCount = new AtomicInteger();
        MethodHandle constructor = MethodHandles.publicLookup().findConstructor(
                CocoObservabilityAsyncLogDropListener.class,
                MethodType.methodType(void.class, CocoLogOverflowObservation.class));
        CocoObservabilityAsyncLogDropListener listener = (CocoObservabilityAsyncLogDropListener) constructor.invokeExact(
                (CocoLogOverflowObservation) dropCount::incrementAndGet);

        listener.onDropped(CocoLogLevel.INFO, "legacy", 1L);

        assertThat(dropCount).hasValue(1);
    }

    @Test
    void composesWithStandardLoggingAutoConfigurationAndCountsEachRealDropOnce() throws Exception {
        this.standardLoggingContextRunner.run(context -> {
            assertThat(context).hasSingleBean(CocoAsyncLogDropListener.class);
            CocoAsyncLogDropListener listener = context.getBean(CocoAsyncLogDropListener.class);
            assertThat(listener).isInstanceOf(CocoObservabilityAsyncLogDropListener.class);

            CountDownLatch delegateStarted = new CountDownLatch(1);
            CountDownLatch releaseDelegate = new CountDownLatch(1);
            CocoLogSink blockingDelegate = record -> {
                delegateStarted.countDown();
                try {
                    releaseDelegate.await();
                }
                catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            };
            CocoLogRecord record = new CocoLogRecord(CocoLogHandle.of("test", "test", CocoLogLevel.INFO),
                    CocoLogLevel.INFO, "untrusted-body", null);
            AsyncCocoLogSink sink = new AsyncCocoLogSink(blockingDelegate, 1, listener);
            try {
                sink.log(record);
                assertThat(delegateStarted.await(5, TimeUnit.SECONDS)).isTrue();
                sink.log(record);
                sink.log(record);
                assertThat(context.getBean(SimpleMeterRegistry.class).get("coco.logging.dropped")
                        .tag("outcome", "dropped").counter().count()).isEqualTo(1.0);
            }
            finally {
                releaseDelegate.countDown();
                sink.close();
            }
        });
    }

    @Test
    void standardLoggingAutoConfigurationComposesUserLogListenerWithObservation() {
        CustomLogListenerConfiguration.dropCount.set(0);
        this.standardLoggingContextRunner.withUserConfiguration(CustomLogListenerConfiguration.class).run(context -> {
            CocoAsyncLogDropListener listener = context.getBean(CocoAsyncLogDropListener.class);
            assertThat(listener).isInstanceOf(CocoObservabilityAsyncLogDropListener.class);
            assertThat(context.getBeansOfType(CocoAsyncLogDropListener.class)).hasSize(2)
                    .containsValue(CustomLogListenerConfiguration.listener);
            listener.onDropped(CocoLogLevel.INFO, "custom", 1L);
            assertThat(CustomLogListenerConfiguration.dropCount).hasValue(1);
            assertThat(context.getBean(SimpleMeterRegistry.class).get("coco.logging.dropped")
                    .tag("outcome", "dropped").counter().count()).isEqualTo(1.0);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfiguration {

        @Bean
        SimpleMeterRegistry simpleMeterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FeaturePlanConfiguration {

        @Bean
        CocoFeaturePlan cocoFeaturePlan() {
            return StandardCocoFeatures.resolve(
                    CocoFeatureSelection.ofDisabled(Set.of(CocoFeature.MYBATIS_PLUS)));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomHealthConfiguration {

        @Bean(name = "cocoObservabilityHealthEndpoint")
        CocoActuatorHealthEndpoint replacementHealthEndpoint() {
            return new CocoActuatorHealthEndpoint(() -> Map.of("replacement", true));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomObservationConfiguration {

        static final AtomicInteger recordCount = new AtomicInteger();

        @Bean
        CocoObservationRecorder customObservationRecorder() {
            return new RecordingObservationRecorder();
        }

        static final class RecordingObservationRecorder implements CocoObservationRecorder {

            @Override
            public void record(CocoObservationKind kind, CocoObservationOutcome outcome) {
                recordCount.incrementAndGet();
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomLogListenerConfiguration {

        static final AtomicInteger dropCount = new AtomicInteger();

        static final CocoAsyncLogDropListener listener = (level, handleName, totalDropped) -> dropCount.incrementAndGet();

        @Bean
        CocoAsyncLogDropListener customLogDropListener() {
            return listener;
        }
    }
}
