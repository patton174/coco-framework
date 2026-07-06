package io.github.coco.common.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Coco 请求上下文。
 * <p>
 * 保存当前请求的 TraceId、HTTP 方法、请求路径和扩展属性，为日志、审计、租户、数据权限等能力提供统一上下文快照。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-context}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoRequestContext {

    private final String traceId;

    private final String method;

    private final String path;

    private final Map<String, String> attributes;

    private CocoRequestContext(String traceId, String method, String path, Map<String, String> attributes) {
        this.traceId = requireTraceId(traceId);
        this.method = normalizeMethod(method);
        this.path = normalizeOptional(path);
        this.attributes = copyAttributes(attributes);
    }

    /**
     * <p>
     * 创建只包含 TraceId 的请求上下文。
     * </p>
     * @param traceId TraceId
     * @return 请求上下文
     */
    public static CocoRequestContext of(String traceId) {
        return of(traceId, null, null);
    }

    /**
     * <p>
     * 创建包含 TraceId、HTTP 方法和请求路径的请求上下文。
     * </p>
     * @param traceId TraceId
     * @param method HTTP 方法
     * @param path 请求路径
     * @return 请求上下文
     */
    public static CocoRequestContext of(String traceId, String method, String path) {
        return of(traceId, method, path, Map.of());
    }

    /**
     * <p>
     * 创建包含扩展属性的请求上下文。
     * </p>
     * @param traceId TraceId
     * @param method HTTP 方法
     * @param path 请求路径
     * @param attributes 扩展属性
     * @return 请求上下文
     */
    public static CocoRequestContext of(String traceId, String method, String path,
            Map<String, String> attributes) {
        return new CocoRequestContext(traceId, method, path, attributes);
    }

    /**
     * <p>
     * 返回请求 TraceId。
     * </p>
     * @return TraceId
     */
    public String traceId() {
        return this.traceId;
    }

    /**
     * <p>
     * 返回 HTTP 方法。
     * </p>
     * @return HTTP 方法；未设置时为空
     */
    public Optional<String> method() {
        return Optional.ofNullable(this.method);
    }

    /**
     * <p>
     * 返回请求路径。
     * </p>
     * @return 请求路径；未设置时为空
     */
    public Optional<String> path() {
        return Optional.ofNullable(this.path);
    }

    /**
     * <p>
     * 返回不可变扩展属性。
     * </p>
     * @return 扩展属性
     */
    public Map<String, String> attributes() {
        return this.attributes;
    }

    /**
     * <p>
     * 返回指定名称的扩展属性。
     * </p>
     * @param name 属性名称
     * @return 属性值；未设置时为空
     */
    public Optional<String> attribute(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.attributes.get(name.trim()));
    }

    /**
     * <p>
     * 返回客户端 IP。
     * </p>
     * @return 客户端 IP；未设置时为空
     */
    public Optional<String> clientIp() {
        return attribute(CocoRequestContextAttributes.CLIENT_IP);
    }

    /**
     * <p>
     * 返回客户端 IP 来源。
     * </p>
     * @return 客户端 IP 来源；未设置时为空
     */
    public Optional<String> clientIpSource() {
        return attribute(CocoRequestContextAttributes.CLIENT_IP_SOURCE);
    }

    /**
     * <p>
     * 返回客户端 IP 来源请求头。
     * </p>
     * @return 客户端 IP 来源请求头；未设置时为空
     */
    public Optional<String> clientIpSourceHeader() {
        return attribute(CocoRequestContextAttributes.CLIENT_IP_SOURCE_HEADER);
    }

    /**
     * <p>
     * 返回 Servlet 远端地址。
     * </p>
     * @return Servlet 远端地址；未设置时为空
     */
    public Optional<String> clientIpRemoteAddress() {
        return attribute(CocoRequestContextAttributes.CLIENT_IP_REMOTE_ADDRESS);
    }

    /**
     * <p>
     * 返回客户端 IP 是否来自可信代理链。
     * </p>
     * @return 客户端 IP 来自可信代理链时返回 {@code true}
     */
    public boolean clientIpTrustedProxy() {
        return attribute(CocoRequestContextAttributes.CLIENT_IP_TRUSTED_PROXY)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    /**
     * <p>
     * 返回 User-Agent。
     * </p>
     * @return User-Agent；未设置时为空
     */
    public Optional<String> userAgent() {
        return attribute(CocoRequestContextAttributes.USER_AGENT);
    }

    /**
     * <p>
     * 返回查询字符串。
     * </p>
     * @return 查询字符串；未设置时为空
     */
    public Optional<String> queryString() {
        return attribute(CocoRequestContextAttributes.QUERY_STRING);
    }

    /**
     * <p>
     * 返回请求语言。
     * </p>
     * @return 请求语言；未设置时为空
     */
    public Optional<String> locale() {
        return attribute(CocoRequestContextAttributes.LOCALE);
    }

    /**
     * <p>
     * 返回浏览器指纹。
     * </p>
     * @return 浏览器指纹；未设置时为空
     */
    public Optional<String> browserFingerprint() {
        return attribute(CocoRequestContextAttributes.BROWSER_FINGERPRINT);
    }

    /**
     * <p>
     * 返回请求体 SHA-256 摘要。
     * </p>
     * @return 请求体 SHA-256 摘要；未设置时为空
     */
    public Optional<String> requestBodySha256() {
        return attribute(CocoRequestContextAttributes.REQUEST_BODY_SHA256);
    }

    /**
     * <p>
     * 返回请求安全应用标识。
     * </p>
     * @return 请求安全应用标识；未设置时为空
     */
    public Optional<String> securityAppId() {
        return attribute(CocoRequestContextAttributes.SECURITY_APP_ID);
    }

    /**
     * <p>
     * 返回请求安全密钥标识。
     * </p>
     * @return 请求安全密钥标识；未设置时为空
     */
    public Optional<String> securityKeyId() {
        return attribute(CocoRequestContextAttributes.SECURITY_KEY_ID);
    }

    /**
     * <p>
     * 返回请求是否已签名。
     * </p>
     * @return 已签名时返回 {@code true}
     */
    public boolean requestSigned() {
        return attribute(CocoRequestContextAttributes.REQUEST_SIGNED)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    /**
     * <p>
     * 返回请求是否已加密。
     * </p>
     * @return 已加密时返回 {@code true}
     */
    public boolean requestEncrypted() {
        return attribute(CocoRequestContextAttributes.REQUEST_ENCRYPTED)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    /**
     * <p>
     * 返回请求是否带有防重放材料。
     * </p>
     * @return 带有防重放材料时返回 {@code true}
     */
    public boolean requestReplayProtected() {
        return attribute(CocoRequestContextAttributes.REQUEST_REPLAY_PROTECTED)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    /**
     * <p>
     * 返回请求签名算法。
     * </p>
     * @return 请求签名算法；未设置时为空
     */
    public Optional<String> signatureAlgorithm() {
        return attribute(CocoRequestContextAttributes.SIGNATURE_ALGORITHM);
    }

    /**
     * <p>
     * 返回请求加密算法。
     * </p>
     * @return 请求加密算法；未设置时为空
     */
    public Optional<String> encryptionAlgorithm() {
        return attribute(CocoRequestContextAttributes.ENCRYPTION_ALGORITHM);
    }

    /**
     * <p>
     * 返回请求头上下文属性。
     * </p>
     * @param name 请求头名称
     * @return 请求头值；未设置时为空
     */
    public Optional<String> header(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return attribute(CocoRequestContextAttributes.header(name));
    }

    /**
     * <p>
     * 返回请求参数上下文属性。
     * </p>
     * @param name 请求参数名称
     * @return 请求参数值；未设置时为空
     */
    public Optional<String> parameter(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return attribute(CocoRequestContextAttributes.parameter(name));
    }

    private static String requireTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        return traceId.trim();
    }

    private static String normalizeMethod(String method) {
        String normalized = normalizeOptional(method);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, String> copyAttributes(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copied = new LinkedHashMap<>();
        attributes.forEach((name, value) -> {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("attribute name must not be blank");
            }
            String normalizedValue = normalizeOptional(value);
            if (normalizedValue != null) {
                copied.put(name.trim(), normalizedValue);
            }
        });
        return Collections.unmodifiableMap(copied);
    }
}
