package io.github.coco.common.logging.autoconfigure;

import io.github.coco.logging.access.CocoAccessLogFormatter;
import io.github.coco.logging.access.CocoAccessLogProperties;
import io.github.coco.logging.access.CocoAccessLogRecorder;
import io.github.coco.logging.access.DefaultCocoAccessLogFormatter;
import io.github.coco.logging.access.Slf4jCocoAccessLogRecorder;
import io.github.coco.logging.core.AsyncCocoLogSink;
import io.github.coco.logging.core.CocoAsyncLogDropListener;
import io.github.coco.logging.core.CocoLogHandle;
import io.github.coco.logging.core.CocoLogHandleRegistrar;
import io.github.coco.logging.core.CocoLogHandleRegistry;
import io.github.coco.logging.core.CocoLogHandles;
import io.github.coco.logging.core.CocoLogLevel;
import io.github.coco.logging.core.CocoLogManager;
import io.github.coco.logging.core.CocoLogSink;
import io.github.coco.logging.core.CocoLoggingProperties;
import io.github.coco.logging.core.Slf4jCocoAsyncLogDropListener;
import io.github.coco.logging.core.Slf4jCocoLogSink;
import io.github.coco.logging.lifecycle.CocoLifecycleLogger;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Coco 通用日志自动配置。
 * <p>
 * 注册日志句柄、默认日志输出器、日志管理器以及接口访问日志默认记录器。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-spring-boot-autoconfigure}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(CocoLoggingProperties.class)
public class CocoCommonLoggingAutoConfiguration {

    /**
     * <p>
     * 创建日志句柄注册表。
     * </p>
     * @param properties Coco 日志配置属性
     * @param registrars 业务侧和框架模块提供的日志句柄注册器
     * @return 日志句柄注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoLogHandleRegistry cocoLogHandleRegistry(CocoLoggingProperties properties,
            ObjectProvider<CocoLogHandleRegistrar> registrars, Environment environment) {
        CocoLogHandleRegistry registry = new CocoLogHandleRegistry();
        CocoLogHandles.registerDefaults(registry);
        registrars.orderedStream().forEach(registrar -> registrar.register(registry));
        registerAccessLogHandle(registry, properties, environment);
        return registry;
    }

    static void registerAccessLogHandle(CocoLogHandleRegistry registry, CocoLoggingProperties properties,
            Environment environment) {
        CocoLoggingProperties checkedProperties = properties == null ? new CocoLoggingProperties() : properties;
        CocoAccessLogProperties accessLogProperties = checkedProperties.getAccessLog();
        CocoLogHandle existingHandle = registry.find(CocoLogHandles.ACCESS)
                .orElseGet(() -> CocoLogHandle.of(CocoLogHandles.ACCESS, CocoAccessLogProperties.DEFAULT_LOGGER_NAME,
                        CocoLogLevel.INFO));
        String configuredLoggerName = accessLogProperties.getLoggerName();
        String loggerName = configuredLoggerName.equals(CocoAccessLogProperties.DEFAULT_LOGGER_NAME)
                ? existingHandle.loggerName()
                : configuredLoggerName;
        CocoLogLevel level = environment != null && environment.containsProperty("coco.logging.access-log.level")
                ? accessLogProperties.getLevel()
                : existingHandle.defaultLevel();
        registry.register(CocoLogHandle.of(CocoLogHandles.ACCESS, loggerName, level));
    }

    /**
     * <p>
     * 创建默认日志输出器。
     * </p>
     * @param properties Coco 日志配置属性
     * @param dropListeners 异步日志丢弃监听器提供器
     * @return 日志输出器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoLogSink cocoLogSink(CocoLoggingProperties properties,
            ObjectProvider<CocoAsyncLogDropListener> dropListeners) {
        return createCocoLogSink(properties, dropListeners::getObject);
    }

    /**
     * <p>
     * 使用默认丢弃监听器创建日志输出器，保留原有直接调用方式。
     * </p>
     * @param properties Coco 日志配置属性
     * @return 日志输出器
     */
    public CocoLogSink cocoLogSink(CocoLoggingProperties properties) {
        return createCocoLogSink(properties, Slf4jCocoAsyncLogDropListener::new);
    }

    private static CocoLogSink createCocoLogSink(CocoLoggingProperties properties,
            Supplier<CocoAsyncLogDropListener> dropListenerSupplier) {
        CocoLogSink slf4jSink = new Slf4jCocoLogSink();
        CocoLoggingProperties.AsyncProperties async = properties.getAsync();
        if (!async.isEnabled()) {
            return slf4jSink;
        }
        return new AsyncCocoLogSink(slf4jSink, async.getQueueCapacity(), dropListenerSupplier.get());
    }

    /**
     * <p>
     * 创建默认异步日志丢弃监听器。
     * </p>
     * @return 异步日志丢弃监听器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoAsyncLogDropListener cocoAsyncLogDropListener() {
        return new Slf4jCocoAsyncLogDropListener();
    }

    /**
     * <p>
     * 创建日志管理器。
     * </p>
     * @param registry 日志句柄注册表
     * @param sink 日志输出器
     * @return 日志管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoLogManager cocoLogManager(CocoLogHandleRegistry registry, CocoLogSink sink) {
        return new CocoLogManager(registry, sink);
    }

    /**
     * <p>
     * 创建应用生命周期日志记录器。
     * </p>
     * @param logManager Coco 日志管理器
     * @return 应用生命周期日志记录器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoLifecycleLogger cocoLifecycleLogger(CocoLogManager logManager) {
        return new CocoLifecycleLogger(logManager);
    }

    /**
     * <p>
     * 创建默认接口访问日志格式化器。
     * </p>
     * @return 接口访问日志格式化器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoAccessLogFormatter cocoAccessLogFormatter() {
        return new DefaultCocoAccessLogFormatter();
    }

    /**
     * <p>
     * 创建默认接口访问日志记录器。
     * </p>
     * @param properties Coco 日志配置属性
     * @param formatter 接口访问日志格式化器
     * @param logManager Coco 日志管理器
     * @return 接口访问日志记录器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "coco.logging.access-log", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public CocoAccessLogRecorder cocoAccessLogRecorder(CocoLoggingProperties properties,
            CocoAccessLogFormatter formatter, CocoLogManager logManager) {
        CocoAccessLogProperties accessLogProperties = properties.getAccessLog();
        return new Slf4jCocoAccessLogRecorder(accessLogProperties, formatter, logManager);
    }
}
