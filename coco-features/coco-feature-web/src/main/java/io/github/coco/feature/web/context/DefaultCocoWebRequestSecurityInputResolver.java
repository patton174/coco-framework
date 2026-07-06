package io.github.coco.feature.web.context;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.coco.feature.web.body.CocoCachedBodyHttpServletRequest;
import io.github.coco.feature.web.body.CocoCachedRequestBody;
import io.github.coco.feature.web.encryption.CocoEncryptionProperties;
import io.github.coco.feature.web.replay.CocoReplayProperties;
import io.github.coco.feature.web.signature.CocoSignatureProperties;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 默认 Coco Web 请求安全输入解析器。
 * <p>
 * 组合原始查询字符串、原始请求参数、安全请求头、签名规范化请求头和缓存请求体摘要，生成安全能力共用输入。
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
public final class DefaultCocoWebRequestSecurityInputResolver implements CocoWebRequestSecurityInputResolver {

    private final Set<String> securityHeaderNames;

    private final Set<String> canonicalHeaderNames;

    private final Set<String> canonicalCookieNames;

    private final CocoRequestHeaderResolver requestHeaderResolver;

    private final CocoRequestCookieResolver requestCookieResolver;

    private final CocoRequestParameterResolver requestParameterResolver;

    /**
     * <p>
     * 创建默认 Coco Web 请求安全输入解析器。
     * </p>
     * @param properties Web 请求上下文配置属性
     * @param requestHeaderResolver 请求头解析器
     * @param requestParameterResolver 请求参数解析器
     */
    public DefaultCocoWebRequestSecurityInputResolver(CocoWebContextProperties properties,
            CocoRequestHeaderResolver requestHeaderResolver, CocoRequestParameterResolver requestParameterResolver) {
        this(properties, requestHeaderResolver, requestParameterResolver, null, null, null);
    }

    /**
     * <p>
     * 创建默认 Coco Web 请求安全输入解析器。
     * </p>
     * @param properties Web 请求上下文配置属性
     * @param requestHeaderResolver 请求头解析器
     * @param requestParameterResolver 请求参数解析器
     * @param signatureProperties 请求签名配置属性
     * @param encryptionProperties 请求加密配置属性
     */
    public DefaultCocoWebRequestSecurityInputResolver(CocoWebContextProperties properties,
            CocoRequestHeaderResolver requestHeaderResolver, CocoRequestParameterResolver requestParameterResolver,
            CocoSignatureProperties signatureProperties, CocoEncryptionProperties encryptionProperties) {
        this(properties, requestHeaderResolver, requestParameterResolver, signatureProperties, encryptionProperties,
                null);
    }

    /**
     * <p>
     * 创建默认 Coco Web 请求安全输入解析器。
     * </p>
     * @param properties Web 请求上下文配置属性
     * @param requestHeaderResolver 请求头解析器
     * @param requestParameterResolver 请求参数解析器
     * @param signatureProperties 请求签名配置属性
     * @param encryptionProperties 请求加密配置属性
     * @param replayProperties 防重放配置属性
     */
    public DefaultCocoWebRequestSecurityInputResolver(CocoWebContextProperties properties,
            CocoRequestHeaderResolver requestHeaderResolver, CocoRequestParameterResolver requestParameterResolver,
            CocoSignatureProperties signatureProperties, CocoEncryptionProperties encryptionProperties,
            CocoReplayProperties replayProperties) {
        this(properties, requestHeaderResolver, null, requestParameterResolver, signatureProperties,
                encryptionProperties, replayProperties);
    }

    /**
     * <p>
     * 创建默认 Coco Web 请求安全输入解析器。
     * </p>
     * @param properties Web 请求上下文配置属性
     * @param requestHeaderResolver 请求头解析器
     * @param requestCookieResolver 请求 Cookie 解析器
     * @param requestParameterResolver 请求参数解析器
     * @param signatureProperties 请求签名配置属性
     * @param encryptionProperties 请求加密配置属性
     * @param replayProperties 防重放配置属性
     */
    public DefaultCocoWebRequestSecurityInputResolver(CocoWebContextProperties properties,
            CocoRequestHeaderResolver requestHeaderResolver, CocoRequestCookieResolver requestCookieResolver,
            CocoRequestParameterResolver requestParameterResolver, CocoSignatureProperties signatureProperties,
            CocoEncryptionProperties encryptionProperties, CocoReplayProperties replayProperties) {
        CocoWebContextProperties contextProperties = properties == null ? new CocoWebContextProperties() : properties;
        this.securityHeaderNames = securityHeaderNames(contextProperties, signatureProperties, encryptionProperties,
                replayProperties);
        this.canonicalHeaderNames = canonicalHeaderNames(contextProperties, signatureProperties, encryptionProperties,
                replayProperties);
        this.canonicalCookieNames = contextProperties.getCanonicalCookieNames();
        this.requestHeaderResolver = requestHeaderResolver == null
                ? new DefaultCocoRequestHeaderResolver(contextProperties)
                : requestHeaderResolver;
        this.requestCookieResolver = requestCookieResolver == null
                ? new DefaultCocoRequestCookieResolver(contextProperties)
                : requestCookieResolver;
        this.requestParameterResolver = requestParameterResolver == null
                ? new DefaultCocoRequestParameterResolver(new CocoWebParameterProperties())
                : requestParameterResolver;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoWebRequestSecurityInput resolve(HttpServletRequest request, String method, String path) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        String rawQueryString = this.requestParameterResolver.resolveRawQueryString(checkedRequest);
        Map<String, List<String>> rawQueryParameters = this.requestParameterResolver.resolveRawQueryParameters(
                checkedRequest);
        Map<String, List<String>> rawPayloadParameters = this.requestParameterResolver.resolveRawPayloadParameters(
                checkedRequest);
        Map<String, List<String>> rawParameters = this.requestParameterResolver.resolveRawParameters(checkedRequest);
        Map<String, String> securityHeaders = this.requestHeaderResolver.resolveSelectedHeaders(checkedRequest,
                this.securityHeaderNames, false);
        Map<String, List<String>> canonicalHeaderValues = this.requestHeaderResolver.resolveSelectedHeaderValues(
                checkedRequest,
                this.canonicalHeaderNames, false);
        Map<String, String> canonicalHeaders = joinHeaders(canonicalHeaderValues);
        Map<String, String> canonicalCookies = this.requestCookieResolver.resolveSelectedCookies(checkedRequest,
                this.canonicalCookieNames, false);
        CocoCachedRequestBody cachedBody = CocoCachedBodyHttpServletRequest.cachedBody(checkedRequest)
                .orElse(CocoCachedRequestBody.empty());
        return new CocoWebRequestSecurityInput(method, path, rawQueryString, rawParameters, rawQueryParameters,
                rawPayloadParameters, securityHeaders, canonicalHeaders, cachedBody.sha256(),
                cachedBody.cached() ? cachedBody.length() : null, cachedBody.cached(), canonicalHeaderValues,
                canonicalCookies);
    }

    private static Set<String> securityHeaderNames(CocoWebContextProperties properties,
            CocoSignatureProperties signatureProperties, CocoEncryptionProperties encryptionProperties,
            CocoReplayProperties replayProperties) {
        LinkedHashSet<String> headerNames = new LinkedHashSet<>(properties.getSecurityHeaderNames());
        CocoSignatureProperties signature = signatureProperties == null
                ? new CocoSignatureProperties()
                : signatureProperties;
        CocoEncryptionProperties encryption = encryptionProperties == null
                ? new CocoEncryptionProperties()
                : encryptionProperties;
        CocoReplayProperties replay = replayProperties == null ? new CocoReplayProperties() : replayProperties;
        add(headerNames, signature.getAppIdHeaderName());
        add(headerNames, signature.getKeyIdHeaderName());
        add(headerNames, signature.getTimestampHeaderName());
        add(headerNames, signature.getNonceHeaderName());
        add(headerNames, signature.getSignatureHeaderName());
        add(headerNames, signature.getSignatureFallbackHeaderName());
        add(headerNames, signature.getAlgorithmHeaderName());
        add(headerNames, encryption.getEncryptedHeaderName());
        add(headerNames, encryption.getAppIdHeaderName());
        add(headerNames, encryption.getKeyIdHeaderName());
        add(headerNames, encryption.getIvHeaderName());
        add(headerNames, encryption.getAlgorithmHeaderName());
        add(headerNames, replay.getAppIdHeaderName());
        add(headerNames, replay.getKeyIdHeaderName());
        add(headerNames, replay.getTimestampHeaderName());
        add(headerNames, replay.getNonceHeaderName());
        return Set.copyOf(headerNames);
    }

    private static Set<String> canonicalHeaderNames(CocoWebContextProperties properties,
            CocoSignatureProperties signatureProperties, CocoEncryptionProperties encryptionProperties,
            CocoReplayProperties replayProperties) {
        LinkedHashSet<String> headerNames = new LinkedHashSet<>(properties.getCanonicalHeaderNames());
        CocoSignatureProperties signature = signatureProperties == null
                ? new CocoSignatureProperties()
                : signatureProperties;
        CocoEncryptionProperties encryption = encryptionProperties == null
                ? new CocoEncryptionProperties()
                : encryptionProperties;
        CocoReplayProperties replay = replayProperties == null ? new CocoReplayProperties() : replayProperties;
        add(headerNames, signature.getAppIdHeaderName());
        add(headerNames, signature.getKeyIdHeaderName());
        add(headerNames, signature.getTimestampHeaderName());
        add(headerNames, signature.getNonceHeaderName());
        add(headerNames, signature.getAlgorithmHeaderName());
        add(headerNames, encryption.getAppIdHeaderName());
        add(headerNames, encryption.getKeyIdHeaderName());
        add(headerNames, encryption.getIvHeaderName());
        add(headerNames, encryption.getAlgorithmHeaderName());
        add(headerNames, replay.getAppIdHeaderName());
        add(headerNames, replay.getKeyIdHeaderName());
        add(headerNames, replay.getTimestampHeaderName());
        add(headerNames, replay.getNonceHeaderName());
        return Set.copyOf(headerNames);
    }

    private static void add(Set<String> headerNames, String headerName) {
        String normalizedName = headerName == null || headerName.isBlank()
                ? null
                : headerName.trim().toLowerCase(Locale.ROOT);
        if (normalizedName != null) {
            headerNames.add(normalizedName);
        }
    }

    private static Map<String, String> joinHeaders(Map<String, List<String>> headerValues) {
        if (headerValues == null || headerValues.isEmpty()) {
            return Map.of();
        }
        Map<String, String> joined = new LinkedHashMap<>();
        headerValues.forEach((name, values) -> {
            if (values != null && !values.isEmpty()) {
                joined.put(name, String.join(",", values));
            }
        });
        return joined;
    }
}
