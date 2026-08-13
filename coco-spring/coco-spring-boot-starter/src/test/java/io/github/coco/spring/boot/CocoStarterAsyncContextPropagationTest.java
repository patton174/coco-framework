package io.github.coco.spring.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.coco.context.CocoContextSnapshotFactory;
import io.github.coco.context.spring.CocoContextTaskDecorator;
import io.github.coco.context.trace.CocoTraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

class CocoStarterAsyncContextPropagationTest {

    @AfterEach
    void clearContext() {
        CocoTraceContext.clear();
    }

    @Test
    void propagatesSubmissionContextThroughBootDefaultAsyncExecutor() throws Exception {
        SpringApplication application = new SpringApplication(AsyncTestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        application.setDefaultProperties(Map.of(
                "spring.main.banner-mode", "off",
                "spring.task.execution.pool.core-size", "1",
                "spring.task.execution.pool.max-size", "1",
                "spring.task.execution.pool.queue-capacity", "16",
                "coco.features.disabled[0]", "mybatis-plus",
                "coco.features.disabled[1]", "tenant",
                "coco.features.disabled[2]", "data-permission"));

        CountDownLatch releaseWorker = new CountDownLatch(1);
        try (ConfigurableApplicationContext context = application.run()) {
            assertAutoConfiguredPropagationBeans(context);
            AsyncTaskExecutor executor = context.getBean(
                    TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME, AsyncTaskExecutor.class);
            AsyncProbe probe = context.getBean(AsyncProbe.class);
            CountDownLatch workerStarted = new CountDownLatch(1);
            executor.execute(() -> {
                workerStarted.countDown();
                await(releaseWorker);
            });
            assertThat(workerStarted.await(5, TimeUnit.SECONDS)).isTrue();

            CocoTraceContext.setTraceId("submitted");
            CompletableFuture<String> submitted = probe.currentTraceId();
            CocoTraceContext.setTraceId("changed-after-submit");
            releaseWorker.countDown();

            assertThat(submitted.get(5, TimeUnit.SECONDS)).isEqualTo("submitted");
            CocoTraceContext.clear();
            assertThat(probe.currentTraceId().get(5, TimeUnit.SECONDS)).isNull();

            CocoTraceContext.setTraceId("exception-submitted");
            CompletableFuture<Void> failed = probe.failAfterMutation();
            CocoTraceContext.clear();
            assertThatThrownBy(() -> failed.get(5, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("async failure");
            assertThat(probe.currentTraceId().get(5, TimeUnit.SECONDS)).isNull();
            assertThat(context.getBean(AtomicInteger.class)).hasPositiveValue();
        }
        finally {
            releaseWorker.countDown();
        }
    }

    private static void assertAutoConfiguredPropagationBeans(ConfigurableApplicationContext context) {
        assertThat(context.getBean(CocoContextTaskDecorator.class)).isNotNull();
        assertThat(context.getBean(CocoContextSnapshotFactory.class)).isNotNull();
        assertThat(context.getBeanFactory().getBeanDefinition("cocoContextTaskDecorator").getFactoryMethodName())
                .isEqualTo("cocoContextTaskDecorator");
        assertThat(context.getBeanFactory().getBeanDefinition("cocoContextSnapshotFactory").getFactoryMethodName())
                .isEqualTo("cocoContextSnapshotFactory");
        assertThat(context.getBeansOfType(TaskDecorator.class)).hasSize(2);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("worker release timed out");
            }
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("worker interrupted", ex);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableAsync
    static class AsyncTestApplication {

        @Bean
        AsyncProbe asyncProbe() {
            return new AsyncProbe();
        }

        @Bean
        AtomicInteger businessDecorations() {
            return new AtomicInteger();
        }

        @Bean
        TaskDecorator businessTaskDecorator(AtomicInteger businessDecorations) {
            return runnable -> {
                businessDecorations.incrementAndGet();
                return runnable;
            };
        }
    }

    static class AsyncProbe {

        @Async
        public CompletableFuture<String> currentTraceId() {
            return CompletableFuture.completedFuture(CocoTraceContext.currentTraceId().orElse(null));
        }

        @Async
        public CompletableFuture<Void> failAfterMutation() {
            assertThat(CocoTraceContext.currentTraceId()).contains("exception-submitted");
            CocoTraceContext.setTraceId("worker-mutated");
            throw new IllegalStateException("async failure");
        }
    }
}
