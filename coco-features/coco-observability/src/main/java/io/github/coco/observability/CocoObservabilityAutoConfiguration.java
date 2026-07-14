package io.github.coco.observability;

import io.github.coco.observability.micrometer.MicrometerCocoObservationRecorder;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Coco 可观测性可选自动配置。
 */
@AutoConfiguration(afterName = "io.github.coco.feature.audit.CocoAuditAutoConfiguration")
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
@ConditionalOnProperty(prefix = "coco.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CocoObservabilityProperties.class)
public class CocoObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnBean(type = "io.micrometer.core.instrument.MeterRegistry")
    @ConditionalOnMissingBean(CocoObservationRecorder.class)
    @ConditionalOnProperty(prefix = "coco.observability.metrics", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public CocoObservationRecorder cocoObservationRecorder(
            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        return new MicrometerCocoObservationRecorder(meterRegistry);
    }

    @Bean
    @ConditionalOnBean(type = "io.micrometer.core.instrument.MeterRegistry")
    @ConditionalOnMissingBean(CocoReplayObservation.class)
    @ConditionalOnProperty(prefix = "coco.observability.metrics", name = { "enabled", "replay-enabled" },
            havingValue = "true", matchIfMissing = true)
    public CocoReplayObservation cocoReplayObservation(CocoObservationRecorder recorder) {
        return outcome -> recorder.record(CocoObservationKind.REPLAY, requireReplayOutcome(outcome));
    }

    @Bean
    @ConditionalOnBean(type = "io.micrometer.core.instrument.MeterRegistry")
    @ConditionalOnMissingBean(CocoRateLimitObservation.class)
    @ConditionalOnProperty(prefix = "coco.observability.metrics", name = { "enabled", "rate-limit-enabled" },
            havingValue = "true", matchIfMissing = true)
    public CocoRateLimitObservation cocoRateLimitObservation(CocoObservationRecorder recorder) {
        return outcome -> recorder.record(CocoObservationKind.RATE_LIMIT, requireRateLimitOutcome(outcome));
    }

    @Bean
    @ConditionalOnBean(type = "io.micrometer.core.instrument.MeterRegistry")
    @ConditionalOnMissingBean(CocoLogOverflowObservation.class)
    @ConditionalOnProperty(prefix = "coco.observability.metrics", name = { "enabled", "log-overflow-enabled" },
            havingValue = "true", matchIfMissing = true)
    public CocoLogOverflowObservation cocoLogOverflowObservation(CocoObservationRecorder recorder) {
        return () -> recorder.record(CocoObservationKind.LOG_OVERFLOW, CocoObservationOutcome.DROPPED);
    }

    @Bean
    @ConditionalOnMissingBean(CocoObservabilityStatusProvider.class)
    public CocoObservabilityStatusProvider cocoObservabilityStatusProvider(ConfigurableApplicationContext applicationContext,
            ObjectProvider<CocoFeaturePlanStatusContributor> featurePlanContributors) {
        return () -> {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("startup", applicationContext.isRunning() ? "running" : "starting");
            CocoFeaturePlanStatusContributor contributor = featurePlanContributors.getIfAvailable();
            status.put("featurePlan", contributor == null ? Map.of("status", "unavailable") : contributor.contribute());
            return Map.copyOf(status);
        };
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.github.coco.feature.model.CocoFeaturePlan")
    static class FeaturePlanStatusConfiguration {

        @Bean
        @ConditionalOnBean(type = "io.github.coco.feature.model.CocoFeaturePlan")
        @ConditionalOnMissingBean(CocoFeaturePlanStatusContributor.class)
        CocoFeaturePlanStatusContributor cocoFeaturePlanStatusContributor(
                io.github.coco.feature.model.CocoFeaturePlan featurePlan) {
            return () -> Map.of(
                    "status", "available",
                    "enabledCount", featurePlan.enabledFeatures().size(),
                    "disabledCount", featurePlan.disabledFeatures().size());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "io.github.coco.feature.audit.core.CocoAuditRecorder",
            "io.github.coco.feature.audit.core.CocoAuditEvent" })
    static class AuditMetricsConfiguration {

        @Bean(name = "cocoObservabilityAuditRecorder")
        @ConditionalOnBean(type = "io.micrometer.core.instrument.MeterRegistry")
        @ConditionalOnMissingBean(name = "cocoObservabilityAuditRecorder")
        @ConditionalOnProperty(prefix = "coco.observability.metrics", name = { "enabled", "audit-enabled" },
                havingValue = "true", matchIfMissing = true)
        io.github.coco.feature.audit.core.CocoAuditRecorder cocoObservabilityAuditRecorder(
                CocoObservationRecorder recorder) {
            return new io.github.coco.observability.audit.CocoObservabilityAuditRecorder(recorder);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "io.github.coco.logging.core.CocoAsyncLogDropListener",
            "io.github.coco.logging.core.CocoLogLevel" })
    static class LoggingMetricsConfiguration {

        @Bean
        @ConditionalOnBean(type = "io.micrometer.core.instrument.MeterRegistry")
        @ConditionalOnMissingBean(type = "io.github.coco.logging.core.CocoAsyncLogDropListener")
        @ConditionalOnProperty(prefix = "coco.observability.metrics", name = { "enabled", "log-overflow-enabled" },
                havingValue = "true", matchIfMissing = true)
        io.github.coco.logging.core.CocoAsyncLogDropListener cocoObservabilityAsyncLogDropListener(
                CocoLogOverflowObservation observation) {
            return new io.github.coco.observability.logging.CocoObservabilityAsyncLogDropListener(observation);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
    static class ActuatorEndpointConfiguration {

        @Bean(name = "cocoObservabilityHealthEndpoint")
        @ConditionalOnMissingBean(name = "cocoObservabilityHealthEndpoint")
        @ConditionalOnProperty(prefix = "coco.observability.health", name = "enabled", havingValue = "true",
                matchIfMissing = true)
        io.github.coco.observability.actuator.CocoActuatorHealthEndpoint cocoObservabilityHealthEndpoint(
                CocoObservabilityStatusProvider statusProvider) {
            return new io.github.coco.observability.actuator.CocoActuatorHealthEndpoint(statusProvider);
        }

        @Bean(name = "cocoObservabilityInfoContributor")
        @ConditionalOnMissingBean(name = "cocoObservabilityInfoContributor")
        @ConditionalOnProperty(prefix = "coco.observability.info", name = "enabled", havingValue = "true",
                matchIfMissing = true)
        org.springframework.boot.actuate.info.InfoContributor cocoObservabilityInfoContributor(
                CocoObservabilityStatusProvider statusProvider) {
            return new io.github.coco.observability.actuator.CocoActuatorInfoContributor(statusProvider);
        }
    }

    private static CocoObservationOutcome requireReplayOutcome(CocoObservationOutcome outcome) {
        if (outcome == CocoObservationOutcome.ACCEPTED || outcome == CocoObservationOutcome.DUPLICATE
                || outcome == CocoObservationOutcome.CAPACITY_EXCEEDED || outcome == CocoObservationOutcome.ERROR) {
            return outcome;
        }
        throw new IllegalArgumentException("unsupported replay observation outcome: " + outcome);
    }

    private static CocoObservationOutcome requireRateLimitOutcome(CocoObservationOutcome outcome) {
        if (outcome == CocoObservationOutcome.ALLOWED || outcome == CocoObservationOutcome.REJECTED) {
            return outcome;
        }
        throw new IllegalArgumentException("unsupported rate-limit observation outcome: " + outcome);
    }
}
