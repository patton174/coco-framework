package io.github.coco.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.coco.feature.audit.core.CocoAuditEvent;
import io.github.coco.feature.audit.core.CocoAuditRecorder;
import io.github.coco.feature.model.CocoFeaturePlan;
import io.github.coco.logging.core.CocoAsyncLogDropListener;
import io.github.coco.logging.core.CocoLogLevel;
import io.github.coco.observability.actuator.CocoActuatorHealthEndpoint;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            context.getBean(CocoAsyncLogDropListener.class).onDropped(CocoLogLevel.INFO, "request-9842", 1L);

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
    void exposesSafeStartupAndFeaturePlanStatusThroughActuatorTypes() {
        this.contextRunner.withUserConfiguration(FeaturePlanConfiguration.class).run(context -> {
            CocoActuatorHealthEndpoint healthEndpoint = context.getBean("cocoObservabilityHealthEndpoint",
                    CocoActuatorHealthEndpoint.class);
            assertThat(healthEndpoint.health()).containsEntry("status", "UP").containsKeys("startup", "featurePlan");
            assertThat(healthEndpoint.health().get("featurePlan"))
                    .isEqualTo(Map.of("status", "available", "enabledCount", 0, "disabledCount", 0));

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
            return new CocoFeaturePlan(Set.of(), Set.of(), List.of());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomHealthConfiguration {

        @Bean(name = "cocoObservabilityHealthEndpoint")
        CocoActuatorHealthEndpoint replacementHealthEndpoint() {
            return new CocoActuatorHealthEndpoint(() -> Map.of("replacement", true));
        }
    }
}
