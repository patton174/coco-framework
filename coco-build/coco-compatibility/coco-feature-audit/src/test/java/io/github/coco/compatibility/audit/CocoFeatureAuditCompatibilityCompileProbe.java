package io.github.coco.compatibility.audit;

import io.github.coco.feature.audit.CocoAuditAutoConfiguration;
import io.github.coco.feature.audit.CocoAuditFeature;
import io.github.coco.feature.audit.CocoAuditProperties;
import io.github.coco.feature.audit.accesslog.CocoAccessLogAuditRecorder;
import io.github.coco.feature.audit.core.CocoAuditErrorHandler;
import io.github.coco.feature.audit.core.CocoAuditEvent;
import io.github.coco.feature.audit.core.CocoAuditFailurePolicy;
import io.github.coco.feature.audit.core.CocoAuditFormatter;
import io.github.coco.feature.audit.core.CocoAuditPublisher;
import io.github.coco.feature.audit.core.CocoAuditRecorder;
import io.github.coco.feature.audit.core.CompositeCocoAuditPublisher;
import io.github.coco.feature.audit.core.DefaultCocoAuditFormatter;
import io.github.coco.feature.audit.core.LoggingCocoAuditRecorder;
import io.github.coco.feature.audit.core.NoOpCocoAuditRecorder;
import io.github.coco.feature.audit.core.PolicyCocoAuditErrorHandler;

final class CocoFeatureAuditCompatibilityCompileProbe {

    private static final Class<?>[] PUBLIC_TYPES = {
            CocoAuditFeature.class,
            CocoAuditAutoConfiguration.class,
            CocoAuditProperties.class,
            CocoAccessLogAuditRecorder.class,
            CocoAuditErrorHandler.class,
            CocoAuditEvent.class,
            CocoAuditFailurePolicy.class,
            CocoAuditFormatter.class,
            CocoAuditPublisher.class,
            CocoAuditRecorder.class,
            CompositeCocoAuditPublisher.class,
            DefaultCocoAuditFormatter.class,
            LoggingCocoAuditRecorder.class,
            NoOpCocoAuditRecorder.class,
            PolicyCocoAuditErrorHandler.class
    };

    private CocoFeatureAuditCompatibilityCompileProbe() {
    }
}
