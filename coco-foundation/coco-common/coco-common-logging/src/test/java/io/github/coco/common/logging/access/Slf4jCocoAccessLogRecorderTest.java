package io.github.coco.common.logging.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import io.github.coco.common.logging.core.CocoLogHandleRegistry;
import io.github.coco.common.logging.core.CocoLogHandles;
import io.github.coco.common.logging.core.CocoLogManager;
import io.github.coco.common.logging.core.CocoLogRecord;
import io.github.coco.common.logging.core.CocoLogSink;
import org.junit.jupiter.api.Test;

/**
 * Coco SLF4J 访问日志记录器测试。
 * <p>
 * 验证访问日志记录器会把 request 和 response 作为独立日志事件提交，并把异常对象挂到 response 事件。
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
class Slf4jCocoAccessLogRecorderTest {

    @Test
    void writesRequestAndResponseRecordsAndAttachesFailureToResponse() {
        CocoLogHandleRegistry registry = new CocoLogHandleRegistry();
        CocoLogHandles.registerDefaults(registry);
        CapturingCocoLogSink sink = new CapturingCocoLogSink();
        CocoLogManager logManager = new CocoLogManager(registry, sink);
        Slf4jCocoAccessLogRecorder recorder = new Slf4jCocoAccessLogRecorder(new CocoAccessLogProperties(),
                new DefaultCocoAccessLogFormatter(), logManager);
        RuntimeException failure = new RuntimeException("boom");
        CocoAccessLog accessLog = CocoAccessLog.of("trace-1001", "get", "/sample/products",
                500, 12L, false, null)
                .withFailure(failure);

        recorder.record(accessLog);

        assertEquals(2, sink.records().size());
        assertTrue(sink.records().get(0).message().startsWith("▸ request"));
        assertTrue(sink.records().get(0).failure().isEmpty());
        assertTrue(sink.records().get(1).message().startsWith("◂ response"));
        assertSame(failure, sink.records().get(1).failure().orElseThrow());
    }

    private static final class CapturingCocoLogSink implements CocoLogSink {

        private final List<CocoLogRecord> records = new ArrayList<>();

        @Override
        public void log(CocoLogRecord record) {
            this.records.add(record);
        }

        private List<CocoLogRecord> records() {
            return this.records;
        }
    }
}
