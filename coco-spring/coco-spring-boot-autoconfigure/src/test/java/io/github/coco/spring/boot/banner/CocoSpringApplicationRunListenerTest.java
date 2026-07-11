package io.github.coco.spring.boot.banner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.github.coco.logging.core.CocoLogHandle;
import io.github.coco.logging.core.CocoLogHandleRegistry;
import io.github.coco.logging.core.CocoLogHandles;
import io.github.coco.logging.core.CocoLogLevel;
import io.github.coco.logging.core.CocoLogManager;
import io.github.coco.logging.core.CocoLogRecord;
import io.github.coco.logging.core.CocoLogSink;
import io.github.coco.logging.lifecycle.CocoLifecycleLogger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * <p>
 * Coco Spring 应用运行监听器测试。
 * </p>
 * <p>
 * 验证运行监听器在应用上下文可用后，会优先使用容器中的生命周期日志记录器，而不是回退到进程内默认日志管理器。
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
class CocoSpringApplicationRunListenerTest {

    /**
     * <p>
     * 应用上下文已经装配生命周期日志记录器时，启动日志应通过容器中的日志管理器输出。
     * </p>
     */
    @Test
    void usesLifecycleLoggerFromApplicationContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setId("sample-app");

        CapturingCocoLogSink sink = new CapturingCocoLogSink();
        CocoLogHandleRegistry registry = new CocoLogHandleRegistry();
        registry.register(CocoLogHandle.of(CocoLogHandles.LIFECYCLE, "io.github.coco.lifecycle.test",
                CocoLogLevel.INFO));

        context.registerBean(CocoLogHandleRegistry.class, () -> registry);
        context.registerBean(CocoLogSink.class, () -> sink);
        context.registerBean(CocoLogManager.class, () -> new CocoLogManager(registry, sink));
        context.registerBean(CocoLifecycleLogger.class,
                () -> new CocoLifecycleLogger(context.getBean(CocoLogManager.class)));
        context.refresh();

        try {
            CocoSpringApplicationRunListener listener =
                    new CocoSpringApplicationRunListener(new SpringApplication(Object.class), new String[0]);
            listener.started(context, Duration.ofMillis(1234));

            assertFalse(sink.records().isEmpty());
            assertEquals("lifecycle", sink.records().get(0).handle().name());
            assertEquals("io.github.coco.lifecycle.test", sink.records().get(0).handle().loggerName());
        }
        finally {
            context.close();
        }
    }

    /**
     * <p>
     * 测试用日志输出器。
     * </p>
     */
    static final class CapturingCocoLogSink implements CocoLogSink {

        private final List<CocoLogRecord> records = new ArrayList<>();

        /**
         * {@inheritDoc}
         */
        @Override
        public void log(CocoLogRecord record) {
            this.records.add(record);
        }

        /**
         * <p>
         * 返回已捕获的日志记录。
         * </p>
         * @return 日志记录列表
         */
        List<CocoLogRecord> records() {
            return this.records;
        }
    }
}
