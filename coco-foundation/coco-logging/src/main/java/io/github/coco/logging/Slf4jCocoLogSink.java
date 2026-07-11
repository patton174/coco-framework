package io.github.coco.logging;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 SLF4J 的 Coco 日志输出器。
 * <p>
 * 根据日志记录中的句柄选择隔离 logger，并按记录级别完成最终打印。
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
public final class Slf4jCocoLogSink implements CocoLogSink {

    /**
     * {@inheritDoc}
     */
    @Override
    public void log(CocoLogRecord record) {
        CocoLogRecord checkedRecord = Objects.requireNonNull(record, "record must not be null");
        Logger logger = LoggerFactory.getLogger(checkedRecord.handle().loggerName());
        checkedRecord.failure().ifPresentOrElse(
                failure -> logWithFailure(logger, checkedRecord, failure),
                () -> logMessage(logger, checkedRecord));
    }

    private static void logMessage(Logger logger, CocoLogRecord record) {
        switch (record.level()) {
            case ERROR -> logger.error(record.message());
            case WARN -> logger.warn(record.message());
            case INFO -> logger.info(record.message());
            case DEBUG -> logger.debug(record.message());
            case TRACE -> logger.trace(record.message());
            case OFF -> {
            }
        }
    }

    private static void logWithFailure(Logger logger, CocoLogRecord record, Throwable failure) {
        switch (record.level()) {
            case ERROR -> logger.error(record.message(), failure);
            case WARN -> logger.warn(record.message(), failure);
            case INFO -> logger.info(record.message(), failure);
            case DEBUG -> logger.debug(record.message(), failure);
            case TRACE -> logger.trace(record.message(), failure);
            case OFF -> {
            }
        }
    }
}
