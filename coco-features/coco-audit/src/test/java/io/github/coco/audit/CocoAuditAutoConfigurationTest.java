package io.github.coco.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.coco.spring.boot.autoconfigure.i18n.CocoI18nAutoConfiguration;
import io.github.coco.i18n.CocoMessageService;
import io.github.coco.logging.access.CocoAccessLog;
import io.github.coco.logging.access.CocoAccessLogRecorder;
import io.github.coco.logging.access.Slf4jCocoAccessLogRecorder;
import io.github.coco.spring.boot.autoconfigure.logging.CocoLoggingAutoConfiguration;
import io.github.coco.logging.CocoLogLevel;
import io.github.coco.logging.CocoLogRecord;
import io.github.coco.logging.CocoLogSink;
import io.github.coco.audit.accesslog.CocoAccessLogAuditRecorder;
import io.github.coco.audit.core.CocoAuditEvent;
import io.github.coco.audit.core.CocoAuditErrorHandler;
import io.github.coco.audit.core.CocoAuditFailurePolicy;
import io.github.coco.audit.core.CocoAuditFormatter;
import io.github.coco.audit.core.CocoAuditPublisher;
import io.github.coco.audit.core.CocoAuditRecorder;
import io.github.coco.audit.core.CompositeCocoAuditPublisher;
import io.github.coco.audit.core.DefaultCocoAuditFormatter;
import io.github.coco.audit.core.LoggingCocoAuditRecorder;
import io.github.coco.audit.core.PolicyCocoAuditErrorHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Coco 审计功能自动配置测试。
 * <p>
 * 验证审计功能模块可以注册消息资源、审计记录器 SPI，以及访问日志到审计事件的适配器。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-audit}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoAuditAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoI18nAutoConfiguration.class,
                    CocoAuditAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    private final ApplicationContextRunner loggingContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoI18nAutoConfiguration.class,
                    CocoLoggingAutoConfiguration.class,
                    CocoAuditAutoConfiguration.class))
            .withUserConfiguration(CapturingLoggingConfiguration.class)
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    @Test
    void registersAuditMessageBundle() {
        this.contextRunner.run(context -> {
            CocoMessageService messageService = context.getBean(CocoMessageService.class);

            assertTrue(context.containsBean("cocoAuditMessageBundleRegistrar"));
            assertEquals("Coco 审计功能消息资源已就绪。", messageService.getMessage("coco.feature.audit.ready"));
        });
    }

    @Test
    void createsDefaultAuditRecorderAndAccessLogAdapter() {
        this.loggingContextRunner.run(context -> {
            assertThat(context).hasSingleBean(CocoAuditFormatter.class);
            assertThat(context.getBean(CocoAuditFormatter.class)).isInstanceOf(DefaultCocoAuditFormatter.class);
            assertThat(context).hasSingleBean(CocoAuditRecorder.class);
            assertThat(context.getBean(CocoAuditRecorder.class)).isInstanceOf(LoggingCocoAuditRecorder.class);
            assertThat(context).hasSingleBean(CocoAuditErrorHandler.class);
            assertThat(context).hasSingleBean(CocoAuditPublisher.class);
            assertThat(context.getBean(CocoAuditPublisher.class)).isInstanceOf(CompositeCocoAuditPublisher.class);
            assertThat(context).hasBean("cocoAuditLogHandleRegistrar");
            assertThat(context).hasBean("cocoAccessLogAuditRecorder");
            assertThat(context.getBean("cocoAccessLogAuditRecorder")).isInstanceOf(CocoAccessLogAuditRecorder.class);
        });
    }

    @Test
    void writesDefaultAuditRecordThroughCocoLogSink() {
        this.loggingContextRunner
                .withPropertyValues(
                        "coco.audit.logging.logger-name=example.audit",
                        "coco.audit.logging.level=WARN")
                .run(context -> {
                    CocoAuditPublisher publisher = context.getBean(CocoAuditPublisher.class);
                    CapturingCocoLogSink sink = context.getBean(CapturingCocoLogSink.class);

                    publisher.publish(CocoAuditEvent.builder("business-operation")
                            .action("update")
                            .resourceType("order")
                            .resourceId("1001")
                            .traceId("trace-log")
                            .occurredAt(Instant.parse("2026-07-10T01:02:03Z"))
                            .attribute("region", "cn")
                            .attribute("attempt", 2)
                            .build());

                    CocoLogRecord record = sink.latest.get();
                    assertThat(record).isNotNull();
                    assertThat(record.handle().name()).isEqualTo(LoggingCocoAuditRecorder.LOG_HANDLE);
                    assertThat(record.handle().loggerName()).isEqualTo("example.audit");
                    assertThat(record.handle().defaultLevel()).isEqualTo(CocoLogLevel.WARN);
                    assertThat(record.level()).isEqualTo(CocoLogLevel.WARN);
                    assertThat(record.failure()).isEmpty();
                    assertThat(record.message()).isEqualTo("{\"type\":\"business-operation\",\"action\":\"update\","
                            + "\"resourceType\":\"order\",\"resourceId\":\"1001\",\"traceId\":\"trace-log\","
                            + "\"actor\":null,\"tenantId\":null,\"success\":true,"
                            + "\"occurredAt\":\"2026-07-10T01:02:03Z\","
                            + "\"attributes\":{\"attempt\":2,\"region\":\"cn\"}}");
                });
    }

    @Test
    void usesCustomFormatterWithDefaultLoggingRecorder() {
        this.loggingContextRunner
                .withUserConfiguration(CustomFormatterConfiguration.class)
                .run(context -> {
                    CocoAuditPublisher publisher = context.getBean(CocoAuditPublisher.class);
                    CapturingCocoLogSink sink = context.getBean(CapturingCocoLogSink.class);

                    publisher.publish(CocoAuditEvent.builder("custom-format").build());

                    assertThat(context.getBeansOfType(CocoAuditFormatter.class))
                            .containsOnlyKeys("customAuditFormatter");
                    assertThat(sink.latest.get().message()).isEqualTo("custom:custom-format");
                });
    }

    @Test
    void publishesAuditEventToMultipleRecordersAndIgnoresFailuresByDefault() {
        AtomicInteger firstRecorderCount = new AtomicInteger();
        AtomicInteger secondRecorderCount = new AtomicInteger();
        CocoAuditRecorder failingRecorder = event -> {
            throw new IllegalStateException("audit failed");
        };
        CocoAuditPublisher publisher = new CompositeCocoAuditPublisher(List.of(
                failingRecorder,
                event -> firstRecorderCount.incrementAndGet(),
                event -> secondRecorderCount.incrementAndGet()),
                new PolicyCocoAuditErrorHandler(CocoAuditFailurePolicy.IGNORE));

        publisher.publish(CocoAuditEvent.builder("test").build());

        assertThat(firstRecorderCount).hasValue(1);
        assertThat(secondRecorderCount).hasValue(1);
    }

    @Test
    void throwsAuditRecorderFailureWhenFailurePolicyIsThrow() {
        AtomicInteger nextRecorderCount = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("audit failed");
        CocoAuditRecorder failingRecorder = event -> {
            throw failure;
        };
        CocoAuditPublisher publisher = new CompositeCocoAuditPublisher(List.of(
                failingRecorder,
                event -> nextRecorderCount.incrementAndGet()),
                new PolicyCocoAuditErrorHandler(CocoAuditFailurePolicy.THROW));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> publisher.publish(CocoAuditEvent.builder("test").build()));

        assertThat(thrown).isSameAs(failure);
        assertThat(nextRecorderCount).hasValue(0);
    }

    @Test
    void convertsAccessLogToAuditEvent() {
        this.contextRunner
                .withUserConfiguration(CapturingAuditConfiguration.class)
                .run(context -> {
                    CapturingCocoAuditRecorder auditRecorder = context.getBean(CapturingCocoAuditRecorder.class);
                    CocoAccessLogRecorder accessLogRecorder = context.getBean("cocoAccessLogAuditRecorder",
                            CocoAccessLogRecorder.class);

                    accessLogRecorder.record(CocoAccessLog.of("trace-audit", "post", "/orders", 201, 32L,
                            true, null, "127.0.0.1", "JUnit", "sku=COCO", null));

                    CocoAuditEvent event = auditRecorder.latest.get();
                    assertThat(event.type()).isEqualTo(CocoAccessLogAuditRecorder.EVENT_TYPE);
                    assertThat(event.traceId()).contains("trace-audit");
                    assertThat(event.action()).contains("POST");
                    assertThat(event.resourceId()).contains("/orders");
                    assertThat(event.attributes()).containsEntry("status", 201);
                    assertThat(event.attributes()).containsEntry("clientIp", "127.0.0.1");
                });
    }

    @Test
    void autoConfiguredPublisherPublishesToAllCustomRecorders() {
        this.loggingContextRunner
                .withUserConfiguration(MultipleAuditRecorderConfiguration.class)
                .run(context -> {
                    CocoAuditPublisher auditPublisher = context.getBean(CocoAuditPublisher.class);
                    CapturingCocoAuditRecorder firstRecorder = context.getBean("firstAuditRecorder",
                            CapturingCocoAuditRecorder.class);
                    CapturingCocoAuditRecorder secondRecorder = context.getBean("secondAuditRecorder",
                            CapturingCocoAuditRecorder.class);

                    auditPublisher.publish(CocoAuditEvent.builder("business-operation")
                            .traceId("trace-e2e")
                            .resourceType("order")
                            .resourceId("1001")
                            .build());

                    assertThat(firstRecorder.events).hasSize(1);
                    assertThat(secondRecorder.events).hasSize(1);
                    assertThat(firstRecorder.latest.get().traceId()).contains("trace-e2e");
                    assertThat(secondRecorder.latest.get().resourceId()).contains("1001");
                    assertThat(context.getBeansOfType(CocoAuditRecorder.class))
                            .containsOnlyKeys("firstAuditRecorder", "secondAuditRecorder");
                    assertThat(context).doesNotHaveBean("cocoAuditRecorder");
                });
    }

    @Test
    void accessLogAuditAdapterCoexistsWithCommonLoggingAccessLogRecorder() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        CocoI18nAutoConfiguration.class,
                        CocoLoggingAutoConfiguration.class,
                        CocoAuditAutoConfiguration.class))
                .withPropertyValues("coco.common.i18n.basename=coco-messages")
                .run(context -> {
                    assertThat(context.getBeansOfType(CocoAccessLogRecorder.class))
                            .containsKeys("cocoAccessLogRecorder", "cocoAccessLogAuditRecorder");
                    assertThat(context.getBean("cocoAccessLogRecorder"))
                            .isInstanceOf(Slf4jCocoAccessLogRecorder.class);
                    assertThat(context.getBean("cocoAccessLogAuditRecorder"))
                            .isInstanceOf(CocoAccessLogAuditRecorder.class);
                });
    }

    @Test
    void disablesAccessLogAuditAdapter() {
        this.loggingContextRunner
                .withPropertyValues("coco.audit.access-log.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoAuditRecorder.class);
                    assertThat(context).doesNotHaveBean("cocoAccessLogAuditRecorder");
                });
    }

    @Test
    void disablesDefaultLoggingRecorder() {
        this.loggingContextRunner
                .withPropertyValues("coco.audit.logging.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoAuditFormatter.class);
                    assertThat(context).doesNotHaveBean(CocoAuditRecorder.class);
                    assertThat(context).doesNotHaveBean(CocoAuditPublisher.class);
                    assertThat(context).doesNotHaveBean("cocoAuditLogHandleRegistrar");
                    assertThat(context).doesNotHaveBean("cocoAccessLogAuditRecorder");
                });
    }

    @Test
    void keepsCustomRecorderPipelineWhenDefaultLoggingIsDisabled() {
        this.loggingContextRunner
                .withUserConfiguration(CapturingAuditConfiguration.class)
                .withPropertyValues("coco.audit.logging.enabled=false")
                .run(context -> {
                    CocoAuditPublisher publisher = context.getBean(CocoAuditPublisher.class);
                    CapturingCocoAuditRecorder recorder = context.getBean(CapturingCocoAuditRecorder.class);
                    CapturingCocoLogSink sink = context.getBean(CapturingCocoLogSink.class);

                    publisher.publish(CocoAuditEvent.builder("custom-recorder").build());

                    assertThat(recorder.events).hasSize(1);
                    assertThat(sink.records).isEmpty();
                    assertThat(context).doesNotHaveBean("cocoAuditLogHandleRegistrar");
                    assertThat(context).hasBean("cocoAccessLogAuditRecorder");
                });
    }

    @Test
    void keepsAuditPipelineButSkipsLoggingWhenLevelIsOff() {
        this.loggingContextRunner
                .withPropertyValues("coco.audit.logging.level=OFF")
                .run(context -> {
                    CocoAuditPublisher publisher = context.getBean(CocoAuditPublisher.class);
                    CapturingCocoLogSink sink = context.getBean(CapturingCocoLogSink.class);

                    publisher.publish(CocoAuditEvent.builder("off-level").build());

                    assertThat(context).hasSingleBean(CocoAuditRecorder.class);
                    assertThat(context).hasSingleBean(CocoAuditPublisher.class);
                    assertThat(sink.records).isEmpty();
                });
    }

    @Test
    void disablesAuditInfrastructure() {
        this.loggingContextRunner
                .withPropertyValues("coco.audit.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CocoAuditFormatter.class);
                    assertThat(context).doesNotHaveBean(CocoAuditRecorder.class);
                    assertThat(context).doesNotHaveBean(CocoAuditErrorHandler.class);
                    assertThat(context).doesNotHaveBean(CocoAuditPublisher.class);
                    assertThat(context).doesNotHaveBean("cocoAuditLogHandleRegistrar");
                    assertThat(context).doesNotHaveBean("cocoAccessLogAuditRecorder");
                });
    }

    @Test
    void disablesAccessLogAuditAdapterWhenCustomAuditRecorderExists() {
        this.contextRunner
                .withUserConfiguration(CapturingAuditConfiguration.class)
                .withPropertyValues("coco.audit.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoAuditRecorder.class);
                    assertThat(context).doesNotHaveBean("cocoAccessLogAuditRecorder");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CapturingAuditConfiguration {

        @Bean
        CapturingCocoAuditRecorder capturingCocoAuditRecorder() {
            return new CapturingCocoAuditRecorder();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MultipleAuditRecorderConfiguration {

        @Bean
        CapturingCocoAuditRecorder firstAuditRecorder() {
            return new CapturingCocoAuditRecorder();
        }

        @Bean
        CapturingCocoAuditRecorder secondAuditRecorder() {
            return new CapturingCocoAuditRecorder();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CapturingLoggingConfiguration {

        @Bean
        CapturingCocoLogSink capturingCocoLogSink() {
            return new CapturingCocoLogSink();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomFormatterConfiguration {

        @Bean
        CocoAuditFormatter customAuditFormatter() {
            return event -> "custom:" + event.type();
        }
    }

    static class CapturingCocoAuditRecorder implements CocoAuditRecorder {

        private final AtomicReference<CocoAuditEvent> latest = new AtomicReference<>();

        private final List<CocoAuditEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void record(CocoAuditEvent event) {
            this.events.add(event);
            this.latest.set(event);
        }
    }

    static class CapturingCocoLogSink implements CocoLogSink {

        private final AtomicReference<CocoLogRecord> latest = new AtomicReference<>();

        private final List<CocoLogRecord> records = new CopyOnWriteArrayList<>();

        @Override
        public void log(CocoLogRecord record) {
            this.records.add(record);
            this.latest.set(record);
        }
    }
}
