package io.github.coco.observability.audit;

import io.github.coco.feature.audit.core.CocoAuditEvent;
import io.github.coco.feature.audit.core.CocoAuditRecorder;
import io.github.coco.observability.CocoObservationKind;
import io.github.coco.observability.CocoObservationOutcome;
import io.github.coco.observability.CocoObservationRecorder;
import java.util.Objects;

/**
 * 将审计事件的业务结果接入可观测性记录器。
 */
public final class CocoObservabilityAuditRecorder implements CocoAuditRecorder {

    private final CocoObservationRecorder observationRecorder;

    public CocoObservabilityAuditRecorder(CocoObservationRecorder observationRecorder) {
        this.observationRecorder = Objects.requireNonNull(observationRecorder, "observationRecorder must not be null");
    }

    @Override
    public void record(CocoAuditEvent event) {
        CocoAuditEvent checkedEvent = Objects.requireNonNull(event, "event must not be null");
        this.observationRecorder.record(CocoObservationKind.AUDIT,
                checkedEvent.success() ? CocoObservationOutcome.SUCCESS : CocoObservationOutcome.FAILURE);
    }
}
