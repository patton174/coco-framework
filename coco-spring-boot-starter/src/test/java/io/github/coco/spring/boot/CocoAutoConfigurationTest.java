package io.github.coco.spring.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.common.exception.CocoCommonErrorCode;
import io.github.coco.common.exception.CocoException;
import io.github.coco.common.i18n.CocoMessageService;
import io.github.coco.common.trace.CocoTraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Coco Spring Boot Starter 自动配置测试。
 * <p>
 * 验证单依赖入口可以通过 Coco 国际化基础设施注册自己的消息资源。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-spring-boot-starter}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    @AfterEach
    void clearTraceContext() {
        CocoTraceContext.clear();
    }

    @Test
    void registersStarterMessageBundle() {
        this.contextRunner.run(context -> {
            CocoMessageService messageService = context.getBean(CocoMessageService.class);

            assertTrue(context.containsBean("cocoSpringBootStarterMessageBundleRegistrar"));
            assertEquals("Coco Spring Boot Starter 消息资源已就绪。",
                    messageService.getMessage("coco.spring.boot.starter.ready"));
        });
    }

    @Test
    void providesCommonTraceContextFromStarterDependency() {
        String traceId = CocoTraceContext.getOrCreateTraceId();

        assertEquals(traceId, CocoTraceContext.currentTraceId().orElseThrow());
    }

    @Test
    void providesCommonExceptionContractsFromStarterDependency() {
        CocoException exception = CocoCommonErrorCode.INVALID_ARGUMENT.exception("name");

        assertEquals("coco.error.invalid-argument", exception.code());
        assertEquals("coco.error.invalid-argument", exception.defaultMessage());
    }
}
