package io.github.coco.logging.access;

import java.util.List;
import java.util.Objects;

import io.github.coco.logging.core.CocoLogHandles;
import io.github.coco.logging.core.CocoLogManager;

/**
 * 基于 Coco 日志管理器的接口访问日志记录器。
 * <p>
 * 将访问日志格式化后提交给日志模块，由日志模块统一选择隔离 logger、输出级别和最终输出器。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-logging}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class Slf4jCocoAccessLogRecorder implements CocoAccessLogRecorder {

    private final CocoAccessLogProperties properties;

    private final CocoAccessLogFormatter formatter;

    private final CocoLogManager logManager;

    /**
     * <p>
     * 创建接口访问日志记录器。
     * </p>
     * @param properties 接口访问日志配置
     * @param formatter 接口访问日志格式化器
     * @param logManager Coco 日志管理器
     */
    public Slf4jCocoAccessLogRecorder(CocoAccessLogProperties properties, CocoAccessLogFormatter formatter,
            CocoLogManager logManager) {
        this.properties = properties == null ? new CocoAccessLogProperties() : properties;
        this.formatter = Objects.requireNonNull(formatter, "formatter must not be null");
        this.logManager = Objects.requireNonNull(logManager, "logManager must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void record(CocoAccessLog accessLog) {
        Objects.requireNonNull(accessLog, "accessLog must not be null");
        if (!this.properties.isEnabled() || !this.properties.getLevel().enabled()) {
            return;
        }
        List<String> entries = this.formatter.formatEntries(accessLog, this.properties);
        if (entries == null || entries.isEmpty()) {
            return;
        }
        int lastPrintableIndex = lastPrintableIndex(entries);
        for (int index = 0; index < entries.size(); index++) {
            String entry = entries.get(index);
            if (entry == null || entry.isBlank()) {
                continue;
            }
            Throwable failure = index == lastPrintableIndex ? accessLog.failure().orElse(null) : null;
            this.logManager.log(CocoLogHandles.ACCESS, this.properties.getLevel(), entry, failure);
        }
    }

    private static int lastPrintableIndex(List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return -1;
        }
        for (int index = entries.size() - 1; index >= 0; index--) {
            String entry = entries.get(index);
            if (entry != null && !entry.isBlank()) {
                return index;
            }
        }
        return -1;
    }
}
