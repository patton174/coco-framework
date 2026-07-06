package io.github.coco.spring.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.common.i18n.api.CocoMessageService;
import io.github.coco.common.logging.autoconfigure.CocoCommonLoggingAutoConfiguration;
import io.github.coco.common.logging.core.CocoLoggingProperties;
import io.github.coco.spring.boot.banner.CocoBannerProperties;
import io.github.coco.spring.boot.banner.CocoSpringBanner;
import io.github.coco.spring.boot.banner.CocoStartupBanner;
import io.github.coco.spring.boot.logging.CocoLoggingEnvironmentPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Coco Spring Boot 自动配置测试。
 * <p>
 * 验证自动配置模块负责 Spring Boot 接入、启动 banner 和 Coco 默认日志环境。
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
class CocoSpringAutoconfigureTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoCommonLoggingAutoConfiguration.class,
                    CocoAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    @Test
    void registersAutoconfigureMessageBundleAndProperties() {
        this.contextRunner.run(context -> {
            CocoMessageService messageService = context.getBean(CocoMessageService.class);

            assertTrue(context.containsBean("cocoSpringBootStarterMessageBundleRegistrar"));
            assertEquals("Coco Spring Boot starter message bundle is ready.",
                    messageService.getMessage("coco.spring.boot.starter.ready", Locale.US));
            assertEquals("coco spring", context.getBean(CocoBannerProperties.class).getTitle());
            assertTrue(context.getBean(CocoLoggingProperties.class).isEnabled());
        });
    }

    @Test
    void rendersLeanCocoSpringBannerWithoutFrame() {
        CocoBannerProperties properties = new CocoBannerProperties();
        CocoStartupBanner banner = new CocoStartupBanner(properties);

        String rendered = banner.render("9.9.9", "4.1.0");

        assertEquals(String.join(System.lineSeparator(),
                "coco spring",
                "  fast web framework",
                "",
                "  version     9.9.9",
                "  spring boot 4.1.0"), rendered);
        assertFalse(rendered.contains("::"));
        assertFalse(rendered.contains("+"));
        assertFalse(rendered.contains("|"));
        assertFalse(rendered.contains("Author"));
        assertFalse(rendered.contains("Repository"));
        assertFalse(rendered.contains(":: Spring Boot ::"));
    }

    @Test
    void printsCocoSpringBannerInsteadOfSpringBootBanner() {
        CocoSpringBanner banner = new CocoSpringBanner();
        StandardEnvironment environment = new StandardEnvironment();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        banner.printBanner(environment, TestApplication.class,
                new PrintStream(output, true, StandardCharsets.UTF_8));

        String rendered = output.toString(StandardCharsets.UTF_8);
        assertTrue(rendered.contains("coco spring"));
        assertTrue(rendered.contains("  fast web framework"));
        assertTrue(rendered.contains("  version     "));
        assertTrue(rendered.contains("  spring boot "));
        assertFalse(rendered.contains("::"));
        assertFalse(rendered.contains("+"));
        assertFalse(rendered.contains("|"));
        assertFalse(rendered.contains("____          _"));
    }

    @Test
    void appliesCocoLoggingDefaultsWithoutOverridingApplicationProperties() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("applicationConfig", Map.of(
                "logging.level.org.springframework", "ERROR")));
        CocoLoggingEnvironmentPostProcessor processor = new CocoLoggingEnvironmentPostProcessor();

        processor.postProcessEnvironment(environment, new SpringApplication(TestApplication.class));

        assertEquals("false", environment.getProperty("spring.main.log-startup-info"));
        assertEquals("ERROR", environment.getProperty("logging.level.org.springframework"));
        assertEquals("WARN", environment.getProperty("logging.level.org.apache.catalina"));
        assertTrue(environment.getProperty("logging.pattern.console").contains("%highlight(%-5level)"));
        assertTrue(environment.getProperty("logging.pattern.console").contains("%clr(coco){cyan}"));
        assertTrue(environment.getProperty("logging.pattern.console").contains("%clr(%-10.10logger{0}){magenta}"));
    }

    private static final class TestApplication {
    }
}
