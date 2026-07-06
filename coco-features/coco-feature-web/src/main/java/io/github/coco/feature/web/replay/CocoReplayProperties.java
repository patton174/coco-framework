package io.github.coco.feature.web.replay;

import io.github.coco.feature.web.context.CocoWebRequestMatcherProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco Web 防重放配置属性。
 * <p>
 * 控制防重放过滤器启用策略、保护范围、重放窗口和内存存储清理节奏。
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
public class CocoReplayProperties {

    private static final long DEFAULT_TTL_SECONDS = 300L;

    private static final long DEFAULT_CLEANUP_INTERVAL_SECONDS = 60L;

    private static final String DEFAULT_APP_ID_HEADER_NAME = "X-Coco-App-Id";

    private static final String DEFAULT_KEY_ID_HEADER_NAME = "X-Coco-Key-Id";

    private static final String DEFAULT_TIMESTAMP_HEADER_NAME = "X-Coco-Timestamp";

    private static final String DEFAULT_NONCE_HEADER_NAME = "X-Coco-Nonce";

    private boolean enabled = true;

    private boolean required = false;

    private boolean protectSignedRequests = true;

    private boolean protectEncryptedRequests = true;

    private boolean includeMethod = true;

    private boolean includePath = true;

    @NestedConfigurationProperty
    private CocoWebRequestMatcherProperties matcher = new CocoWebRequestMatcherProperties();

    private String appIdHeaderName = DEFAULT_APP_ID_HEADER_NAME;

    private String keyIdHeaderName = DEFAULT_KEY_ID_HEADER_NAME;

    private String timestampHeaderName = DEFAULT_TIMESTAMP_HEADER_NAME;

    private String nonceHeaderName = DEFAULT_NONCE_HEADER_NAME;

    private long ttlSeconds = DEFAULT_TTL_SECONDS;

    private long cleanupIntervalSeconds = DEFAULT_CLEANUP_INTERVAL_SECONDS;

    /**
     * <p>
     * 返回是否启用防重放设施。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用防重放设施。
     * </p>
     * @param enabled 是否启用防重放设施
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回是否要求所有请求都通过防重放校验。
     * </p>
     * @return 要求所有请求校验时返回 {@code true}
     */
    public boolean isRequired() {
        return this.required;
    }

    /**
     * <p>
     * 设置是否要求所有请求都通过防重放校验。
     * </p>
     * @param required 是否要求所有请求校验
     */
    public void setRequired(boolean required) {
        this.required = required;
    }

    /**
     * <p>
     * 返回是否保护已签名请求。
     * </p>
     * @return 保护已签名请求时返回 {@code true}
     */
    public boolean isProtectSignedRequests() {
        return this.protectSignedRequests;
    }

    /**
     * <p>
     * 设置是否保护已签名请求。
     * </p>
     * @param protectSignedRequests 是否保护已签名请求
     */
    public void setProtectSignedRequests(boolean protectSignedRequests) {
        this.protectSignedRequests = protectSignedRequests;
    }

    /**
     * <p>
     * 返回是否保护已加密请求。
     * </p>
     * @return 保护已加密请求时返回 {@code true}
     */
    public boolean isProtectEncryptedRequests() {
        return this.protectEncryptedRequests;
    }

    /**
     * <p>
     * 设置是否保护已加密请求。
     * </p>
     * @param protectEncryptedRequests 是否保护已加密请求
     */
    public void setProtectEncryptedRequests(boolean protectEncryptedRequests) {
        this.protectEncryptedRequests = protectEncryptedRequests;
    }

    /**
     * <p>
     * 返回防重放键是否包含 HTTP 方法。
     * </p>
     * @return 包含 HTTP 方法时返回 {@code true}
     */
    public boolean isIncludeMethod() {
        return this.includeMethod;
    }

    /**
     * <p>
     * 设置防重放键是否包含 HTTP 方法。
     * </p>
     * @param includeMethod 是否包含 HTTP 方法
     */
    public void setIncludeMethod(boolean includeMethod) {
        this.includeMethod = includeMethod;
    }

    /**
     * <p>
     * 返回防重放键是否包含请求路径。
     * </p>
     * @return 包含请求路径时返回 {@code true}
     */
    public boolean isIncludePath() {
        return this.includePath;
    }

    /**
     * <p>
     * 设置防重放键是否包含请求路径。
     * </p>
     * @param includePath 是否包含请求路径
     */
    public void setIncludePath(boolean includePath) {
        this.includePath = includePath;
    }

    /**
     * <p>
     * 返回防重放路径和方法匹配配置。
     * </p>
     * @return 请求匹配配置
     */
    public CocoWebRequestMatcherProperties getMatcher() {
        return this.matcher;
    }

    /**
     * <p>
     * 设置防重放路径和方法匹配配置。
     * </p>
     * @param matcher 请求匹配配置
     */
    public void setMatcher(CocoWebRequestMatcherProperties matcher) {
        this.matcher = matcher == null ? new CocoWebRequestMatcherProperties() : matcher;
    }

    /**
     * <p>
     * 返回防重放应用标识请求头名称。
     * </p>
     * @return 防重放应用标识请求头名称
     */
    public String getAppIdHeaderName() {
        return this.appIdHeaderName;
    }

    /**
     * <p>
     * 设置防重放应用标识请求头名称。
     * </p>
     * @param appIdHeaderName 防重放应用标识请求头名称
     */
    public void setAppIdHeaderName(String appIdHeaderName) {
        this.appIdHeaderName = normalizeHeaderName(appIdHeaderName, DEFAULT_APP_ID_HEADER_NAME);
    }

    /**
     * <p>
     * 返回防重放密钥标识请求头名称。
     * </p>
     * @return 防重放密钥标识请求头名称
     */
    public String getKeyIdHeaderName() {
        return this.keyIdHeaderName;
    }

    /**
     * <p>
     * 设置防重放密钥标识请求头名称。
     * </p>
     * @param keyIdHeaderName 防重放密钥标识请求头名称
     */
    public void setKeyIdHeaderName(String keyIdHeaderName) {
        this.keyIdHeaderName = normalizeHeaderName(keyIdHeaderName, DEFAULT_KEY_ID_HEADER_NAME);
    }

    /**
     * <p>
     * 返回防重放时间戳请求头名称。
     * </p>
     * @return 防重放时间戳请求头名称
     */
    public String getTimestampHeaderName() {
        return this.timestampHeaderName;
    }

    /**
     * <p>
     * 设置防重放时间戳请求头名称。
     * </p>
     * @param timestampHeaderName 防重放时间戳请求头名称
     */
    public void setTimestampHeaderName(String timestampHeaderName) {
        this.timestampHeaderName = normalizeHeaderName(timestampHeaderName, DEFAULT_TIMESTAMP_HEADER_NAME);
    }

    /**
     * <p>
     * 返回防重放随机串请求头名称。
     * </p>
     * @return 防重放随机串请求头名称
     */
    public String getNonceHeaderName() {
        return this.nonceHeaderName;
    }

    /**
     * <p>
     * 设置防重放随机串请求头名称。
     * </p>
     * @param nonceHeaderName 防重放随机串请求头名称
     */
    public void setNonceHeaderName(String nonceHeaderName) {
        this.nonceHeaderName = normalizeHeaderName(nonceHeaderName, DEFAULT_NONCE_HEADER_NAME);
    }

    /**
     * <p>
     * 返回重放窗口秒数。
     * </p>
     * @return 重放窗口秒数
     */
    public long getTtlSeconds() {
        return this.ttlSeconds;
    }

    /**
     * <p>
     * 设置重放窗口秒数。
     * </p>
     * @param ttlSeconds 重放窗口秒数
     */
    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds <= 0 ? DEFAULT_TTL_SECONDS : ttlSeconds;
    }

    /**
     * <p>
     * 返回内存存储过期键清理间隔秒数。
     * </p>
     * @return 清理间隔秒数
     */
    public long getCleanupIntervalSeconds() {
        return this.cleanupIntervalSeconds;
    }

    /**
     * <p>
     * 设置内存存储过期键清理间隔秒数。
     * </p>
     * @param cleanupIntervalSeconds 清理间隔秒数
     */
    public void setCleanupIntervalSeconds(long cleanupIntervalSeconds) {
        this.cleanupIntervalSeconds = cleanupIntervalSeconds <= 0
                ? DEFAULT_CLEANUP_INTERVAL_SECONDS
                : cleanupIntervalSeconds;
    }

    private static String normalizeHeaderName(String headerName, String defaultHeaderName) {
        return headerName == null || headerName.isBlank() ? defaultHeaderName : headerName.trim();
    }
}
