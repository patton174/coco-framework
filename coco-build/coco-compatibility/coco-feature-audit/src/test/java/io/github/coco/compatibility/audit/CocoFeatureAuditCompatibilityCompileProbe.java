package io.github.coco.compatibility.audit;

import io.github.coco.feature.audit.CocoAuditAutoConfiguration;
import io.github.coco.feature.audit.CocoAuditFeature;
import io.github.coco.feature.audit.core.CocoAuditEvent;
import io.github.coco.feature.audit.core.CocoAuditPublisher;

final class CocoFeatureAuditCompatibilityCompileProbe {

    private static final Class<?>[] PUBLIC_TYPES = {
            CocoAuditFeature.class,
            CocoAuditAutoConfiguration.class,
            CocoAuditEvent.class,
            CocoAuditPublisher.class
    };

    private CocoFeatureAuditCompatibilityCompileProbe() {
    }
}
