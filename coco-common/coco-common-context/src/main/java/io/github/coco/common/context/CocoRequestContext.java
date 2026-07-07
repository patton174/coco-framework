package io.github.coco.common.context;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    private static final Set<String> PUBLIC_FRAMEWORK_ATTRIBUTES = Set.of(
            CocoRequestContextAttributes.CLIENT_IP,
            CocoRequestContextAttributes.LOCALE);

    private static final Set<String> RESERVED_FRAMEWORK_ATTRIBUTES = reservedFrameworkAttributes();

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
     * 返回客户端 IP 来源请求头原始值。
     * </p>
     * @return 客户端 IP 来源请求头原始值；未设置时为空
     */
    public Optional<String> clientIpSourceHeaderValue() {
        return attribute(CocoRequestContextAttributes.CLIENT_IP_SOURCE_HEADER_VALUE);
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
     * 返回客户端 IP 代理链。
     * </p>
     * @return 客户端 IP 代理链；未设置时返回空列表
     */
    public List<String> clientIpChain() {
        return listAttribute(CocoRequestContextAttributes.CLIENT_IP_CHAIN, true)
                .orElseGet(List::of);
    }

    /**
     * <p>
     * 返回命中的客户端 IP 在代理链中的下标。
     * </p>
     * @return 命中的客户端 IP 在代理链中的下标；未设置时为空
     */
    public Optional<Integer> clientIpResolvedChainIndex() {
        return intAttribute(CocoRequestContextAttributes.CLIENT_IP_RESOLVED_CHAIN_INDEX);
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
     * 返回请求协议。
     * </p>
     * @return 请求协议；未设置时为空
     */
    public Optional<String> scheme() {
        return attribute(CocoRequestContextAttributes.SCHEME);
    }

    /**
     * <p>
     * 返回请求主机。
     * </p>
     * @return 请求主机；未设置时为空
     */
    public Optional<String> host() {
        return attribute(CocoRequestContextAttributes.HOST);
    }

    /**
     * <p>
     * 返回请求端口。
     * </p>
     * @return 请求端口；未设置时为空
     */
    public Optional<Integer> port() {
        return intAttribute(CocoRequestContextAttributes.PORT);
    }

    /**
     * <p>
     * 返回请求内容类型。
     * </p>
     * @return 请求内容类型；未设置时为空
     */
    public Optional<String> contentType() {
        return attribute(CocoRequestContextAttributes.CONTENT_TYPE);
    }

    /**
     * <p>
     * 返回请求目标来源。
     * </p>
     * @return 请求目标来源；未设置时为空
     */
    public Optional<String> requestTargetSource() {
        return attribute(CocoRequestContextAttributes.REQUEST_TARGET_SOURCE);
    }

    /**
     * <p>
     * 返回请求目标解析时使用的远端地址。
     * </p>
     * @return 请求目标解析远端地址；未设置时为空
     */
    public Optional<String> requestTargetRemoteAddress() {
        return attribute(CocoRequestContextAttributes.REQUEST_TARGET_REMOTE_ADDRESS);
    }

    /**
     * <p>
     * 返回请求目标是否来自可信代理。
     * </p>
     * @return 请求目标来自可信代理时返回 {@code true}
     */
    public boolean requestTargetTrustedProxy() {
        return attribute(CocoRequestContextAttributes.REQUEST_TARGET_TRUSTED_PROXY)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    /**
     * <p>
     * 返回请求目标解析时实际使用的来源头列表。
     * </p>
     * @return 请求目标来源头列表；未设置时返回空列表
     */
    public List<String> requestTargetSourceHeaders() {
        return listAttribute(CocoRequestContextAttributes.REQUEST_TARGET_SOURCE_HEADERS, true)
                .orElseGet(List::of);
    }

    /**
     * <p>
     * 返回请求目标生效的转发前缀。
     * </p>
     * @return 生效的转发前缀；未设置时为空
     */
    public Optional<String> requestTargetForwardedPrefix() {
        return attribute(CocoRequestContextAttributes.REQUEST_TARGET_FORWARDED_PREFIX);
    }

    /**
     * <p>
     * 返回请求上下文阶段。
     * </p>
     * @return 请求上下文阶段；未设置时为空
     */
    public Optional<String> requestContextPhase() {
        return attribute(CocoRequestContextAttributes.REQUEST_CONTEXT_PHASE);
    }

    /**
     * <p>
     * 返回请求体参数来源。
     * </p>
     * @return 请求体参数来源；未设置时为空
     */
    public Optional<String> requestPayloadSource() {
        return attribute(CocoRequestContextAttributes.REQUEST_PAYLOAD_SOURCE);
    }

    /**
     * <p>
     * 返回请求体参数解析状态。
     * </p>
     * @return 请求体参数解析状态；未设置时为空
     */
    public Optional<String> requestPayloadParseStatus() {
        return attribute(CocoRequestContextAttributes.REQUEST_PAYLOAD_PARSE_STATUS);
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
     * 返回浏览器指纹信号快照。
     * </p>
     * @return 浏览器指纹信号快照
     */
    public Map<String, String> browserFingerprintSignals() {
        return prefixedAttributes(CocoRequestContextAttributes.BROWSER_FINGERPRINT_SIGNAL_PREFIX);
    }

    /**
     * <p>
     * 返回指定浏览器指纹信号。
     * </p>
     * @param name 浏览器指纹信号名称
     * @return 浏览器指纹信号值；未设置时为空
     */
    public Optional<String> browserFingerprintSignal(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return attribute(CocoRequestContextAttributes.browserFingerprintSignal(name));
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
     * 返回传输态请求体 SHA-256 摘要。
     * </p>
     * @return 传输态请求体 SHA-256 摘要；未设置时为空
     */
    public Optional<String> requestBodyTransportSha256() {
        return attribute(CocoRequestContextAttributes.REQUEST_BODY_TRANSPORT_SHA256);
    }

    /**
     * <p>
     * 返回业务态请求体 SHA-256 摘要。
     * </p>
     * @return 业务态请求体 SHA-256 摘要；未设置时为空
     */
    public Optional<String> requestBodyEffectiveSha256() {
        return attribute(CocoRequestContextAttributes.REQUEST_BODY_EFFECTIVE_SHA256)
                .or(this::requestBodySha256);
    }

    /**
     * <p>
     * 返回传输态请求体长度。
     * </p>
     * @return 传输态请求体长度；未设置时为空
     */
    public Optional<Long> requestBodyTransportLength() {
        return longAttribute(CocoRequestContextAttributes.REQUEST_BODY_TRANSPORT_LENGTH);
    }

    /**
     * <p>
     * 返回业务态请求体长度。
     * </p>
     * @return 业务态请求体长度；未设置时为空
     */
    public Optional<Long> requestBodyEffectiveLength() {
        return longAttribute(CocoRequestContextAttributes.REQUEST_BODY_EFFECTIVE_LENGTH);
    }

    /**
     * <p>
     * 返回请求体阶段。
     * </p>
     * @return 请求体阶段；未设置时为空
     */
    public Optional<String> requestBodyStage() {
        return attribute(CocoRequestContextAttributes.REQUEST_BODY_STAGE);
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
     * 返回签名应用标识。
     * </p>
     * @return 签名应用标识；未设置时为空
     */
    public Optional<String> signatureAppId() {
        return attribute(CocoRequestContextAttributes.SIGNATURE_APP_ID);
    }

    /**
     * <p>
     * 返回签名密钥标识。
     * </p>
     * @return 签名密钥标识；未设置时为空
     */
    public Optional<String> signatureKeyId() {
        return attribute(CocoRequestContextAttributes.SIGNATURE_KEY_ID);
    }

    /**
     * <p>
     * 返回签名时间戳。
     * </p>
     * @return 签名时间戳；未设置时为空
     */
    public Optional<String> signatureTimestamp() {
        return attribute(CocoRequestContextAttributes.SIGNATURE_TIMESTAMP);
    }

    /**
     * <p>
     * 返回签名随机串。
     * </p>
     * @return 签名随机串；未设置时为空
     */
    public Optional<String> signatureNonce() {
        return attribute(CocoRequestContextAttributes.SIGNATURE_NONCE);
    }

    /**
     * <p>
     * 返回签名值。
     * </p>
     * @return 签名值；未设置时为空
     */
    public Optional<String> signatureValue() {
        return attribute(CocoRequestContextAttributes.SIGNATURE_VALUE);
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
     * 返回请求签名元数据来源。
     * </p>
     * @return 请求签名元数据来源；未设置时为空
     */
    public Optional<String> signatureMetadataSource() {
        return attribute(CocoRequestContextAttributes.SIGNATURE_METADATA_SOURCE);
    }

    /**
     * <p>
     * 返回请求签名是否已验证通过。
     * </p>
     * @return 验签通过时返回 {@code true}
     */
    public boolean signatureVerified() {
        return attribute(CocoRequestContextAttributes.SIGNATURE_VERIFIED)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    /**
     * <p>
     * 返回请求签名验证完成时间。
     * </p>
     * @return 请求签名验证完成时间；未设置时为空
     */
    public Optional<String> signatureVerifiedAt() {
        return attribute(CocoRequestContextAttributes.SIGNATURE_VERIFIED_AT);
    }

    /**
     * <p>
     * 返回请求签名规范化文本 SHA-256 摘要。
     * </p>
     * @return 请求签名规范化文本 SHA-256 摘要；未设置时为空
     */
    public Optional<String> signatureCanonicalSha256() {
        return attribute(CocoRequestContextAttributes.SIGNATURE_CANONICAL_SHA256);
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
     * 返回请求加密元数据来源。
     * </p>
     * @return 请求加密元数据来源；未设置时为空
     */
    public Optional<String> encryptionMetadataSource() {
        return attribute(CocoRequestContextAttributes.ENCRYPTION_METADATA_SOURCE);
    }

    /**
     * <p>
     * 返回请求是否已成功解密。
     * </p>
     * @return 解密成功时返回 {@code true}
     */
    public boolean requestDecrypted() {
        return attribute(CocoRequestContextAttributes.REQUEST_DECRYPTED)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    /**
     * <p>
     * 返回请求加密 AAD 版本。
     * </p>
     * @return 请求加密 AAD 版本；未设置时为空
     */
    public Optional<String> encryptionAssociatedDataVersion() {
        return attribute(CocoRequestContextAttributes.ENCRYPTION_ASSOCIATED_DATA_VERSION);
    }

    /**
     * <p>
     * 返回请求加密 AAD SHA-256 摘要。
     * </p>
     * @return 请求加密 AAD SHA-256 摘要；未设置时为空
     */
    public Optional<String> encryptionAssociatedDataSha256() {
        return attribute(CocoRequestContextAttributes.ENCRYPTION_ASSOCIATED_DATA_SHA256);
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
     * 返回加密应用标识。
     * </p>
     * @return 加密应用标识；未设置时为空
     */
    public Optional<String> encryptionAppId() {
        return attribute(CocoRequestContextAttributes.ENCRYPTION_APP_ID);
    }

    /**
     * <p>
     * 返回加密密钥标识。
     * </p>
     * @return 加密密钥标识；未设置时为空
     */
    public Optional<String> encryptionKeyId() {
        return attribute(CocoRequestContextAttributes.ENCRYPTION_KEY_ID);
    }

    /**
     * <p>
     * 返回加密初始向量。
     * </p>
     * @return 加密初始向量；未设置时为空
     */
    public Optional<String> encryptionIv() {
        return attribute(CocoRequestContextAttributes.ENCRYPTION_IV);
    }

    /**
     * <p>
     * 返回重放应用标识。
     * </p>
     * @return 重放应用标识；未设置时为空
     */
    public Optional<String> replayAppId() {
        return attribute(CocoRequestContextAttributes.REPLAY_APP_ID);
    }

    /**
     * <p>
     * 返回重放密钥标识。
     * </p>
     * @return 重放密钥标识；未设置时为空
     */
    public Optional<String> replayKeyId() {
        return attribute(CocoRequestContextAttributes.REPLAY_KEY_ID);
    }

    /**
     * <p>
     * 返回重放时间戳。
     * </p>
     * @return 重放时间戳；未设置时为空
     */
    public Optional<String> replayTimestamp() {
        return attribute(CocoRequestContextAttributes.REPLAY_TIMESTAMP);
    }

    /**
     * <p>
     * 返回重放随机串。
     * </p>
     * @return 重放随机串；未设置时为空
     */
    public Optional<String> replayNonce() {
        return attribute(CocoRequestContextAttributes.REPLAY_NONCE);
    }

    /**
     * <p>
     * 返回请求防重放元数据来源。
     * </p>
     * @return 请求防重放元数据来源；未设置时为空
     */
    public Optional<String> replayMetadataSource() {
        return attribute(CocoRequestContextAttributes.REPLAY_METADATA_SOURCE);
    }

    /**
     * <p>
     * 返回请求防重放键是否已预占。
     * </p>
     * @return 防重放键已预占时返回 {@code true}
     */
    public boolean replayReserved() {
        return attribute(CocoRequestContextAttributes.REPLAY_RESERVED)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    /**
     * <p>
     * 返回请求防重放键过期时间。
     * </p>
     * @return 请求防重放键过期时间；未设置时为空
     */
    public Optional<String> replayExpiresAt() {
        return attribute(CocoRequestContextAttributes.REPLAY_EXPIRES_AT);
    }

    /**
     * <p>
     * 返回请求防重放窗口秒数。
     * </p>
     * @return 请求防重放窗口秒数；未设置时为空
     */
    public Optional<Long> replayWindowSeconds() {
        return longAttribute(CocoRequestContextAttributes.REPLAY_WINDOW_SECONDS);
    }

    /**
     * <p>
     * 返回请求防重放键 SHA-256 摘要。
     * </p>
     * @return 请求防重放键 SHA-256 摘要；未设置时为空
     */
    public Optional<String> replayKeySha256() {
        return attribute(CocoRequestContextAttributes.REPLAY_KEY_SHA256);
    }

    /**
     * <p>
     * 返回请求头快照。
     * </p>
     * @return 请求头快照
     */
    public Map<String, String> headers() {
        return prefixedAttributes(CocoRequestContextAttributes.HEADER_PREFIX);
    }

    /**
     * <p>
     * 返回 Cookie 上下文属性。
     * </p>
     * @param name Cookie 名称
     * @return Cookie 值；未设置时为空
     */
    public Optional<String> cookie(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return attribute(CocoRequestContextAttributes.cookie(name));
    }

    /**
     * <p>
     * 返回 Cookie 快照。
     * </p>
     * @return Cookie 快照
     */
    public Map<String, String> cookies() {
        return prefixedAttributes(CocoRequestContextAttributes.COOKIE_PREFIX);
    }

    /**
     * <p>
     * 返回请求参数上下文属性。
     * </p>
     * @param name 请求参数名称
     * @return 请求参数值；未设置时为空
     */
    public Optional<String> parameter(String name) {
        return parameterValues(name).map(values -> String.join(",", values));
    }

    /**
     * <p>
     * 返回请求参数值列表。
     * </p>
     * @param name 请求参数名称
     * @return 请求参数值列表；未设置时为空
     */
    public Optional<List<String>> parameterValues(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return listAttribute(CocoRequestContextAttributes.parameter(name), true);
    }

    /**
     * <p>
     * 返回请求参数快照。
     * </p>
     * @return 请求参数快照
     */
    public Map<String, List<String>> parameters() {
        return prefixedListAttributes(CocoRequestContextAttributes.PARAMETER_PREFIX);
    }

    /**
     * <p>
     * 返回查询参数上下文属性。
     * </p>
     * @param name 查询参数名称
     * @return 查询参数值；未设置时为空
     */
    public Optional<String> queryParameter(String name) {
        return queryParameterValues(name).map(values -> String.join(",", values));
    }

    /**
     * <p>
     * 返回查询参数值列表。
     * </p>
     * @param name 查询参数名称
     * @return 查询参数值列表；未设置时为空
     */
    public Optional<List<String>> queryParameterValues(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return listAttribute(CocoRequestContextAttributes.queryParameter(name), true);
    }

    /**
     * <p>
     * 返回查询参数快照。
     * </p>
     * @return 查询参数快照
     */
    public Map<String, List<String>> queryParameters() {
        return prefixedListAttributes(CocoRequestContextAttributes.QUERY_PARAMETER_PREFIX);
    }

    /**
     * <p>
     * 返回请求体参数上下文属性。
     * </p>
     * @param name 请求体参数名称
     * @return 请求体参数值；未设置时为空
     */
    public Optional<String> payloadParameter(String name) {
        return payloadParameterValues(name).map(values -> String.join(",", values));
    }

    /**
     * <p>
     * 返回请求体参数值列表。
     * </p>
     * @param name 请求体参数名称
     * @return 请求体参数值列表；未设置时为空
     */
    public Optional<List<String>> payloadParameterValues(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return listAttribute(CocoRequestContextAttributes.payloadParameter(name), true);
    }

    /**
     * <p>
     * 返回请求体参数快照。
     * </p>
     * @return 请求体参数快照
     */
    public Map<String, List<String>> payloadParameters() {
        return prefixedListAttributes(CocoRequestContextAttributes.PAYLOAD_PARAMETER_PREFIX);
    }

    private Optional<Long> longAttribute(String name) {
        return attribute(name).flatMap(CocoRequestContext::parseLong);
    }

    private Optional<Integer> intAttribute(String name) {
        return attribute(name).flatMap(CocoRequestContext::parseInteger);
    }

    private Optional<List<String>> listAttribute(String name, boolean legacyCsvFallback) {
        return attribute(name).map(value -> decodeAttributeList(value, legacyCsvFallback));
    }

    private Map<String, String> prefixedAttributes(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        this.attributes.forEach((name, value) -> {
            if (name.startsWith(prefix) && name.length() > prefix.length()) {
                values.put(name.substring(prefix.length()), value);
            }
        });
        return values.isEmpty() ? Map.of() : Collections.unmodifiableMap(values);
    }

    private Map<String, List<String>> prefixedListAttributes(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return Map.of();
        }
        Map<String, List<String>> values = new LinkedHashMap<>();
        this.attributes.forEach((name, value) -> {
            if (name.startsWith(prefix) && name.length() > prefix.length()) {
                values.put(name.substring(prefix.length()), decodeAttributeList(value, true));
            }
        });
        return values.isEmpty() ? Map.of() : Collections.unmodifiableMap(values);
    }

    private static Optional<Long> parseLong(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        }
        catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> parseInteger(String value) {
        try {
            return Optional.of(Integer.parseInt(value));
        }
        catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static List<String> decodeAttributeList(String value, boolean legacyCsvFallback) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        if (CocoRequestContextValueCodec.isEncodedList(value)) {
            try {
                return CocoRequestContextValueCodec.decodeList(value);
            }
            catch (IllegalArgumentException ignored) {
                return List.of(value.trim());
            }
        }
        if (legacyCsvFallback) {
            return Arrays.stream(value.split(",", -1))
                    .map(String::trim)
                    .toList();
        }
        return List.of(value.trim());
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
                String normalizedName = name.trim();
                if (publicAttributeName(normalizedName)) {
                    copied.put(normalizedName, normalizedValue);
                }
            }
        });
        return Collections.unmodifiableMap(copied);
    }

    private static boolean publicAttributeName(String name) {
        if (PUBLIC_FRAMEWORK_ATTRIBUTES.contains(name)) {
            return true;
        }
        return !RESERVED_FRAMEWORK_ATTRIBUTES.contains(name);
    }

    private static Set<String> reservedFrameworkAttributes() {
        Set<String> attributes = new HashSet<>();
        attributes.add(CocoRequestContextAttributes.SECURITY_APP_ID);
        attributes.add(CocoRequestContextAttributes.SIGNATURE_APP_ID);
        attributes.add(CocoRequestContextAttributes.SIGNATURE_KEY_ID);
        attributes.add(CocoRequestContextAttributes.SIGNATURE_TIMESTAMP);
        attributes.add(CocoRequestContextAttributes.SIGNATURE_NONCE);
        attributes.add(CocoRequestContextAttributes.SIGNATURE_VALUE);
        attributes.add(CocoRequestContextAttributes.SECURITY_KEY_ID);
        attributes.add(CocoRequestContextAttributes.ENCRYPTION_APP_ID);
        attributes.add(CocoRequestContextAttributes.ENCRYPTION_KEY_ID);
        attributes.add(CocoRequestContextAttributes.ENCRYPTION_IV);
        attributes.add(CocoRequestContextAttributes.REPLAY_APP_ID);
        attributes.add(CocoRequestContextAttributes.REPLAY_KEY_ID);
        attributes.add(CocoRequestContextAttributes.REPLAY_TIMESTAMP);
        attributes.add(CocoRequestContextAttributes.REPLAY_NONCE);
        return Collections.unmodifiableSet(attributes);
    }
}
