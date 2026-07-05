package io.github.coco.feature.web.logging;

import java.util.Objects;

import io.github.coco.common.accesslog.CocoAccessLog;
import io.github.coco.common.accesslog.CocoAccessLogRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 SLF4J 的 Coco 接口访问日志记录器。
 * <p>
 * 将访问日志写入独立 logger，便于业务应用通过日志框架单独配置输出级别、文件和采集规则。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class Slf4jCocoAccessLogRecorder implements CocoAccessLogRecorder {

    private final CocoAccessLogProperties properties;

    private final CocoAccessLogFormatter formatter;

    private final Logger logger;

    /**
     * <p>
     * 创建 SLF4J 接口访问日志记录器。
     * </p>
     * @param properties 接口访问日志配置
     * @param formatter 接口访问日志格式化器
     */
    public Slf4jCocoAccessLogRecorder(CocoAccessLogProperties properties, CocoAccessLogFormatter formatter) {
        this.properties = properties == null ? new CocoAccessLogProperties() : properties;
        this.formatter = Objects.requireNonNull(formatter, "formatter must not be null");
        this.logger = LoggerFactory.getLogger(this.properties.getLoggerName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void record(CocoAccessLog accessLog) {
        Objects.requireNonNull(accessLog, "accessLog must not be null");
        CocoAccessLogLevel level = this.properties.getLevel();
        if (!this.properties.isEnabled() || !level.enabled()) {
            return;
        }
        String message = this.formatter.format(accessLog, this.properties);
        switch (level) {
            case ERROR -> this.logger.error(message);
            case WARN -> this.logger.warn(message);
            case INFO -> this.logger.info(message);
            case DEBUG -> this.logger.debug(message);
            case TRACE -> this.logger.trace(message);
            case OFF -> {
            }
        }
    }
}
