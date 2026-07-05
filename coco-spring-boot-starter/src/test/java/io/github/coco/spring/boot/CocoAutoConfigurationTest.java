package io.github.coco.spring.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.common.exception.CocoCommonErrorCode;
import io.github.coco.common.exception.CocoException;
import io.github.coco.common.i18n.api.CocoMessageService;
import io.github.coco.common.trace.CocoTraceContext;
import io.github.coco.spring.boot.banner.CocoBannerProperties;
import io.github.coco.spring.boot.banner.CocoStartupBanner;
import io.github.coco.spring.boot.logging.CocoLoggingProperties;
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

    @Test
    void createsStartupBannerByDefault() {
        this.contextRunner.run(context -> {
            assertTrue(context.containsBean("cocoStartupBanner"));
            assertTrue(context.getBean(CocoBannerProperties.class) instanceof CocoBannerProperties);
            assertEquals("Coco Spring", context.getBean(CocoBannerProperties.class).getTitle());
        });
    }

    @Test
    void providesLoggingPropertiesByDefault() {
        this.contextRunner.run(context -> {
            CocoLoggingProperties loggingProperties = context.getBean(CocoLoggingProperties.class);

            assertTrue(loggingProperties.isEnabled());
            assertTrue(loggingProperties.isQuietSpring());
            assertTrue(loggingProperties.getConsolePattern().contains("COCO"));
        });
    }

    @Test
    void doesNotRegisterStartupBannerLogger() {
        this.contextRunner
                .withPropertyValues("coco.banner.enabled=false")
                .run(context -> {
                    assertTrue(context.containsBean("cocoStartupBanner"));
                    assertTrue(context.getBean(CocoBannerProperties.class) instanceof CocoBannerProperties);
                    assertEquals(false, context.containsBean("cocoStartupBannerLogger"));
                });
    }

    @Test
    void rendersStartupBannerWithProjectInfo() {
        CocoBannerProperties properties = new CocoBannerProperties();
        CocoStartupBanner banner = new CocoStartupBanner(properties);

        String rendered = banner.render("9.9.9");

        assertTrue(rendered.contains("╭"));
        assertTrue(rendered.contains("█"));
        assertTrue(rendered.contains("Coco Spring"));
        assertTrue(rendered.contains("9.9.9"));
        assertEquals(false, rendered.contains("Author"));
        assertEquals(false, rendered.contains("Repository"));
        assertEquals(false, rendered.contains(":: Spring Boot ::"));
    }
}
