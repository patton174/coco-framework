package io.github.coco.observability.logging;

import io.github.coco.logging.core.CocoAsyncLogDropListener;
import io.github.coco.logging.core.CocoLogLevel;
import io.github.coco.logging.core.Slf4jCocoAsyncLogDropListener;
import io.github.coco.observability.CocoLogOverflowObservation;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 保留默认日志诊断并记录安全的异步日志溢出计数。
 */
public final class CocoObservabilityAsyncLogDropListener implements CocoAsyncLogDropListener {

    private final CocoAsyncLogDropListener diagnosticListener = new Slf4jCocoAsyncLogDropListener();

    private final Supplier<CocoLogOverflowObservation> observationSupplier;

    private final Supplier<Stream<CocoAsyncLogDropListener>> listenerSupplier;

    /**
     * 创建使用固定 observation 的日志丢弃监听器。
     * <p>
     * 该构造器保留既有源码和二进制契约，每次丢弃先执行标准 SLF4J 诊断，再记录一次 observation。
     * </p>
     * @param observation 日志溢出 observation
     */
    public CocoObservabilityAsyncLogDropListener(CocoLogOverflowObservation observation) {
        this(fixedObservation(observation), Stream::empty);
    }

    /**
     * 创建延迟解析 observation 的日志丢弃监听器。
     * @param observationProvider 日志溢出 observation 提供器
     */
    public CocoObservabilityAsyncLogDropListener(ObjectProvider<CocoLogOverflowObservation> observationProvider) {
        this(observationSupplier(observationProvider), Stream::empty);
    }

    /**
     * 创建延迟解析 observation 并组合业务监听器的日志丢弃监听器。
     * <p>
     * 没有业务监听器时保留标准 SLF4J 诊断；存在业务监听器时按 Spring 顺序委派业务监听器。
     * </p>
     * @param observationProvider 日志溢出 observation 提供器
     * @param listenerProvider 业务日志丢弃监听器提供器
     */
    public CocoObservabilityAsyncLogDropListener(ObjectProvider<CocoLogOverflowObservation> observationProvider,
            ObjectProvider<CocoAsyncLogDropListener> listenerProvider) {
        this(observationSupplier(observationProvider), listenerSupplier(listenerProvider));
    }

    private CocoObservabilityAsyncLogDropListener(Supplier<CocoLogOverflowObservation> observationSupplier,
            Supplier<Stream<CocoAsyncLogDropListener>> listenerSupplier) {
        this.observationSupplier = observationSupplier;
        this.listenerSupplier = listenerSupplier;
    }

    @Override
    public void onDropped(CocoLogLevel level, String handleName, long totalDropped) {
        List<CocoAsyncLogDropListener> listeners = this.listenerSupplier.get()
                .filter(listener -> !(listener instanceof CocoObservabilityAsyncLogDropListener))
                .toList();
        if (listeners.isEmpty()) {
            this.diagnosticListener.onDropped(level, handleName, totalDropped);
        }
        else {
            listeners.forEach(listener -> listener.onDropped(level, handleName, totalDropped));
        }
        CocoLogOverflowObservation observation = this.observationSupplier.get();
        if (observation != null) {
            observation.recordDrop();
        }
    }

    private static Supplier<CocoLogOverflowObservation> fixedObservation(CocoLogOverflowObservation observation) {
        CocoLogOverflowObservation checkedObservation = Objects.requireNonNull(observation,
                "observation must not be null");
        return () -> checkedObservation;
    }

    private static Supplier<CocoLogOverflowObservation> observationSupplier(
            ObjectProvider<CocoLogOverflowObservation> observationProvider) {
        ObjectProvider<CocoLogOverflowObservation> checkedProvider = Objects.requireNonNull(observationProvider,
                "observationProvider must not be null");
        return checkedProvider::getIfAvailable;
    }

    private static Supplier<Stream<CocoAsyncLogDropListener>> listenerSupplier(
            ObjectProvider<CocoAsyncLogDropListener> listenerProvider) {
        ObjectProvider<CocoAsyncLogDropListener> checkedProvider = Objects.requireNonNull(listenerProvider,
                "listenerProvider must not be null");
        return checkedProvider::orderedStream;
    }
}
