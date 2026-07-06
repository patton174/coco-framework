package io.github.coco.feature.web.context;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco Web 请求上下文配置属性。
 * <p>
 * 控制 Web 入口解析客户端 IP、请求头、请求参数、浏览器指纹和规范化输入的策略。
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
public class CocoWebContextProperties {

    private static final int DEFAULT_MAX_HEADER_VALUE_LENGTH = 256;

    private static final int DEFAULT_MAX_COOKIE_VALUE_LENGTH = 128;

    private static final List<String> DEFAULT_CLIENT_IP_HEADER_NAMES = List.of(
            "Forwarded",
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR");

    private static final Set<String> DEFAULT_INCLUDED_HEADER_NAMES = Set.of(
            "host", "content-type", "user-agent", "accept", "accept-language", "referer", "origin");

    private static final Set<String> DEFAULT_MASKED_HEADER_NAMES = Set.of(
            "authorization", "cookie", "set-cookie", "x-api-key", "x-auth-token");

    private static final Set<String> DEFAULT_MASKED_COOKIE_NAMES = Set.of(
            "jsessionid", "session", "sessionid", "token", "access_token", "refresh_token");

    private static final Set<String> DEFAULT_SECURITY_HEADER_NAMES = Set.of(
            "content-md5", "content-type", "x-coco-app-id", "x-coco-timestamp", "x-coco-nonce",
            "x-coco-sign", "x-coco-signature", "x-coco-encrypted", "x-coco-key-id", "x-coco-iv",
            "x-coco-algorithm", "x-coco-sign-algorithm");

    private static final Set<String> DEFAULT_CANONICAL_HEADER_NAMES = Set.of(
            "content-md5", "content-type", "x-coco-app-id", "x-coco-timestamp", "x-coco-nonce",
            "x-coco-key-id", "x-coco-iv", "x-coco-algorithm", "x-coco-sign-algorithm");

    private static final Set<String> DEFAULT_FINGERPRINT_HEADER_NAMES = Set.of(
            "user-agent", "accept", "accept-language", "dnt", "sec-ch-ua", "sec-ch-ua-mobile",
            "sec-ch-ua-platform", "sec-ch-ua-platform-version", "sec-ch-ua-arch", "sec-ch-ua-bitness",
            "sec-ch-ua-model", "sec-ch-ua-full-version-list");

    private List<String> clientIpHeaderNames = DEFAULT_CLIENT_IP_HEADER_NAMES;

    private Set<String> trustedProxyCidrs = Set.of();

    private boolean includeHeaders = true;

    private Set<String> includedHeaderNames = DEFAULT_INCLUDED_HEADER_NAMES;

    private Set<String> maskedHeaderNames = DEFAULT_MASKED_HEADER_NAMES;

    private Set<String> securityHeaderNames = DEFAULT_SECURITY_HEADER_NAMES;

    private Set<String> canonicalHeaderNames = DEFAULT_CANONICAL_HEADER_NAMES;

    private Set<String> fingerprintHeaderNames = DEFAULT_FINGERPRINT_HEADER_NAMES;

    private int maxHeaderValueLength = DEFAULT_MAX_HEADER_VALUE_LENGTH;

    private Set<String> includedCookieNames = Set.of();

    private Set<String> maskedCookieNames = DEFAULT_MASKED_COOKIE_NAMES;

    private int maxCookieValueLength = DEFAULT_MAX_COOKIE_VALUE_LENGTH;

    @NestedConfigurationProperty
    private CocoWebParameterProperties parameter = new CocoWebParameterProperties();

    @NestedConfigurationProperty
    private CocoWebRequestCanonicalizationProperties canonicalization =
            new CocoWebRequestCanonicalizationProperties();

    /**
     * <p>
     * 返回用于解析客户端 IP 的请求头名称，按顺序匹配。
     * </p>
     * @return 客户端 IP 请求头名称
     */
    public List<String> getClientIpHeaderNames() {
        return this.clientIpHeaderNames;
    }

    /**
     * <p>
     * 返回可信代理 IP 或 CIDR 集合。
     * </p>
     * @return 可信代理 IP 或 CIDR 集合
     */
    public Set<String> getTrustedProxyCidrs() {
        return this.trustedProxyCidrs;
    }

    /**
     * <p>
     * 设置可信代理 IP 或 CIDR 集合。
     * </p>
     * @param trustedProxyCidrs 可信代理 IP 或 CIDR 集合
     */
    public void setTrustedProxyCidrs(Set<String> trustedProxyCidrs) {
        if (trustedProxyCidrs == null || trustedProxyCidrs.isEmpty()) {
            this.trustedProxyCidrs = Set.of();
            return;
        }
        Set<String> normalizedCidrs = new LinkedHashSet<>();
        for (String cidr : trustedProxyCidrs) {
            if (cidr != null && !cidr.isBlank()) {
                normalizedCidrs.add(cidr.trim());
            }
        }
        this.trustedProxyCidrs = normalizedCidrs.isEmpty() ? Set.of() : Set.copyOf(normalizedCidrs);
    }

    /**
     * <p>
     * 设置用于解析客户端 IP 的请求头名称。
     * </p>
     * @param clientIpHeaderNames 客户端 IP 请求头名称
     */
    public void setClientIpHeaderNames(List<String> clientIpHeaderNames) {
        if (clientIpHeaderNames == null || clientIpHeaderNames.isEmpty()) {
            this.clientIpHeaderNames = DEFAULT_CLIENT_IP_HEADER_NAMES;
            return;
        }
        List<String> normalizedNames = clientIpHeaderNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        this.clientIpHeaderNames = normalizedNames.isEmpty()
                ? DEFAULT_CLIENT_IP_HEADER_NAMES
                : List.copyOf(normalizedNames);
    }

    /**
     * <p>
     * 返回是否将配置的请求头写入请求上下文。
     * </p>
     * @return 写入请求头时返回 {@code true}
     */
    public boolean isIncludeHeaders() {
        return this.includeHeaders;
    }

    /**
     * <p>
     * 设置是否将配置的请求头写入请求上下文。
     * </p>
     * @param includeHeaders 是否写入请求头
     */
    public void setIncludeHeaders(boolean includeHeaders) {
        this.includeHeaders = includeHeaders;
    }

    /**
     * <p>
     * 返回允许写入请求上下文的请求头名称。
     * </p>
     * @return 请求头名称集合
     */
    public Set<String> getIncludedHeaderNames() {
        return this.includedHeaderNames;
    }

    /**
     * <p>
     * 设置允许写入请求上下文的请求头名称。
     * </p>
     * @param includedHeaderNames 请求头名称集合
     */
    public void setIncludedHeaderNames(Set<String> includedHeaderNames) {
        this.includedHeaderNames = normalizeHeaderNames(includedHeaderNames, DEFAULT_INCLUDED_HEADER_NAMES);
    }

    /**
     * <p>
     * 返回需要掩码的请求头名称。
     * </p>
     * @return 请求头名称集合
     */
    public Set<String> getMaskedHeaderNames() {
        return this.maskedHeaderNames;
    }

    /**
     * <p>
     * 设置需要掩码的请求头名称。
     * </p>
     * @param maskedHeaderNames 请求头名称集合
     */
    public void setMaskedHeaderNames(Set<String> maskedHeaderNames) {
        this.maskedHeaderNames = normalizeHeaderNames(maskedHeaderNames, DEFAULT_MASKED_HEADER_NAMES);
    }

    /**
     * <p>
     * 返回安全能力相关请求头名称。
     * </p>
     * @return 安全能力相关请求头名称集合
     */
    public Set<String> getSecurityHeaderNames() {
        return this.securityHeaderNames;
    }

    /**
     * <p>
     * 设置安全能力相关请求头名称。
     * </p>
     * @param securityHeaderNames 安全能力相关请求头名称集合
     */
    public void setSecurityHeaderNames(Set<String> securityHeaderNames) {
        this.securityHeaderNames = normalizeHeaderNames(securityHeaderNames, DEFAULT_SECURITY_HEADER_NAMES);
    }

    /**
     * <p>
     * 返回默认参与签名规范化的请求头名称。
     * </p>
     * @return 默认参与签名规范化的请求头名称集合
     */
    public Set<String> getCanonicalHeaderNames() {
        return this.canonicalHeaderNames;
    }

    /**
     * <p>
     * 设置默认参与签名规范化的请求头名称。
     * </p>
     * @param canonicalHeaderNames 默认参与签名规范化的请求头名称集合
     */
    public void setCanonicalHeaderNames(Set<String> canonicalHeaderNames) {
        this.canonicalHeaderNames = normalizeHeaderNames(canonicalHeaderNames, DEFAULT_CANONICAL_HEADER_NAMES);
    }

    /**
     * <p>
     * 返回参与浏览器指纹生成的请求头名称。
     * </p>
     * @return 参与浏览器指纹生成的请求头名称集合
     */
    public Set<String> getFingerprintHeaderNames() {
        return this.fingerprintHeaderNames;
    }

    /**
     * <p>
     * 设置参与浏览器指纹生成的请求头名称。
     * </p>
     * @param fingerprintHeaderNames 参与浏览器指纹生成的请求头名称集合
     */
    public void setFingerprintHeaderNames(Set<String> fingerprintHeaderNames) {
        this.fingerprintHeaderNames = normalizeHeaderNames(fingerprintHeaderNames, DEFAULT_FINGERPRINT_HEADER_NAMES);
    }

    /**
     * <p>
     * 返回单个请求头值最大采集长度。
     * </p>
     * @return 单个请求头值最大采集长度
     */
    public int getMaxHeaderValueLength() {
        return this.maxHeaderValueLength;
    }

    /**
     * <p>
     * 设置单个请求头值最大采集长度。
     * </p>
     * @param maxHeaderValueLength 单个请求头值最大采集长度
     */
    public void setMaxHeaderValueLength(int maxHeaderValueLength) {
        this.maxHeaderValueLength = maxHeaderValueLength <= 0
                ? DEFAULT_MAX_HEADER_VALUE_LENGTH
                : maxHeaderValueLength;
    }

    /**
     * <p>
     * 返回允许写入请求上下文的 Cookie 名称。
     * </p>
     * @return Cookie 名称集合
     */
    public Set<String> getIncludedCookieNames() {
        return this.includedCookieNames;
    }

    /**
     * <p>
     * 设置允许写入请求上下文的 Cookie 名称。
     * </p>
     * @param includedCookieNames Cookie 名称集合
     */
    public void setIncludedCookieNames(Set<String> includedCookieNames) {
        this.includedCookieNames = normalizeCookieNames(includedCookieNames, Set.of());
    }

    /**
     * <p>
     * 返回需要掩码的 Cookie 名称。
     * </p>
     * @return Cookie 名称集合
     */
    public Set<String> getMaskedCookieNames() {
        return this.maskedCookieNames;
    }

    /**
     * <p>
     * 设置需要掩码的 Cookie 名称。
     * </p>
     * @param maskedCookieNames Cookie 名称集合
     */
    public void setMaskedCookieNames(Set<String> maskedCookieNames) {
        this.maskedCookieNames = normalizeCookieNames(maskedCookieNames, DEFAULT_MASKED_COOKIE_NAMES);
    }

    /**
     * <p>
     * 返回单个 Cookie 值最大采集长度。
     * </p>
     * @return 单个 Cookie 值最大采集长度
     */
    public int getMaxCookieValueLength() {
        return this.maxCookieValueLength;
    }

    /**
     * <p>
     * 设置单个 Cookie 值最大采集长度。
     * </p>
     * @param maxCookieValueLength 单个 Cookie 值最大采集长度
     */
    public void setMaxCookieValueLength(int maxCookieValueLength) {
        this.maxCookieValueLength = maxCookieValueLength <= 0
                ? DEFAULT_MAX_COOKIE_VALUE_LENGTH
                : maxCookieValueLength;
    }

    /**
     * <p>
     * 返回 Web 请求参数配置属性。
     * </p>
     * @return Web 请求参数配置属性
     */
    public CocoWebParameterProperties getParameter() {
        return this.parameter;
    }

    /**
     * <p>
     * 设置 Web 请求参数配置属性。
     * </p>
     * @param parameter Web 请求参数配置属性
     */
    public void setParameter(CocoWebParameterProperties parameter) {
        this.parameter = parameter == null ? new CocoWebParameterProperties() : parameter;
    }

    /**
     * <p>
     * 返回 Web 请求规范化配置属性。
     * </p>
     * @return Web 请求规范化配置属性
     */
    public CocoWebRequestCanonicalizationProperties getCanonicalization() {
        return this.canonicalization;
    }

    /**
     * <p>
     * 设置 Web 请求规范化配置属性。
     * </p>
     * @param canonicalization Web 请求规范化配置属性
     */
    public void setCanonicalization(CocoWebRequestCanonicalizationProperties canonicalization) {
        this.canonicalization = canonicalization == null
                ? new CocoWebRequestCanonicalizationProperties()
                : canonicalization;
    }

    private static Set<String> normalizeHeaderNames(Set<String> headerNames, Set<String> defaults) {
        if (headerNames == null || headerNames.isEmpty()) {
            return defaults;
        }
        Set<String> normalizedNames = new LinkedHashSet<>();
        for (String name : headerNames) {
            if (name != null && !name.isBlank()) {
                normalizedNames.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        return normalizedNames.isEmpty() ? defaults : Set.copyOf(normalizedNames);
    }

    private static Set<String> normalizeCookieNames(Set<String> cookieNames, Set<String> defaults) {
        if (cookieNames == null || cookieNames.isEmpty()) {
            return defaults;
        }
        Set<String> normalizedNames = new LinkedHashSet<>();
        for (String name : cookieNames) {
            if (name != null && !name.isBlank()) {
                normalizedNames.add(name.trim());
            }
        }
        return normalizedNames.isEmpty() ? defaults : Set.copyOf(normalizedNames);
    }
}
