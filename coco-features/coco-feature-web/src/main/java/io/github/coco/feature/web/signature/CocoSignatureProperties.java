package io.github.coco.feature.web.signature;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Coco 请求签名配置属性。
 * <p>
 * 控制 Sign 验签设施的启用策略、请求头名称、时间戳容忍窗口和本地密钥映射。
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
public class CocoSignatureProperties {

    private static final long DEFAULT_MAX_CLOCK_SKEW_SECONDS = 300L;

    private boolean enabled = true;

    private boolean required = false;

    private boolean timestampRequired = true;

    private boolean timestampValidationEnabled = true;

    private long maxClockSkewSeconds = DEFAULT_MAX_CLOCK_SKEW_SECONDS;

    private String appIdHeaderName = "X-Coco-App-Id";

    private String keyIdHeaderName = "X-Coco-Key-Id";

    private String timestampHeaderName = "X-Coco-Timestamp";

    private String nonceHeaderName = "X-Coco-Nonce";

    private String signatureHeaderName = "X-Coco-Sign";

    private String signatureFallbackHeaderName = "X-Coco-Signature";

    private String algorithmHeaderName = "X-Coco-Sign-Algorithm";

    private String defaultAlgorithm = "HMAC-SHA256";

    private Map<String, String> secrets = Map.of();

    /**
     * <p>
     * 返回是否启用 Sign 验签设施。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用 Sign 验签设施。
     * </p>
     * @param enabled 是否启用 Sign 验签设施
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回是否要求所有请求必须携带签名。
     * </p>
     * @return 要求所有请求签名时返回 {@code true}
     */
    public boolean isRequired() {
        return this.required;
    }

    /**
     * <p>
     * 设置是否要求所有请求必须携带签名。
     * </p>
     * @param required 是否要求所有请求签名
     */
    public void setRequired(boolean required) {
        this.required = required;
    }

    /**
     * <p>
     * 返回签名请求是否必须携带时间戳。
     * </p>
     * @return 必须携带时间戳时返回 {@code true}
     */
    public boolean isTimestampRequired() {
        return this.timestampRequired;
    }

    /**
     * <p>
     * 设置签名请求是否必须携带时间戳。
     * </p>
     * @param timestampRequired 是否必须携带时间戳
     */
    public void setTimestampRequired(boolean timestampRequired) {
        this.timestampRequired = timestampRequired;
    }

    /**
     * <p>
     * 返回是否校验签名时间戳窗口。
     * </p>
     * @return 校验时间戳窗口时返回 {@code true}
     */
    public boolean isTimestampValidationEnabled() {
        return this.timestampValidationEnabled;
    }

    /**
     * <p>
     * 设置是否校验签名时间戳窗口。
     * </p>
     * @param timestampValidationEnabled 是否校验时间戳窗口
     */
    public void setTimestampValidationEnabled(boolean timestampValidationEnabled) {
        this.timestampValidationEnabled = timestampValidationEnabled;
    }

    /**
     * <p>
     * 返回允许的客户端和服务端时间差，单位秒。
     * </p>
     * @return 时间戳容忍窗口秒数
     */
    public long getMaxClockSkewSeconds() {
        return this.maxClockSkewSeconds;
    }

    /**
     * <p>
     * 设置允许的客户端和服务端时间差，单位秒。
     * </p>
     * @param maxClockSkewSeconds 时间戳容忍窗口秒数
     */
    public void setMaxClockSkewSeconds(long maxClockSkewSeconds) {
        this.maxClockSkewSeconds = maxClockSkewSeconds <= 0
                ? DEFAULT_MAX_CLOCK_SKEW_SECONDS
                : maxClockSkewSeconds;
    }

    /**
     * <p>
     * 返回 AppId 请求头名称。
     * </p>
     * @return AppId 请求头名称
     */
    public String getAppIdHeaderName() {
        return this.appIdHeaderName;
    }

    /**
     * <p>
     * 设置 AppId 请求头名称。
     * </p>
     * @param appIdHeaderName AppId 请求头名称
     */
    public void setAppIdHeaderName(String appIdHeaderName) {
        this.appIdHeaderName = normalizeHeaderName(appIdHeaderName, "X-Coco-App-Id");
    }

    /**
     * <p>
     * 返回 KeyId 请求头名称。
     * </p>
     * @return KeyId 请求头名称
     */
    public String getKeyIdHeaderName() {
        return this.keyIdHeaderName;
    }

    /**
     * <p>
     * 设置 KeyId 请求头名称。
     * </p>
     * @param keyIdHeaderName KeyId 请求头名称
     */
    public void setKeyIdHeaderName(String keyIdHeaderName) {
        this.keyIdHeaderName = normalizeHeaderName(keyIdHeaderName, "X-Coco-Key-Id");
    }

    /**
     * <p>
     * 返回时间戳请求头名称。
     * </p>
     * @return 时间戳请求头名称
     */
    public String getTimestampHeaderName() {
        return this.timestampHeaderName;
    }

    /**
     * <p>
     * 设置时间戳请求头名称。
     * </p>
     * @param timestampHeaderName 时间戳请求头名称
     */
    public void setTimestampHeaderName(String timestampHeaderName) {
        this.timestampHeaderName = normalizeHeaderName(timestampHeaderName, "X-Coco-Timestamp");
    }

    /**
     * <p>
     * 返回随机串请求头名称。
     * </p>
     * @return 随机串请求头名称
     */
    public String getNonceHeaderName() {
        return this.nonceHeaderName;
    }

    /**
     * <p>
     * 设置随机串请求头名称。
     * </p>
     * @param nonceHeaderName 随机串请求头名称
     */
    public void setNonceHeaderName(String nonceHeaderName) {
        this.nonceHeaderName = normalizeHeaderName(nonceHeaderName, "X-Coco-Nonce");
    }

    /**
     * <p>
     * 返回签名请求头名称。
     * </p>
     * @return 签名请求头名称
     */
    public String getSignatureHeaderName() {
        return this.signatureHeaderName;
    }

    /**
     * <p>
     * 设置签名请求头名称。
     * </p>
     * @param signatureHeaderName 签名请求头名称
     */
    public void setSignatureHeaderName(String signatureHeaderName) {
        this.signatureHeaderName = normalizeHeaderName(signatureHeaderName, "X-Coco-Sign");
    }

    /**
     * <p>
     * 返回签名兜底请求头名称。
     * </p>
     * @return 签名兜底请求头名称
     */
    public String getSignatureFallbackHeaderName() {
        return this.signatureFallbackHeaderName;
    }

    /**
     * <p>
     * 设置签名兜底请求头名称。
     * </p>
     * @param signatureFallbackHeaderName 签名兜底请求头名称
     */
    public void setSignatureFallbackHeaderName(String signatureFallbackHeaderName) {
        this.signatureFallbackHeaderName = normalizeHeaderName(signatureFallbackHeaderName, "X-Coco-Signature");
    }

    /**
     * <p>
     * 返回签名算法请求头名称。
     * </p>
     * @return 签名算法请求头名称
     */
    public String getAlgorithmHeaderName() {
        return this.algorithmHeaderName;
    }

    /**
     * <p>
     * 设置签名算法请求头名称。
     * </p>
     * @param algorithmHeaderName 签名算法请求头名称
     */
    public void setAlgorithmHeaderName(String algorithmHeaderName) {
        this.algorithmHeaderName = normalizeHeaderName(algorithmHeaderName, "X-Coco-Sign-Algorithm");
    }

    /**
     * <p>
     * 返回默认签名算法。
     * </p>
     * @return 默认签名算法
     */
    public String getDefaultAlgorithm() {
        return this.defaultAlgorithm;
    }

    /**
     * <p>
     * 设置默认签名算法。
     * </p>
     * @param defaultAlgorithm 默认签名算法
     */
    public void setDefaultAlgorithm(String defaultAlgorithm) {
        this.defaultAlgorithm = defaultAlgorithm == null || defaultAlgorithm.isBlank()
                ? "HMAC-SHA256"
                : defaultAlgorithm.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * <p>
     * 返回本地签名密钥映射。
     * </p>
     * <p>
     * 键可以是 {@code appId} 或 {@code appId:keyId}，后者优先级更高。
     * </p>
     * @return 本地签名密钥映射
     */
    public Map<String, String> getSecrets() {
        return this.secrets;
    }

    /**
     * <p>
     * 设置本地签名密钥映射。
     * </p>
     * @param secrets 本地签名密钥映射
     */
    public void setSecrets(Map<String, String> secrets) {
        this.secrets = normalizeSecrets(secrets);
    }

    private static String normalizeHeaderName(String headerName, String defaultValue) {
        return headerName == null || headerName.isBlank() ? defaultValue : headerName.trim();
    }

    private static Map<String, String> normalizeSecrets(Map<String, String> secrets) {
        if (secrets == null || secrets.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalizedSecrets = new LinkedHashMap<>();
        secrets.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                normalizedSecrets.put(key.trim(), value.trim());
            }
        });
        return normalizedSecrets.isEmpty() ? Map.of() : Map.copyOf(normalizedSecrets);
    }
}
