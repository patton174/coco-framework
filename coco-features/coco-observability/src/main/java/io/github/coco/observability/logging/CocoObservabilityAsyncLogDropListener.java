package io.github.coco.observability.logging;

import io.github.coco.logging.core.CocoAsyncLogDropListener;
import io.github.coco.logging.core.CocoLogLevel;
import io.github.coco.logging.core.Slf4jCocoAsyncLogDropListener;
import io.github.coco.observability.CocoLogOverflowObservation;
import java.util.Objects;

/**
 * 保留默认日志诊断并记录安全的异步日志溢出计数。
 */
public final class CocoObservabilityAsyncLogDropListener implements CocoAsyncLogDropListener {

    private final CocoAsyncLogDropListener diagnosticListener = new Slf4jCocoAsyncLogDropListener();

    private final CocoLogOverflowObservation observation;

    public CocoObservabilityAsyncLogDropListener(CocoLogOverflowObservation observation) {
        this.observation = Objects.requireNonNull(observation, "observation must not be null");
    }

    @Override
    public void onDropped(CocoLogLevel level, String handleName, long totalDropped) {
        this.diagnosticListener.onDropped(level, handleName, totalDropped);
        this.observation.recordDrop();
    }
}
