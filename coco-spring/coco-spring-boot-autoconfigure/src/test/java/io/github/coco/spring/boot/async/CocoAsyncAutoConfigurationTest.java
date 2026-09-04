package io.github.coco.spring.boot.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.github.coco.context.trace.CocoTraceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Coco 异步线程池自动配置测试。
 * <p>
 * 验证线程池自动装配、属性绑定、禁用开关以及跨线程上下文传播能力。
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
 * @since 1.1.0
 */
class CocoAsyncAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoAsyncAutoConfiguration.class));

    @Test
    void registersCocoTaskExecutorByDefault() {
        this.contextRunner.run(context -> {
            assertTrue(context.containsBean("cocoTaskExecutor"));
            ThreadPoolTaskExecutor executor = context.getBean("cocoTaskExecutor", ThreadPoolTaskExecutor.class);
            assertEquals(8, executor.getCorePoolSize());
            assertEquals(32, executor.getMaxPoolSize());
            assertEquals("coco-async-", executor.getThreadNamePrefix());
        });
    }

    @Test
    void disabledWhenPropertySetToFalse() {
        this.contextRunner
                .withPropertyValues("coco.async.enabled=false")
                .run(context -> assertFalse(context.containsBean("cocoTaskExecutor")));
    }

    @Test
    void propagatesTraceContextToAsyncTask() {
        this.contextRunner.run(context -> {
            ThreadPoolTaskExecutor executor = context.getBean("cocoTaskExecutor", ThreadPoolTaskExecutor.class);

            String expectedTraceId = "test-trace-id-12345";
            AtomicReference<String> capturedTraceId = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            CocoTraceContext.setTraceId(expectedTraceId);
            try {
                executor.execute(() -> {
                    try {
                        capturedTraceId.set(CocoTraceContext.currentTraceId().orElse(null));
                    }
                    finally {
                        latch.countDown();
                    }
                });

                assertTrue(latch.await(5, TimeUnit.SECONDS), "任务应在超时前完成");
                assertEquals(expectedTraceId, capturedTraceId.get());
            }
            finally {
                CocoTraceContext.clear();
            }
        });
    }
}
