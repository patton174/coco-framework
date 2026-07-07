package io.github.coco.common.context;

import java.util.Locale;

/**
 * Coco 请求上下文标准属性键。
 * <p>
 * 统一定义框架内部共享的请求上下文字段，避免 Web、日志、审计、租户和数据权限等模块各自约定属性名称。
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
public final class CocoRequestContextAttributes {

    /**
     * 客户端 IP 属性键。
     */
    public static final String CLIENT_IP = "clientIp";

    /**
     * 客户端 IP 来源属性键。
     */
    public static final String CLIENT_IP_SOURCE = "clientIpSource";

    /**
     * 客户端 IP 来源请求头属性键。
     */
    public static final String CLIENT_IP_SOURCE_HEADER = "clientIpSourceHeader";

    /**
     * 客户端 IP 来源请求头原始值属性键。
     */
    public static final String CLIENT_IP_SOURCE_HEADER_VALUE = "clientIpSourceHeaderValue";

    /**
     * Servlet 远端地址属性键。
     */
    public static final String CLIENT_IP_REMOTE_ADDRESS = "clientIpRemoteAddress";

    /**
     * 客户端 IP 是否来自可信代理链属性键。
     */
    public static final String CLIENT_IP_TRUSTED_PROXY = "clientIpTrustedProxy";

    /**
     * 客户端 IP 代理链属性键。
     */
    public static final String CLIENT_IP_CHAIN = "clientIpChain";

    /**
     * 客户端 IP 命中的代理链下标属性键。
     */
    public static final String CLIENT_IP_RESOLVED_CHAIN_INDEX = "clientIpResolvedChainIndex";

    /**
     * User-Agent 属性键。
     */
    public static final String USER_AGENT = "userAgent";

    /**
     * 查询字符串属性键。
     */
    public static final String QUERY_STRING = "queryString";

    /**
     * 请求语言属性键。
     */
    public static final String LOCALE = "locale";

    /**
     * 请求协议属性键。
     */
    public static final String SCHEME = "scheme";

    /**
     * 请求主机属性键。
     */
    public static final String HOST = "host";

    /**
     * 请求端口属性键。
     */
    public static final String PORT = "port";

    /**
     * 请求内容类型属性键。
     */
    public static final String CONTENT_TYPE = "contentType";

    /**
     * 请求目标来源属性键。
     */
    public static final String REQUEST_TARGET_SOURCE = "requestTargetSource";

    /**
     * 请求目标远端地址属性键。
     */
    public static final String REQUEST_TARGET_REMOTE_ADDRESS = "requestTargetRemoteAddress";

    /**
     * 请求目标是否来自可信代理属性键。
     */
    public static final String REQUEST_TARGET_TRUSTED_PROXY = "requestTargetTrustedProxy";

    /**
     * 请求目标来源头列表属性键。
     */
    public static final String REQUEST_TARGET_SOURCE_HEADERS = "requestTargetSourceHeaders";

    /**
     * 请求目标转发前缀属性键。
     */
    public static final String REQUEST_TARGET_FORWARDED_PREFIX = "requestTargetForwardedPrefix";

    /**
     * 请求上下文阶段属性键。
     */
    public static final String REQUEST_CONTEXT_PHASE = "requestContextPhase";

    /**
     * 请求体参数来源属性键。
     */
    public static final String REQUEST_PAYLOAD_SOURCE = "requestPayloadSource";

    /**
     * 请求体参数解析状态属性键。
     */
    public static final String REQUEST_PAYLOAD_PARSE_STATUS = "requestPayloadParseStatus";

    /**
     * 浏览器指纹属性键。
     */
    public static final String BROWSER_FINGERPRINT = "browserFingerprint";

    /**
     * 浏览器指纹信号属性键前缀。
     */
    public static final String BROWSER_FINGERPRINT_SIGNAL_PREFIX = "browserFingerprintSignal.";

    /**
     * 请求体 SHA-256 摘要属性键。
     */
    public static final String REQUEST_BODY_SHA256 = "requestBodySha256";

    /**
     * 传输态请求体 SHA-256 摘要属性键。
     */
    public static final String REQUEST_BODY_TRANSPORT_SHA256 = "requestBodyTransportSha256";

    /**
     * 业务态请求体 SHA-256 摘要属性键。
     */
    public static final String REQUEST_BODY_EFFECTIVE_SHA256 = "requestBodyEffectiveSha256";

    /**
     * 传输态请求体长度属性键。
     */
    public static final String REQUEST_BODY_TRANSPORT_LENGTH = "requestBodyTransportLength";

    /**
     * 业务态请求体长度属性键。
     */
    public static final String REQUEST_BODY_EFFECTIVE_LENGTH = "requestBodyEffectiveLength";

    /**
     * 请求体阶段属性键。
     */
    public static final String REQUEST_BODY_STAGE = "requestBodyStage";

    /**
     * 请求安全应用标识属性键。
     */
    public static final String SECURITY_APP_ID = "securityAppId";

    /**
     * 签名应用标识属性键。
     */
    public static final String SIGNATURE_APP_ID = "signatureAppId";

    /**
     * 签名密钥标识属性键。
     */
    public static final String SIGNATURE_KEY_ID = "signatureKeyId";

    /**
     * 签名时间戳属性键。
     */
    public static final String SIGNATURE_TIMESTAMP = "signatureTimestamp";

    /**
     * 签名随机串属性键。
     */
    public static final String SIGNATURE_NONCE = "signatureNonce";

    /**
     * 签名值属性键。
     */
    public static final String SIGNATURE_VALUE = "signatureValue";

    /**
     * 请求安全密钥标识属性键。
     */
    public static final String SECURITY_KEY_ID = "securityKeyId";

    /**
     * 请求是否已签名属性键。
     */
    public static final String REQUEST_SIGNED = "requestSigned";

    /**
     * 请求是否已加密属性键。
     */
    public static final String REQUEST_ENCRYPTED = "requestEncrypted";

    /**
     * 请求是否带有防重放材料属性键。
     */
    public static final String REQUEST_REPLAY_PROTECTED = "requestReplayProtected";

    /**
     * 请求签名算法属性键。
     */
    public static final String SIGNATURE_ALGORITHM = "signatureAlgorithm";

    /**
     * 请求签名元数据来源属性键。
     */
    public static final String SIGNATURE_METADATA_SOURCE = "signatureMetadataSource";

    /**
     * 请求签名是否已验证通过属性键。
     */
    public static final String SIGNATURE_VERIFIED = "signatureVerified";

    /**
     * 请求签名验证完成时间属性键。
     */
    public static final String SIGNATURE_VERIFIED_AT = "signatureVerifiedAt";

    /**
     * 请求签名规范化文本 SHA-256 摘要属性键。
     */
    public static final String SIGNATURE_CANONICAL_SHA256 = "signatureCanonicalSha256";

    /**
     * 请求加密算法属性键。
     */
    public static final String ENCRYPTION_ALGORITHM = "encryptionAlgorithm";

    /**
     * 请求加密元数据来源属性键。
     */
    public static final String ENCRYPTION_METADATA_SOURCE = "encryptionMetadataSource";

    /**
     * 请求是否已成功解密属性键。
     */
    public static final String REQUEST_DECRYPTED = "requestDecrypted";

    /**
     * 请求加密 AAD 版本属性键。
     */
    public static final String ENCRYPTION_ASSOCIATED_DATA_VERSION = "encryptionAssociatedDataVersion";

    /**
     * 请求加密 AAD SHA-256 摘要属性键。
     */
    public static final String ENCRYPTION_ASSOCIATED_DATA_SHA256 = "encryptionAssociatedDataSha256";

    /**
     * 加密应用标识属性键。
     */
    public static final String ENCRYPTION_APP_ID = "encryptionAppId";

    /**
     * 加密密钥标识属性键。
     */
    public static final String ENCRYPTION_KEY_ID = "encryptionKeyId";

    /**
     * 加密初始向量属性键。
     */
    public static final String ENCRYPTION_IV = "encryptionIv";

    /**
     * 重放应用标识属性键。
     */
    public static final String REPLAY_APP_ID = "replayAppId";

    /**
     * 重放密钥标识属性键。
     */
    public static final String REPLAY_KEY_ID = "replayKeyId";

    /**
     * 重放时间戳属性键。
     */
    public static final String REPLAY_TIMESTAMP = "replayTimestamp";

    /**
     * 重放随机串属性键。
     */
    public static final String REPLAY_NONCE = "replayNonce";

    /**
     * 请求防重放元数据来源属性键。
     */
    public static final String REPLAY_METADATA_SOURCE = "replayMetadataSource";

    /**
     * 请求防重放键是否已预占属性键。
     */
    public static final String REPLAY_RESERVED = "replayReserved";

    /**
     * 请求防重放键过期时间属性键。
     */
    public static final String REPLAY_EXPIRES_AT = "replayExpiresAt";

    /**
     * 请求防重放窗口秒数属性键。
     */
    public static final String REPLAY_WINDOW_SECONDS = "replayWindowSeconds";

    /**
     * 请求防重放键 SHA-256 摘要属性键。
     */
    public static final String REPLAY_KEY_SHA256 = "replayKeySha256";

    /**
     * 请求头属性键前缀。
     */
    public static final String HEADER_PREFIX = "header.";

    /**
     * Cookie 属性键前缀。
     */
    public static final String COOKIE_PREFIX = "cookie.";

    /**
     * 请求参数属性键前缀。
     */
    public static final String PARAMETER_PREFIX = "parameter.";

    /**
     * 查询参数属性键前缀。
     */
    public static final String QUERY_PARAMETER_PREFIX = "queryParameter.";

    /**
     * 请求体参数属性键前缀。
     */
    public static final String PAYLOAD_PARAMETER_PREFIX = "payloadParameter.";

    private CocoRequestContextAttributes() {
    }

    /**
     * <p>
     * 创建请求头上下文属性键。
     * </p>
     * @param name 请求头名称
     * @return 请求头上下文属性键
     */
    public static String header(String name) {
        return HEADER_PREFIX + normalizeHeaderName(name);
    }

    /**
     * <p>
     * 创建 Cookie 上下文属性键。
     * </p>
     * @param name Cookie 名称
     * @return Cookie 上下文属性键
     */
    public static String cookie(String name) {
        return COOKIE_PREFIX + normalizeSegment(name);
    }

    /**
     * <p>
     * 创建请求参数上下文属性键。
     * </p>
     * @param name 请求参数名称
     * @return 请求参数上下文属性键
     */
    public static String parameter(String name) {
        return PARAMETER_PREFIX + normalizeSegment(name);
    }

    /**
     * <p>
     * 创建查询参数上下文属性键。
     * </p>
     * @param name 查询参数名称
     * @return 查询参数上下文属性键
     */
    public static String queryParameter(String name) {
        return QUERY_PARAMETER_PREFIX + normalizeSegment(name);
    }

    /**
     * <p>
     * 创建请求体参数上下文属性键。
     * </p>
     * @param name 请求体参数名称
     * @return 请求体参数上下文属性键
     */
    public static String payloadParameter(String name) {
        return PAYLOAD_PARAMETER_PREFIX + normalizeSegment(name);
    }

    /**
     * <p>
     * 创建浏览器指纹信号上下文属性键。
     * </p>
     * @param name 浏览器指纹信号名称
     * @return 浏览器指纹信号上下文属性键
     */
    public static String browserFingerprintSignal(String name) {
        return BROWSER_FINGERPRINT_SIGNAL_PREFIX + normalizeHeaderName(name);
    }

    private static String normalizeHeaderName(String name) {
        return normalizeSegment(name).toLowerCase(Locale.ROOT);
    }

    private static String normalizeSegment(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("attribute segment must not be blank");
        }
        return name.trim();
    }
}
