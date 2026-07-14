package io.github.coco.observability;

import io.github.coco.observability.logging.CocoObservabilityAsyncLogDropListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Coco 异步日志溢出可观测性自动配置。
 * <p>
 * 该配置必须早于标准日志自动配置，使可观测性组合 listener 成为异步日志输出器的唯一主监听器。
 * 没有业务自定义 listener 时保留标准 SLF4J 诊断；存在业务 listener 时委派业务 listener 并继续记录安全指标。
 * </p>
 */
@AutoConfiguration(beforeName = "io.github.coco.common.logging.autoconfigure.CocoCommonLoggingAutoConfiguration")
@ConditionalOnClass(name = {
        "io.micrometer.core.instrument.MeterRegistry",
        "io.github.coco.logging.core.CocoAsyncLogDropListener" })
@ConditionalOnBean(type = "io.micrometer.core.instrument.MeterRegistry")
@ConditionalOnProperty(prefix = "coco.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "coco.observability.metrics", name = { "enabled", "log-overflow-enabled" },
        havingValue = "true", matchIfMissing = true)
public class CocoObservabilityLoggingAutoConfiguration {

    /**
     * 创建标准日志默认诊断与可观测指标的组合 listener。
     * @param observationProvider 日志溢出观察提供器
     * @param dropListeners 业务日志丢弃监听器提供器
     * @return 异步日志丢弃监听器
     */
    @Bean
    @Primary
    public io.github.coco.logging.core.CocoAsyncLogDropListener cocoObservabilityAsyncLogDropListener(
            ObjectProvider<CocoLogOverflowObservation> observationProvider,
            ObjectProvider<io.github.coco.logging.core.CocoAsyncLogDropListener> dropListeners) {
        return new CocoObservabilityAsyncLogDropListener(observationProvider, dropListeners);
    }
}
