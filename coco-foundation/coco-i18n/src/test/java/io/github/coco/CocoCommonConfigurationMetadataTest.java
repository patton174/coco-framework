package io.github.coco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.coco.i18n.CocoI18nProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * Coco 通用配置元数据测试。
 * <p>
 * 验证框架产物中包含 Spring Boot IDE 可识别的通用基础设施配置提示。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-i18n}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoCommonConfigurationMetadataTest {

    @Test
    void exposesI18nPropertyMetadata() throws IOException {
        InputStream metadata = getClass().getResourceAsStream("/META-INF/spring-configuration-metadata.json");

        assertNotNull(metadata);
        String content = new String(metadata.readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(content.contains("\"name\": \"coco.common.i18n.basename\""));
        assertTrue(content.contains("\"name\": \"coco.common.i18n.default-locale\""));
        assertTrue(content.contains("\"name\": \"coco.common.i18n.supported-languages\""));
        assertTrue(content.contains("\"defaultValue\": []"));
        assertTrue(content.contains("空列表表示不过滤；非空列表表示显式 opt-in 允许列表。"));
        assertTrue(content.contains("匹配遵循 JDK Locale 规范化语义，不执行 IANA 注册表别名扩展；"
                + "BU\\/MM 等已弃用 Preferred-Value 别名保持不同。"));
        assertTrue(content.contains("\"name\": \"coco.common.i18n.fallback-to-system-locale\""));
        assertTrue(content.contains("\"name\": \"coco.common.i18n.use-code-as-default-message\""));
    }

    @Test
    void preservesPublishedLiveI18nConfigurationContract() {
        CocoI18nProperties i18n = new CocoI18nProperties();
        i18n.setBasename(List.of("application-messages"));
        CocoCommonProperties properties = new CocoCommonProperties();
        properties.setI18n(i18n);

        CocoI18nProperties liveProperties = properties.getI18n();
        liveProperties.getBasename().add("late-mutation");
        liveProperties.setDefaultLocale(Locale.US);

        assertSame(i18n, liveProperties);
        assertSame(liveProperties, properties.getI18n());
        assertEquals(List.of("application-messages", "late-mutation"),
                properties.getI18n().getBasename());
        assertEquals(Locale.US, properties.getI18n().getDefaultLocale());

        CocoI18nProperties replacement = new CocoI18nProperties();
        properties.setI18n(replacement);

        assertSame(replacement, properties.getI18n());
    }

    @Test
    void bindsNestedI18nPropertiesThroughJavaBeanAccessors() {
        CocoCommonProperties properties = new CocoCommonProperties();
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "coco.common.i18n.basename[0]", "application-messages",
                "coco.common.i18n.default-locale", "en_US",
                "coco.common.i18n.fallback-to-system-locale", "true",
                "coco.common.i18n.use-code-as-default-message", "false")));

        assertTrue(binder.bind("coco.common", Bindable.ofInstance(properties)).isBound());
        CocoI18nProperties i18n = properties.getI18n();
        assertEquals(List.of("application-messages"), i18n.getBasename());
        assertEquals(Locale.US, i18n.getDefaultLocale());
        assertTrue(i18n.isFallbackToSystemLocale());
        assertEquals(false, i18n.isUseCodeAsDefaultMessage());
    }

    @Test
    void preservesPublishedI18nConfigurationApi() throws ReflectiveOperationException {
        assertEquals(CocoCommonProperties.class, Class.forName("io.github.coco.CocoCommonProperties"));
        assertEquals(CocoI18nProperties.class, Class.forName("io.github.coco.i18n.CocoI18nProperties"));
        assertNotNull(CocoCommonProperties.class.getConstructor());
        assertEquals(CocoI18nProperties.class, CocoCommonProperties.class.getMethod("getI18n").getReturnType());
        assertNotNull(CocoCommonProperties.class.getMethod("setI18n", CocoI18nProperties.class));
        assertNotNull(CocoI18nProperties.class.getConstructor());
        assertEquals(List.class, CocoI18nProperties.class.getMethod("getBasename").getReturnType());
        assertNotNull(CocoI18nProperties.class.getMethod("setBasename", List.class));
    }
}
