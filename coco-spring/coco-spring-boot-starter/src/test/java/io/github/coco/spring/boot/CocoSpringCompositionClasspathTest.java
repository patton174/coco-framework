package io.github.coco.spring.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * Coco Spring 组合类路径测试。
 * <p>
 * 验证单 starter 和兼容 facade 同时存在时，迁移后的自动配置和早期扩展点仍只注册一次。
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
 * @since 2.0.0
 */
class CocoSpringCompositionClasspathTest {

    private static final String AUTO_CONFIGURATION_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private static final List<String> MOVED_AUTO_CONFIGURATIONS = List.of(
            "io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration",
            "io.github.coco.common.logging.autoconfigure.CocoCommonLoggingAutoConfiguration",
            "io.github.coco.feature.registry.CocoFeatureRegistryAutoConfiguration",
            "io.github.coco.config.CocoConfigAutoConfiguration",
            "io.github.coco.feature.runtime.autoconfigure.CocoFeatureRuntimeAutoConfiguration");

    private static final Map<String, String> MOVED_FACTORIES = Map.of(
            "org.springframework.boot.SpringApplicationRunListener",
            "io.github.coco.spring.boot.banner.CocoSpringApplicationRunListener",
            "org.springframework.boot.EnvironmentPostProcessor",
            "io.github.coco.spring.boot.logging.CocoLoggingEnvironmentPostProcessor",
            "org.springframework.boot.autoconfigure.AutoConfigurationImportFilter",
            "io.github.coco.feature.runtime.autoconfigure.CocoFeatureAutoConfigurationImportFilter");

    @Test
    void registersMovedAutoConfigurationsExactlyOnce() throws IOException {
        for (String autoConfiguration : MOVED_AUTO_CONFIGURATIONS) {
            assertEquals(1, countImport(autoConfiguration), autoConfiguration);
        }
    }

    @Test
    void registersMovedSpringFactoriesExactlyOnce() throws IOException {
        for (Map.Entry<String, String> factory : MOVED_FACTORIES.entrySet()) {
            assertEquals(1, countFactory(factory.getKey(), factory.getValue()), factory.getValue());
        }
    }

    private long countImport(String expected) throws IOException {
        long count = 0;
        for (URL resource : resources(AUTO_CONFIGURATION_IMPORTS)) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
                count += reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .filter(expected::equals)
                        .count();
            }
        }
        return count;
    }

    private long countFactory(String key, String expected) throws IOException {
        long count = 0;
        for (URL resource : resources("META-INF/spring.factories")) {
            Properties factories = new Properties();
            try (InputStream input = resource.openStream()) {
                factories.load(input);
            }
            count += Arrays.stream(factories.getProperty(key, "").split(","))
                    .map(String::trim)
                    .filter(expected::equals)
                    .count();
        }
        return count;
    }

    private List<URL> resources(String name) throws IOException {
        return Collections.list(getClass().getClassLoader().getResources(name));
    }
}
