package io.github.coco.spring.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.common.exception.CocoCommonErrorCode;
import io.github.coco.common.exception.CocoException;
import io.github.coco.common.i18n.api.CocoMessageService;
import io.github.coco.common.logging.autoconfigure.CocoCommonLoggingAutoConfiguration;
import io.github.coco.common.logging.core.CocoLoggingProperties;
import io.github.coco.common.trace.CocoTraceContext;
import io.github.coco.spring.boot.banner.CocoBannerProperties;
import io.github.coco.spring.boot.banner.CocoStartupBanner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Coco Spring Boot Starter 接入测试。
 * <p>
 * 验证业务项目通过 starter 单依赖可以获得自动配置、通用上下文、异常契约和日志配置。
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
                    CocoCommonLoggingAutoConfiguration.class,
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

    @Test
    void createsStartupBannerByDefault() {
        this.contextRunner.run(context -> {
            assertTrue(context.containsBean("cocoStartupBanner"));
            assertTrue(context.getBean(CocoBannerProperties.class) instanceof CocoBannerProperties);
            assertEquals("coco spring", context.getBean(CocoBannerProperties.class).getTitle());
        });
    }

    @Test
    void providesLoggingPropertiesByDefault() {
        this.contextRunner.run(context -> {
            CocoLoggingProperties loggingProperties = context.getBean(CocoLoggingProperties.class);

            assertTrue(loggingProperties.isEnabled());
            assertTrue(loggingProperties.isQuietSpring());
            assertTrue(loggingProperties.getConsolePattern().contains("%clr(COCO){cyan}"));
            assertTrue(loggingProperties.getConsolePattern().contains("%clr(%logger{32}){magenta}"));
        });
    }

    @Test
    void doesNotRegisterStartupBannerLogger() {
        this.contextRunner
                .withPropertyValues("coco.banner.enabled=false")
                .run(context -> {
                    assertTrue(context.containsBean("cocoStartupBanner"));
                    assertTrue(context.getBean(CocoBannerProperties.class) instanceof CocoBannerProperties);
                    assertFalse(context.containsBean("cocoStartupBannerLogger"));
                });
    }

    @Test
    void rendersStartupBannerWithoutFrame() {
        CocoBannerProperties properties = new CocoBannerProperties();
        CocoStartupBanner banner = new CocoStartupBanner(properties);

        String rendered = banner.render("9.9.9");

        assertTrue(rendered.contains("coco spring"));
        assertTrue(rendered.contains("9.9.9"));
        assertFalse(rendered.contains("╭"));
        assertFalse(rendered.contains("│"));
        assertFalse(rendered.contains("Author"));
        assertFalse(rendered.contains("Repository"));
        assertFalse(rendered.contains(":: Spring Boot ::"));
    }
}
