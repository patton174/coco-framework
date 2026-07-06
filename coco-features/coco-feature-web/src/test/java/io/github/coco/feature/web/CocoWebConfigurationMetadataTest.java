package io.github.coco.feature.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Coco Web 配置元数据测试。
 * <p>
 * 验证 Web 功能模块提供 Spring Boot IDE 可识别的配置提示。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoWebConfigurationMetadataTest {

    @Test
    void exposesTracePropertyMetadata() throws IOException {
        InputStream metadata = getClass().getResourceAsStream("/META-INF/spring-configuration-metadata.json");

        assertNotNull(metadata);
        String content = new String(metadata.readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(content.contains("\"name\": \"coco.web.trace.enabled\""));
        assertTrue(content.contains("\"name\": \"coco.web.trace.header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.trace.mdc-key\""));
        assertTrue(content.contains("\"name\": \"coco.web.trace.response-header-enabled\""));
        assertTrue(content.contains("\"name\": \"coco.web.trace.response-cookie-enabled\""));
        assertTrue(content.contains("\"name\": \"coco.web.trace.cookie-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.trace.cookie-path\""));
        assertTrue(content.contains("\"name\": \"coco.web.trace.cookie-max-age\""));
        assertTrue(content.contains("\"name\": \"coco.web.trace.cookie-http-only\""));
        assertTrue(content.contains("\"name\": \"coco.web.trace.cookie-secure\""));
        assertTrue(content.contains("\"name\": \"coco.web.trace.cookie-same-site\""));
        assertTrue(content.contains("\"name\": \"coco.web.access-log.enabled\""));
        assertTrue(content.contains("\"name\": \"coco.web.access-log.include-parameters\""));
        assertTrue(content.contains("\"name\": \"coco.web.access-log.max-parameter-value-length\""));
        assertTrue(content.contains("\"name\": \"coco.web.access-log.masked-parameter-names\""));
        assertFalse(content.contains("\"name\": \"coco.web.access-log.level\""));
        assertFalse(content.contains("\"name\": \"coco.web.access-log.style\""));
        assertFalse(content.contains("\"name\": \"coco.web.access-log.logger-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.request-body.enabled\""));
        assertTrue(content.contains("\"name\": \"coco.web.request-body.mode\""));
        assertTrue(content.contains("\"name\": \"coco.web.request-body.max-cache-bytes\""));
        assertTrue(content.contains("\"name\": \"coco.web.request-body.cache-methods\""));
        assertTrue(content.contains("\"name\": \"coco.web.request-body.trigger-header-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.request-body.included-content-types\""));
        assertTrue(content.contains("\"name\": \"coco.web.request-body.excluded-content-type-prefixes\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.enabled\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.required\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.timestamp-required\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.timestamp-validation-enabled\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.max-clock-skew-seconds\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.app-id-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.key-id-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.timestamp-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.nonce-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.signature-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.signature-fallback-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.algorithm-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.default-algorithm\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.secrets\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.enabled\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.required\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.encrypted-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.app-id-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.key-id-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.iv-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.algorithm-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.default-algorithm\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.key-encoding\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.iv-encoding\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.payload-encoding\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.gcm-tag-length-bits\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.keys\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.enabled\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.required\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.protect-signed-requests\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.protect-encrypted-requests\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.include-method\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.include-path\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.app-id-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.key-id-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.timestamp-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.nonce-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.ttl-seconds\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.cleanup-interval-seconds\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.client-ip-header-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.include-headers\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.included-header-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.masked-header-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.security-header-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonical-header-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.fingerprint-header-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.max-header-value-length\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.parameter.include-parameters\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.parameter.max-parameter-value-length\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.parameter.masked-parameter-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.version\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-version\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-purpose\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-method\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-path\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-query-string\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-headers\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-parameters\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-body-sha256\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-body-length\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.sort-parameter-values\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.parameter-value-separator\""));
        assertTrue(content.contains("\"name\": \"coco.web.response.metadata-mode\""));
        assertTrue(content.contains("\"name\": \"coco.web.response-wrap.enabled\""));
        assertTrue(content.contains("\"name\": \"coco.web.response-wrap.success-message-code\""));
        assertFalse(content.contains("\"name\": \"coco.web.response-wrap.success-code\""));
    }
}
