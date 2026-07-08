package io.github.coco.feature.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.common.i18n.api.CocoMessageService;
import io.github.coco.common.logging.access.CocoAccessLog;
import io.github.coco.common.logging.access.CocoAccessLogRecorder;
import io.github.coco.common.logging.access.Slf4jCocoAccessLogRecorder;
import io.github.coco.common.logging.autoconfigure.CocoCommonLoggingAutoConfiguration;
import io.github.coco.feature.audit.accesslog.CocoAccessLogAuditRecorder;
import io.github.coco.feature.audit.core.CocoAuditEvent;
import io.github.coco.feature.audit.core.CocoAuditRecorder;
import io.github.coco.feature.audit.core.NoOpCocoAuditRecorder;
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
 *   <li>模块：{@code coco-feature-audit}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoAuditAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoAuditAutoConfiguration.class))
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
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CocoAuditRecorder.class);
            assertThat(context.getBean(CocoAuditRecorder.class)).isInstanceOf(NoOpCocoAuditRecorder.class);
            assertThat(context).hasBean("cocoAccessLogAuditRecorder");
            assertThat(context.getBean("cocoAccessLogAuditRecorder")).isInstanceOf(CocoAccessLogAuditRecorder.class);
        });
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
    void accessLogAuditAdapterCoexistsWithCommonLoggingAccessLogRecorder() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        CocoCommonAutoConfiguration.class,
                        CocoCommonLoggingAutoConfiguration.class,
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
        this.contextRunner
                .withPropertyValues("coco.audit.access-log.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoAuditRecorder.class);
                    assertThat(context).doesNotHaveBean("cocoAccessLogAuditRecorder");
                });
    }

    @Test
    void disablesAuditInfrastructure() {
        this.contextRunner
                .withPropertyValues("coco.audit.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CocoAuditRecorder.class);
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

    static class CapturingCocoAuditRecorder implements CocoAuditRecorder {

        private final AtomicReference<CocoAuditEvent> latest = new AtomicReference<>();

        @Override
        public void record(CocoAuditEvent event) {
            this.latest.set(event);
        }
    }
}
