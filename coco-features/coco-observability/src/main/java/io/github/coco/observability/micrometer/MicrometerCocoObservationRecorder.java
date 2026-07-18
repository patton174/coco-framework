package io.github.coco.observability.micrometer;

import io.github.coco.observability.CocoObservationKind;
import io.github.coco.observability.CocoObservationOutcome;
import io.github.coco.observability.CocoObservationRecorder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;

/**
 * 基于 Micrometer 的 Coco 观察记录器。
 */
public final class MicrometerCocoObservationRecorder implements CocoObservationRecorder {

    private final MeterRegistry meterRegistry;

    public MicrometerCocoObservationRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    @Override
    public void record(CocoObservationKind kind, CocoObservationOutcome outcome) {
        CocoObservationKind checkedKind = Objects.requireNonNull(kind, "kind must not be null");
        CocoObservationOutcome checkedOutcome = Objects.requireNonNull(outcome, "outcome must not be null");
        Counter.builder(metricName(checkedKind))
                .tag("outcome", checkedOutcome.name().toLowerCase(java.util.Locale.ROOT))
                .register(this.meterRegistry)
                .increment();
    }

    private static String metricName(CocoObservationKind kind) {
        switch (kind) {
            case AUDIT:
                return "coco.audit.events";
            case REPLAY:
                return "coco.replay.reservations";
            case RATE_LIMIT:
                return "coco.rate_limit.decisions";
            case LOG_OVERFLOW:
                return "coco.logging.dropped";
            default:
                throw new IllegalArgumentException("unsupported observation kind: " + kind);
        }
    }
}
