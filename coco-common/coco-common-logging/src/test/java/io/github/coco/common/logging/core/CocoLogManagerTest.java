package io.github.coco.common.logging.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * Coco 日志管理器测试。
 * <p>
 * 验证框架内部模块可以向日志模块注册隔离日志句柄，并由日志模块统一接管输出。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-logging}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoLogManagerTest {

    @Test
    void dispatchesMessageThroughRegisteredHandle() {
        CocoLogHandleRegistry registry = new CocoLogHandleRegistry();
        registry.register(CocoLogHandle.of("sample", "io.github.coco.sample", CocoLogLevel.INFO));
        CapturingCocoLogSink sink = new CapturingCocoLogSink();
        CocoLogManager manager = new CocoLogManager(registry, sink);

        manager.log("sample", CocoLogLevel.INFO, "hello coco", null);

        CocoLogRecord record = sink.lastRecord().orElseThrow();
        assertEquals("sample", record.handle().name());
        assertEquals("io.github.coco.sample", record.handle().loggerName());
        assertEquals(CocoLogLevel.INFO, record.level());
        assertEquals("hello coco", record.message());
    }

    @Test
    void ignoresDisabledHandleLevel() {
        CocoLogHandleRegistry registry = new CocoLogHandleRegistry();
        registry.register(CocoLogHandle.of("sample", "io.github.coco.sample", CocoLogLevel.OFF));
        CapturingCocoLogSink sink = new CapturingCocoLogSink();
        CocoLogManager manager = new CocoLogManager(registry, sink);

        manager.log("sample", CocoLogLevel.INFO, "hello coco", null);

        assertTrue(sink.lastRecord().isEmpty());
    }

    @Test
    void filtersRecordsBelowHandleThreshold() {
        CocoLogHandleRegistry registry = new CocoLogHandleRegistry();
        registry.register(CocoLogHandle.of("sample", "io.github.coco.sample", CocoLogLevel.WARN));
        CapturingCocoLogSink sink = new CapturingCocoLogSink();
        CocoLogManager manager = new CocoLogManager(registry, sink);

        manager.log("sample", CocoLogLevel.INFO, "hidden coco", null);

        assertTrue(sink.lastRecord().isEmpty());
    }

    @Test
    void dispatchesRecordsAtOrAboveHandleThreshold() {
        CocoLogHandleRegistry registry = new CocoLogHandleRegistry();
        registry.register(CocoLogHandle.of("sample", "io.github.coco.sample", CocoLogLevel.WARN));
        CapturingCocoLogSink sink = new CapturingCocoLogSink();
        CocoLogManager manager = new CocoLogManager(registry, sink);

        manager.log("sample", CocoLogLevel.ERROR, "visible coco", null);

        CocoLogRecord record = sink.lastRecord().orElseThrow();
        assertEquals(CocoLogLevel.ERROR, record.level());
        assertEquals("visible coco", record.message());
    }

    private static final class CapturingCocoLogSink implements CocoLogSink {

        private final AtomicReference<CocoLogRecord> lastRecord = new AtomicReference<>();

        @Override
        public void log(CocoLogRecord record) {
            this.lastRecord.set(record);
        }

        private Optional<CocoLogRecord> lastRecord() {
            return Optional.ofNullable(this.lastRecord.get());
        }
    }
}
