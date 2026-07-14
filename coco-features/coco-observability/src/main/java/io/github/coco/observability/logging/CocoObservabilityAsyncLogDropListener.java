package io.github.coco.observability.logging;

import io.github.coco.logging.core.CocoAsyncLogDropListener;
import io.github.coco.logging.core.CocoLogLevel;
import io.github.coco.logging.core.Slf4jCocoAsyncLogDropListener;
import io.github.coco.observability.CocoLogOverflowObservation;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 保留默认日志诊断并记录安全的异步日志溢出计数。
 */
public final class CocoObservabilityAsyncLogDropListener implements CocoAsyncLogDropListener {

    private final CocoAsyncLogDropListener diagnosticListener = new Slf4jCocoAsyncLogDropListener();

    private final ObjectProvider<CocoLogOverflowObservation> observationProvider;

    public CocoObservabilityAsyncLogDropListener(ObjectProvider<CocoLogOverflowObservation> observationProvider) {
        this.observationProvider = Objects.requireNonNull(observationProvider, "observationProvider must not be null");
    }

    @Override
    public void onDropped(CocoLogLevel level, String handleName, long totalDropped) {
        this.diagnosticListener.onDropped(level, handleName, totalDropped);
        CocoLogOverflowObservation observation = this.observationProvider.getIfAvailable();
        if (observation != null) {
            observation.recordDrop();
        }
    }
}
