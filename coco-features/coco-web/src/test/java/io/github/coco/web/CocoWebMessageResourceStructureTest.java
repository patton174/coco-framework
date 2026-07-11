package io.github.coco.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import io.github.coco.spring.boot.autoconfigure.i18n.CocoI18nAutoConfiguration;
import io.github.coco.i18n.CocoMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Coco Web 消息资源结构测试。
 * <p>
 * 验证 Web 功能模块内置国际化资源在默认、英文和中文语言包中保持相同的消息编码集合。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoWebMessageResourceStructureTest {

    private static final Set<String> WEB_MESSAGE_CODES = Set.of(
            "coco.feature.web.ready",
            "coco.web.response.success",
            "coco.web.error.bad-request",
            "coco.web.error.method-not-allowed",
            "coco.web.request-body.payload-too-large",
            "coco.web.signature.missing-signature",
            "coco.web.signature.missing-app-id",
            "coco.web.signature.missing-algorithm",
            "coco.web.signature.missing-timestamp",
            "coco.web.signature.invalid-timestamp",
            "coco.web.signature.expired",
            "coco.web.signature.secret-not-found",
            "coco.web.signature.invalid",
            "coco.web.signature.missing-body-hash",
            "coco.web.encryption.missing-encrypted",
            "coco.web.encryption.missing-app-id",
            "coco.web.encryption.missing-iv",
            "coco.web.encryption.missing-algorithm",
            "coco.web.encryption.missing-payload",
            "coco.web.encryption.key-not-found",
            "coco.web.encryption.malformed-request",
            "coco.web.encryption.decrypt-failed",
            "coco.web.encryption.body-read-failed",
            "coco.web.replay.missing-app-id",
            "coco.web.replay.missing-timestamp",
            "coco.web.replay.missing-nonce",
            "coco.web.replay.invalid-timestamp",
            "coco.web.replay.expired",
            "coco.web.replay.detected");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoI18nAutoConfiguration.class,
                    CocoWebAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    @Test
    void webMessageBundlesHaveIdenticalKeySets() throws IOException {
        Map<String, Properties> bundles = Map.of(
                "default", loadBundle("coco-web-messages.properties"),
                "en-US", loadBundle("coco-web-messages_en_US.properties"),
                "zh-CN", loadBundle("coco-web-messages_zh_CN.properties"));

        bundles.forEach((locale, bundle) -> {
            assertEquals(WEB_MESSAGE_CODES, bundle.stringPropertyNames(), locale + " message keys");
            WEB_MESSAGE_CODES.forEach(code ->
                    assertFalse(bundle.getProperty(code).isBlank(), locale + " message value: " + code));
        });
    }

    @Test
    void allWebExceptionMessageCodesResolveForSupportedLocales() {
        this.contextRunner.run(context -> {
            CocoMessageService messageService = context.getBean(CocoMessageService.class);

            WEB_MESSAGE_CODES.stream()
                    .filter(code -> code.startsWith("coco.web."))
                    .forEach(code -> {
                        String english = messageService.getMessage(code, Locale.US);
                        String chinese = messageService.getMessage(code, Locale.SIMPLIFIED_CHINESE);
                        assertFalse(english.isBlank(), code + " en-US message");
                        assertFalse(chinese.isBlank(), code + " zh-CN message");
                        assertFalse(code.equals(english), code + " en-US fallback");
                        assertFalse(code.equals(chinese), code + " zh-CN fallback");
                    });
            assertEquals("Request signature is missing.",
                    messageService.getMessage("coco.web.signature.missing-signature", Locale.US));
            assertEquals("请求签名缺失。",
                    messageService.getMessage("coco.web.signature.missing-signature",
                            Locale.SIMPLIFIED_CHINESE));
            assertEquals("Request body exceeds the maximum allowed size.",
                    messageService.getMessage("coco.web.request-body.payload-too-large", Locale.US));
            assertEquals("请求体超过允许的最大大小。",
                    messageService.getMessage("coco.web.request-body.payload-too-large",
                            Locale.SIMPLIFIED_CHINESE));
            assertEquals("Invalid request.",
                    messageService.getMessage("coco.web.error.bad-request", Locale.US));
            assertEquals("请求参数不合法。",
                    messageService.getMessage("coco.web.error.bad-request", Locale.SIMPLIFIED_CHINESE));
            assertEquals("Request replay has been detected.",
                    messageService.getMessage("coco.web.replay.detected", Locale.US));
            assertEquals("检测到重复请求。",
                    messageService.getMessage("coco.web.replay.detected", Locale.SIMPLIFIED_CHINESE));
        });
    }

    private Properties loadBundle(String name) throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(name);
        assertNotNull(inputStream, name + " resource");
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            return properties;
        }
    }
}
