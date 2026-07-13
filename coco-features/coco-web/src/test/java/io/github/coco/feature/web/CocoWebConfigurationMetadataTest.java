package io.github.coco.feature.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;
import java.util.stream.Stream;

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
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoWebConfigurationMetadataTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void exposesAutoConfigurationImport() throws IOException {
        InputStream imports = getClass().getResourceAsStream(
                "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");

        assertNotNull(imports);
        String content = new String(imports.readAllBytes(), StandardCharsets.UTF_8);
        List<String> autoConfigurations = content.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("#"))
                .toList();
        assertEquals(List.of(
                CocoWebJdbcReplayAutoConfiguration.class.getName(),
                CocoWebAutoConfiguration.class.getName()), autoConfigurations);
    }

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
        assertTrue(content.contains("\"name\": \"coco.web.trace.max-length\""));
        assertTrue(content.contains("\"name\": \"coco.web.trace.allowed-pattern\""));
        assertTrue(content.contains("\"name\": \"coco.web.access-log.enabled\""));
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
        assertTrue(content.contains("\"name\": \"coco.web.signature.metadata-source\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.app-id-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.app-id-parameter-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.key-id-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.key-id-parameter-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.timestamp-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.timestamp-parameter-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.nonce-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.nonce-parameter-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.signature-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.signature-parameter-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.signature-fallback-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.signature-fallback-parameter-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.algorithm-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.algorithm-parameter-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.default-algorithm\""));
        assertTrue(content.contains("\"name\": \"coco.web.signature.secrets\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.enabled\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.required\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.metadata-source\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.encrypted-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.encrypted-parameter-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.app-id-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.app-id-parameter-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.key-id-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.key-id-parameter-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.iv-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.iv-parameter-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.algorithm-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.algorithm-parameter-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.payload-parameter-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.default-algorithm\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.key-encoding\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.iv-encoding\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.payload-encoding\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.gcm-tag-length-bits\""));
        assertTrue(content.contains("\"name\": \"coco.web.encryption.keys\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.enabled\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.store-type\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.jdbc.table-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.required\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.protect-signed-requests\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.protect-encrypted-requests\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.include-method\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.include-path\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.metadata-source\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.app-id-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.app-id-parameter-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.key-id-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.key-id-parameter-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.timestamp-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.timestamp-parameter-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.nonce-header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.nonce-parameter-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.ttl-seconds\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.max-clock-skew-seconds\""));
        assertTrue(content.contains("\"name\": \"coco.web.replay.cleanup-interval-seconds\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.client-ip-header-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.trusted-proxy-cidrs\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.include-headers\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.included-header-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.masked-header-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.security-header-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonical-header-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonical-cookie-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.fingerprint-header-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.max-header-value-length\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.included-cookie-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.masked-cookie-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.max-cookie-value-length\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.parameter.include-parameters\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.parameter.max-parameter-value-length\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.parameter.masked-parameter-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.parameter.payload.enabled\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.parameter.payload.included-content-types\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.parameter.payload.max-json-depth\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.parameter.payload.max-parameter-count\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.target.proto-header-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.target.host-header-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.target.port-header-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.target.prefix-header-names\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.target.apply-forwarded-prefix\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.version\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-version\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-purpose\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-method\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-path\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-query-string\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-headers\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-cookies\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-browser-fingerprint\""));
        assertTrue(content.contains(
                "\"name\": \"coco.web.context.canonicalization.include-browser-fingerprint-signals\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-parameters\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-parameter-sources\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-body-sha256\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.include-body-length\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.sort-parameter-values\""));
        assertTrue(content.contains("\"name\": \"coco.web.context.canonicalization.parameter-value-separator\""));
        assertTrue(content.contains("\"name\": \"coco.web.response.metadata-mode\""));
        assertTrue(content.contains("\"name\": \"coco.web.response-wrap.enabled\""));
        assertTrue(content.contains("\"name\": \"coco.web.response-wrap.success-message-code\""));
        assertTrue(content.contains("\"name\": \"coco.web.response-wrap.max-body-bytes\""));
        assertFalse(content.contains("\"name\": \"coco.web.response-wrap.success-code\""));
    }

    @Test
    void exposesConfigurationMetadataHints() throws IOException {
        JsonNode metadata = configurationMetadata("/META-INF/additional-spring-configuration-metadata.json");

        assertHintValues(metadata, "coco.web.request-body.mode", "security-headers", "always");
        assertHintDescriptionContains(metadata, "coco.web.request-body.mode", "security-headers",
                "签名、加密、防重放强制匹配规则");
        assertHintValues(metadata, "coco.web.response.metadata-mode", "none", "cookie", "trace", "debug");
        assertHintValues(metadata, "coco.web.context.parameter.payload.included-content-types",
                "application/json", "application/*+json", "application/x-www-form-urlencoded");
        assertHintValues(metadata, "coco.web.context.canonicalization.version", "coco-v2");
        assertHintValues(metadata, "coco.web.signature.metadata-source", "header", "parameter",
                "header-then-parameter", "parameter-then-header");
        assertHintValues(metadata, "coco.web.encryption.metadata-source", "header", "parameter",
                "header-then-parameter", "parameter-then-header");
        assertHintValues(metadata, "coco.web.replay.metadata-source", "header", "parameter",
                "header-then-parameter", "parameter-then-header");
        assertHintValues(metadata, "coco.web.replay.store-type", "in-memory", "jdbc");
        assertAdditionalProperty(metadata, "coco.web.replay.store-type");
        assertAdditionalProperty(metadata, "coco.web.replay.jdbc.table-name");
        assertHintValues(metadata, "coco.web.encryption.key-encoding", "base64", "hex", "utf8", "raw");
        assertHintValues(metadata, "coco.web.encryption.iv-encoding", "base64", "hex", "utf8", "raw");
        assertHintValues(metadata, "coco.web.encryption.payload-encoding", "base64", "hex", "utf8", "raw");
        assertAdditionalProperty(metadata, "coco.web.signature.matcher.required[].methods");
        assertAdditionalProperty(metadata, "coco.web.signature.matcher.required[].path-patterns");
        assertAdditionalProperty(metadata, "coco.web.signature.matcher.ignored[].methods");
        assertAdditionalProperty(metadata, "coco.web.signature.matcher.ignored[].path-patterns");
        assertAdditionalProperty(metadata, "coco.web.encryption.matcher.required[].methods");
        assertAdditionalProperty(metadata, "coco.web.encryption.matcher.required[].path-patterns");
        assertAdditionalProperty(metadata, "coco.web.encryption.matcher.ignored[].methods");
        assertAdditionalProperty(metadata, "coco.web.encryption.matcher.ignored[].path-patterns");
        assertAdditionalProperty(metadata, "coco.web.replay.matcher.required[].methods");
        assertAdditionalProperty(metadata, "coco.web.replay.matcher.required[].path-patterns");
        assertAdditionalProperty(metadata, "coco.web.replay.matcher.ignored[].methods");
        assertAdditionalProperty(metadata, "coco.web.replay.matcher.ignored[].path-patterns");
    }

    @Test
    void nonEmptyWebPackagesDeclarePackageInfo() throws IOException {
        Path root = Path.of("src/main/java/io/github/coco/feature/web");

        assertTrue(Files.isDirectory(root));
        List<Path> missingPackageInfo;
        try (Stream<Path> directories = Files.walk(root)) {
            missingPackageInfo = directories
                    .filter(Files::isDirectory)
                    .filter(CocoWebConfigurationMetadataTest::containsDirectJavaSource)
                    .filter(directory -> !Files.isRegularFile(directory.resolve("package-info.java")))
                    .toList();
        }

        assertEquals(List.of(), missingPackageInfo);
    }

    private JsonNode configurationMetadata() throws IOException {
        return configurationMetadata("/META-INF/spring-configuration-metadata.json");
    }

    private JsonNode configurationMetadata(String resourceName) throws IOException {
        InputStream metadata = getClass().getResourceAsStream(resourceName);
        assertNotNull(metadata);
        return OBJECT_MAPPER.readTree(metadata);
    }

    private static void assertHintValues(JsonNode metadata, String name, String... expectedValues) {
        JsonNode hint = findNamedNode(metadata.path("hints"), name);
        assertNotNull(hint, "missing hint: " + name);
        List<String> values = StreamSupport.stream(hint.path("values").spliterator(), false)
                .map(value -> value.path("value").asText())
                .toList();
        assertEquals(List.of(expectedValues), values);
    }

    private static void assertHintDescriptionContains(JsonNode metadata, String name,
            String value, String expectedDescriptionPart) {
        JsonNode hint = findNamedNode(metadata.path("hints"), name);
        assertNotNull(hint, "missing hint: " + name);
        JsonNode valueNode = StreamSupport.stream(hint.path("values").spliterator(), false)
                .filter(candidate -> value.equals(candidate.path("value").asText()))
                .findFirst()
                .orElse(null);
        assertNotNull(valueNode, "missing hint value: " + name + "=" + value);
        assertTrue(valueNode.path("description").asText().contains(expectedDescriptionPart));
    }

    private static void assertAdditionalProperty(JsonNode metadata, String name) {
        JsonNode property = findNamedNode(metadata.path("properties"), name);
        assertNotNull(property, "missing property: " + name);
        assertFalse(property.path("description").asText().isBlank());
    }

    private static JsonNode findNamedNode(JsonNode nodes, String name) {
        for (JsonNode node : nodes) {
            if (name.equals(node.path("name").asText())) {
                return node;
            }
        }
        return null;
    }

    private static boolean containsDirectJavaSource(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.anyMatch(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().endsWith(".java"));
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to inspect package directory: " + directory, ex);
        }
    }
}
