package io.github.coco.spring.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import io.github.coco.spring.boot.banner.CocoSpringBanner;
import io.github.coco.spring.boot.logging.CocoLoggingEnvironmentPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Coco Spring 应用启动接管测试。
 * <p>
 * 验证 Starter 可以在 Spring Boot 原始 banner 与默认启动日志输出之前接管启动展示和日志默认值。
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
class CocoSpringApplicationRunListenerTest {

    @Test
    void printsCocoSpringBannerInsteadOfSpringBootBanner() {
        CocoSpringBanner banner = new CocoSpringBanner();
        StandardEnvironment environment = new StandardEnvironment();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        banner.printBanner(environment, TestApplication.class,
                new PrintStream(output, true, StandardCharsets.UTF_8));

        String rendered = output.toString(StandardCharsets.UTF_8);
        assertTrue(rendered.contains("╭"));
        assertTrue(rendered.contains("█"));
        assertTrue(rendered.contains("Coco Spring"));
        assertFalse(rendered.contains("Author"));
        assertFalse(rendered.contains("Repository"));
        assertFalse(rendered.contains("https://github.com/patton174/coco-framework"));
        assertFalse(rendered.contains(":: Spring Boot ::"));
        assertFalse(rendered.contains("____          _"));
    }

    @Test
    void appliesCocoLoggingDefaultsWithoutOverridingApplicationProperties() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("applicationConfig", java.util.Map.of(
                "logging.level.org.springframework", "ERROR")));
        CocoLoggingEnvironmentPostProcessor processor = new CocoLoggingEnvironmentPostProcessor();

        processor.postProcessEnvironment(environment, new SpringApplication(TestApplication.class));

        assertEquals("false", environment.getProperty("spring.main.log-startup-info"));
        assertEquals("ERROR", environment.getProperty("logging.level.org.springframework"));
        assertEquals("WARN", environment.getProperty("logging.level.org.apache.catalina"));
        assertTrue(environment.getProperty("logging.pattern.console").contains("COCO"));
        assertTrue(environment.getProperty("logging.pattern.console").contains("%clr"));
    }

    @Test
    void skipsCocoLoggingDefaultsWhenDisabled() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("applicationConfig", java.util.Map.of(
                "coco.logging.enabled", "false")));
        CocoLoggingEnvironmentPostProcessor processor = new CocoLoggingEnvironmentPostProcessor();

        processor.postProcessEnvironment(environment, new SpringApplication(TestApplication.class));

        assertEquals(null, environment.getProperty("spring.main.log-startup-info"));
        assertEquals(null, environment.getProperty("logging.pattern.console"));
    }

    private static final class TestApplication {
    }
}
