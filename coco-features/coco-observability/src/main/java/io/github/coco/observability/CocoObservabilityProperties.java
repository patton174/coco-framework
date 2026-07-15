package io.github.coco.observability;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco 可观测性适配配置。
 */
@ConfigurationProperties("coco.observability")
public class CocoObservabilityProperties {

    private boolean enabled = true;

    private final MetricsProperties metrics = new MetricsProperties();

    private final EndpointProperties health = new EndpointProperties();

    private final EndpointProperties info = new EndpointProperties();

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP",
            justification = "Spring Boot nested configuration requires the live mutable JavaBean instance")
    public MetricsProperties getMetrics() {
        return this.metrics;
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP",
            justification = "Spring Boot nested configuration requires the live mutable JavaBean instance")
    public EndpointProperties getHealth() {
        return this.health;
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP",
            justification = "Spring Boot nested configuration requires the live mutable JavaBean instance")
    public EndpointProperties getInfo() {
        return this.info;
    }

    public static class MetricsProperties {

        private boolean enabled = true;

        private boolean auditEnabled = true;

        private boolean replayEnabled = true;

        private boolean rateLimitEnabled = true;

        private boolean logOverflowEnabled = true;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAuditEnabled() {
            return this.auditEnabled;
        }

        public void setAuditEnabled(boolean auditEnabled) {
            this.auditEnabled = auditEnabled;
        }

        public boolean isReplayEnabled() {
            return this.replayEnabled;
        }

        public void setReplayEnabled(boolean replayEnabled) {
            this.replayEnabled = replayEnabled;
        }

        public boolean isRateLimitEnabled() {
            return this.rateLimitEnabled;
        }

        public void setRateLimitEnabled(boolean rateLimitEnabled) {
            this.rateLimitEnabled = rateLimitEnabled;
        }

        public boolean isLogOverflowEnabled() {
            return this.logOverflowEnabled;
        }

        public void setLogOverflowEnabled(boolean logOverflowEnabled) {
            this.logOverflowEnabled = logOverflowEnabled;
        }
    }

    public static class EndpointProperties {

        private boolean enabled = true;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
