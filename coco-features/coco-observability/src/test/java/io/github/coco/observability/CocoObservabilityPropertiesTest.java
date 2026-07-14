package io.github.coco.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CocoObservabilityPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsNestedPropertiesAndKeepsLiveMutableJavaBeans() {
        this.contextRunner.withPropertyValues(
                "coco.observability.enabled=false",
                "coco.observability.metrics.enabled=false",
                "coco.observability.metrics.audit-enabled=false",
                "coco.observability.metrics.replay-enabled=false",
                "coco.observability.metrics.rate-limit-enabled=false",
                "coco.observability.metrics.log-overflow-enabled=false",
                "coco.observability.health.enabled=false",
                "coco.observability.info.enabled=false")
                .run(context -> {
                    CocoObservabilityProperties properties = context.getBean(CocoObservabilityProperties.class);
                    CocoObservabilityProperties.MetricsProperties metrics = properties.getMetrics();
                    CocoObservabilityProperties.EndpointProperties health = properties.getHealth();
                    CocoObservabilityProperties.EndpointProperties info = properties.getInfo();

                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(metrics.isEnabled()).isFalse();
                    assertThat(metrics.isAuditEnabled()).isFalse();
                    assertThat(metrics.isReplayEnabled()).isFalse();
                    assertThat(metrics.isRateLimitEnabled()).isFalse();
                    assertThat(metrics.isLogOverflowEnabled()).isFalse();
                    assertThat(health.isEnabled()).isFalse();
                    assertThat(info.isEnabled()).isFalse();

                    metrics.setEnabled(true);
                    health.setEnabled(true);
                    info.setEnabled(true);

                    assertThat(properties.getMetrics()).isSameAs(metrics);
                    assertThat(properties.getHealth()).isSameAs(health);
                    assertThat(properties.getInfo()).isSameAs(info);
                    assertThat(properties.getMetrics().isEnabled()).isTrue();
                    assertThat(properties.getHealth().isEnabled()).isTrue();
                    assertThat(properties.getInfo().isEnabled()).isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CocoObservabilityProperties.class)
    static class PropertiesConfiguration {
    }
}
