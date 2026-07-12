package io.github.coco.spring.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * Coco Spring 组合元数据测试。
 * <p>
 * 验证合并后的自动配置清单和 Spring Boot 早期扩展点只由自动配置模块统一注册。
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
 * @since 2.0.0
 */
class CocoSpringCompositionMetadataTest {

    private static final String AUTO_CONFIGURATION_IMPORTS =
            "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private static final List<String> EXPECTED_AUTO_CONFIGURATIONS = List.of(
            "io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration",
            "io.github.coco.common.logging.autoconfigure.CocoCommonLoggingAutoConfiguration",
            "io.github.coco.feature.registry.CocoFeatureRegistryAutoConfiguration",
            "io.github.coco.config.CocoConfigAutoConfiguration",
            "io.github.coco.feature.runtime.autoconfigure.CocoFeatureRuntimeAutoConfiguration",
            "io.github.coco.spring.boot.CocoAutoConfiguration");

    @Test
    void exposesMergedAutoConfigurationImports() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(AUTO_CONFIGURATION_IMPORTS)) {
            assertNotNull(input);
            List<String> imports = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();

            assertEquals(EXPECTED_AUTO_CONFIGURATIONS, imports);
        }
    }

    @Test
    void exposesMergedSpringFactories() throws IOException {
        Properties factories = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/META-INF/spring.factories")) {
            assertNotNull(input);
            factories.load(input);
        }

        assertEquals("io.github.coco.spring.boot.banner.CocoSpringApplicationRunListener",
                factories.getProperty("org.springframework.boot.SpringApplicationRunListener"));
        assertEquals("io.github.coco.spring.boot.logging.CocoLoggingEnvironmentPostProcessor",
                factories.getProperty("org.springframework.boot.EnvironmentPostProcessor"));
        assertEquals("io.github.coco.feature.runtime.autoconfigure.CocoFeatureAutoConfigurationImportFilter",
                factories.getProperty("org.springframework.boot.autoconfigure.AutoConfigurationImportFilter"));
    }
}
