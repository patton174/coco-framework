package io.github.coco.feature.web.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

import io.github.coco.common.trace.CocoTraceContext;
import io.github.coco.feature.web.body.CocoCachedRequestBody;
import io.github.coco.feature.web.body.CocoRequestBodyMetadata;
import io.github.coco.feature.web.body.CocoRequestBodyResolver;
import io.github.coco.feature.web.body.CocoResolvedRequestBody;
import io.github.coco.feature.web.body.DefaultCocoRequestBodyResolver;
import io.github.coco.feature.web.context.payload.CocoWebPayloadParseStatus;
import io.github.coco.feature.web.context.target.CocoWebRequestTarget;
import io.github.coco.feature.web.context.target.CocoWebRequestTargetResolution;
import io.github.coco.feature.web.context.target.CocoWebRequestTargetResolver;
import io.github.coco.feature.web.context.target.DefaultCocoWebRequestTargetResolver;
import io.github.coco.feature.web.security.metadata.CocoWebRequestSecurityInput;
import io.github.coco.feature.web.security.metadata.CocoWebRequestSecurityInputResolver;
import io.github.coco.feature.web.security.metadata.CocoWebRequestSecurityMetadata;
import io.github.coco.feature.web.security.metadata.CocoWebRequestSecurityMetadataResolver;
import io.github.coco.feature.web.security.metadata.DefaultCocoWebRequestSecurityInputResolver;
import io.github.coco.feature.web.security.metadata.DefaultCocoWebRequestSecurityMetadataResolver;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Coco Web 默认请求上下文解析器。
 * <p>
 * 解析客户端 IP、请求头、请求参数、查询字符串、语言、协议、主机和内容类型，并对敏感字段做统一清洗。
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
public final class DefaultCocoWebRequestContextResolver implements CocoWebRequestContextResolver {

    private final CocoClientIpResolver clientIpResolver;

    private final CocoBrowserFingerprintResolver browserFingerprintResolver;

    private final CocoRequestHeaderResolver requestHeaderResolver;

    private final CocoRequestCookieResolver requestCookieResolver;

    private final CocoWebRequestTargetResolver requestTargetResolver;

    private final CocoRequestParameterResolver requestParameterResolver;

    private final CocoWebRequestSecurityInputResolver securityInputResolver;

    private final CocoWebRequestSecurityMetadataResolver securityMetadataResolver;

    private final CocoRequestBodyResolver requestBodyResolver;

    /**
     * <p>
     * 创建默认请求上下文解析器。
     * </p>
     * @param properties Web 请求上下文配置
     */
    public DefaultCocoWebRequestContextResolver(CocoWebContextProperties properties) {
        this(properties, (CocoBrowserFingerprintResolver) null);
    }

    /**
     * <p>
     * 创建默认请求上下文解析器。
     * </p>
     * @param properties Web 请求上下文配置
     * @param browserFingerprintResolver 浏览器指纹解析器
     */
    public DefaultCocoWebRequestContextResolver(CocoWebContextProperties properties,
            CocoBrowserFingerprintResolver browserFingerprintResolver) {
        this(properties, null, browserFingerprintResolver, null, null);
    }

    /**
     * <p>
     * 创建默认请求上下文解析器。
     * </p>
     * @param properties Web 请求上下文配置
     * @param clientIpResolver 客户端 IP 解析器
     * @param browserFingerprintResolver 浏览器指纹解析器
     */
    public DefaultCocoWebRequestContextResolver(CocoWebContextProperties properties,
            CocoClientIpResolver clientIpResolver,
            CocoBrowserFingerprintResolver browserFingerprintResolver) {
        this(properties, clientIpResolver, browserFingerprintResolver, null, null);
    }

    /**
     * <p>
     * 创建默认请求上下文解析器。
     * </p>
     * @param properties Web 请求上下文配置
     * @param clientIpResolver 客户端 IP 解析器
     * @param browserFingerprintResolver 浏览器指纹解析器
     * @param requestHeaderResolver 请求头解析器
     * @param requestParameterResolver 请求参数解析器
     */
    public DefaultCocoWebRequestContextResolver(CocoWebContextProperties properties,
            CocoClientIpResolver clientIpResolver,
            CocoBrowserFingerprintResolver browserFingerprintResolver, CocoRequestHeaderResolver requestHeaderResolver,
            CocoRequestParameterResolver requestParameterResolver) {
        this(properties, clientIpResolver, browserFingerprintResolver, requestHeaderResolver,
                requestParameterResolver, null);
    }

    /**
     * <p>
     * 创建默认请求上下文解析器。
     * </p>
     * @param properties Web 请求上下文配置
     * @param clientIpResolver 客户端 IP 解析器
     * @param browserFingerprintResolver 浏览器指纹解析器
     * @param requestHeaderResolver 请求头解析器
     * @param requestParameterResolver 请求参数解析器
     * @param securityInputResolver 请求安全输入解析器
     */
    public DefaultCocoWebRequestContextResolver(CocoWebContextProperties properties,
            CocoClientIpResolver clientIpResolver,
            CocoBrowserFingerprintResolver browserFingerprintResolver, CocoRequestHeaderResolver requestHeaderResolver,
            CocoRequestParameterResolver requestParameterResolver,
            CocoWebRequestSecurityInputResolver securityInputResolver) {
        this(properties, clientIpResolver, browserFingerprintResolver, requestHeaderResolver,
                requestParameterResolver, securityInputResolver, null);
    }

    /**
     * <p>
     * 创建默认请求上下文解析器。
     * </p>
     * @param properties Web 请求上下文配置
     * @param clientIpResolver 客户端 IP 解析器
     * @param browserFingerprintResolver 浏览器指纹解析器
     * @param requestHeaderResolver 请求头解析器
     * @param requestParameterResolver 请求参数解析器
     * @param securityInputResolver 请求安全输入解析器
     * @param securityMetadataResolver 请求安全元数据解析器
     */
    public DefaultCocoWebRequestContextResolver(CocoWebContextProperties properties,
            CocoClientIpResolver clientIpResolver,
            CocoBrowserFingerprintResolver browserFingerprintResolver, CocoRequestHeaderResolver requestHeaderResolver,
            CocoRequestParameterResolver requestParameterResolver,
            CocoWebRequestSecurityInputResolver securityInputResolver,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver) {
        this(properties, clientIpResolver, browserFingerprintResolver, requestHeaderResolver,
                null, null, requestParameterResolver, securityInputResolver, securityMetadataResolver);
    }

    /**
     * <p>
     * 创建默认请求上下文解析器。
     * </p>
     * @param properties Web 请求上下文配置
     * @param clientIpResolver 客户端 IP 解析器
     * @param browserFingerprintResolver 浏览器指纹解析器
     * @param requestHeaderResolver 请求头解析器
     * @param requestCookieResolver Cookie 解析器
     * @param requestTargetResolver Web 请求目标解析器
     * @param requestParameterResolver 请求参数解析器
     * @param securityInputResolver 请求安全输入解析器
     * @param securityMetadataResolver 请求安全元数据解析器
     */
    public DefaultCocoWebRequestContextResolver(CocoWebContextProperties properties,
            CocoClientIpResolver clientIpResolver,
            CocoBrowserFingerprintResolver browserFingerprintResolver, CocoRequestHeaderResolver requestHeaderResolver,
            CocoRequestCookieResolver requestCookieResolver, CocoWebRequestTargetResolver requestTargetResolver,
            CocoRequestParameterResolver requestParameterResolver,
            CocoWebRequestSecurityInputResolver securityInputResolver,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver) {
        this(properties, clientIpResolver, browserFingerprintResolver, requestHeaderResolver,
                requestCookieResolver, requestTargetResolver, requestParameterResolver, securityInputResolver,
                securityMetadataResolver, null);
    }

    /**
     * <p>
     * 创建默认请求上下文解析器。
     * </p>
     * @param properties Web 请求上下文配置
     * @param clientIpResolver 客户端 IP 解析器
     * @param browserFingerprintResolver 浏览器指纹解析器
     * @param requestHeaderResolver 请求头解析器
     * @param requestCookieResolver Cookie 解析器
     * @param requestTargetResolver Web 请求目标解析器
     * @param requestParameterResolver 请求参数解析器
     * @param securityInputResolver 请求安全输入解析器
     * @param securityMetadataResolver 请求安全元数据解析器
     * @param requestBodyResolver 请求体解析器
     */
    public DefaultCocoWebRequestContextResolver(CocoWebContextProperties properties,
            CocoClientIpResolver clientIpResolver,
            CocoBrowserFingerprintResolver browserFingerprintResolver, CocoRequestHeaderResolver requestHeaderResolver,
            CocoRequestCookieResolver requestCookieResolver, CocoWebRequestTargetResolver requestTargetResolver,
            CocoRequestParameterResolver requestParameterResolver,
            CocoWebRequestSecurityInputResolver securityInputResolver,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver,
            CocoRequestBodyResolver requestBodyResolver) {
        CocoWebContextProperties contextProperties = properties == null ? new CocoWebContextProperties() : properties;
        CocoWebParameterProperties parameterProperties = contextProperties.getParameter();
        this.clientIpResolver = clientIpResolver == null
                ? new DefaultCocoClientIpResolver(contextProperties)
                : clientIpResolver;
        this.browserFingerprintResolver = browserFingerprintResolver == null
                ? new DefaultCocoBrowserFingerprintResolver(contextProperties)
                : browserFingerprintResolver;
        this.requestHeaderResolver = requestHeaderResolver == null
                ? new DefaultCocoRequestHeaderResolver(contextProperties)
                : requestHeaderResolver;
        this.requestCookieResolver = requestCookieResolver == null
                ? new DefaultCocoRequestCookieResolver(contextProperties)
                : requestCookieResolver;
        this.requestTargetResolver = requestTargetResolver == null
                ? new DefaultCocoWebRequestTargetResolver(contextProperties)
                : requestTargetResolver;
        this.requestParameterResolver = requestParameterResolver == null
                ? new DefaultCocoRequestParameterResolver(parameterProperties)
                : requestParameterResolver;
        this.requestBodyResolver = requestBodyResolver == null
                ? new DefaultCocoRequestBodyResolver()
                : requestBodyResolver;
        this.securityInputResolver = securityInputResolver == null
                ? new DefaultCocoWebRequestSecurityInputResolver(contextProperties, this.requestHeaderResolver,
                        this.requestCookieResolver, this.requestParameterResolver, null, null, null,
                        this.requestBodyResolver)
                : securityInputResolver;
        this.securityMetadataResolver = securityMetadataResolver == null
                ? new DefaultCocoWebRequestSecurityMetadataResolver(null, null, null)
                : securityMetadataResolver;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoWebRequestSnapshot resolve(String traceId, HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        String effectiveTraceId = resolveTraceId(traceId);
        return CocoWebRequestSnapshotAttributes.get(checkedRequest)
                .filter(snapshot -> reusable(snapshot, effectiveTraceId, checkedRequest))
                .orElseGet(() -> resolveAndCache(effectiveTraceId, checkedRequest));
    }

    private CocoWebRequestSnapshot resolveAndCache(String traceId, HttpServletRequest request) {
        String method = request.getMethod();
        CocoWebRequestTargetResolution targetResolution = this.requestTargetResolver.resolveResolution(request);
        CocoWebRequestTarget requestTarget = targetResolution.target();
        String path = requestTarget.path();
        CocoClientIpResolution clientIpResolution = this.clientIpResolver.resolveResolution(request);
        CocoBrowserFingerprint browserFingerprint = this.browserFingerprintResolver.resolve(request);
        CocoWebRequestSecurityInput securityInput = this.securityInputResolver.resolve(request, method, path);
        CocoResolvedRequestBody resolvedBody = this.requestBodyResolver.resolve(request);
        CocoRequestBodyMetadata requestBody = resolvedBody.metadata();
        CocoWebRequestSecurityMetadata securityMetadata = this.securityMetadataResolver.resolve(securityInput);
        CocoWebRequestParameters parameterSnapshot = this.requestParameterResolver.resolveParameterSnapshot(request);
        CocoWebPayloadParseStatus payloadParseStatus = this.requestParameterResolver.resolvePayloadParseStatus(request);
        Map<String, String> cookies = this.requestCookieResolver.resolveIncludedCookies(request);
        CocoWebRequestSnapshot snapshot = new CocoWebRequestSnapshot(traceId, method, path,
                parameterSnapshot.queryString(),
                clientIpResolution.clientIp(), request.getHeader("User-Agent"),
                resolveLocale(request),
                requestTarget.scheme(), requestTarget.host(), requestTarget.port(),
                request.getContentType(), this.requestHeaderResolver.resolveIncludedHeaders(request),
                cookies, parameterSnapshot.parameters(), parameterSnapshot.queryParameters(),
                parameterSnapshot.payloadParameters(), securityInput, requestBody, securityMetadata, browserFingerprint,
                clientIpResolution, targetResolution, parameterSnapshot.payloadSource(), null, payloadParseStatus,
                Map.of());
        CocoWebRequestSnapshotAttributes.set(request, snapshot, headerFingerprint(request));
        return snapshot;
    }

    private boolean reusable(CocoWebRequestSnapshot snapshot, String traceId, HttpServletRequest request) {
        CocoWebRequestTarget requestTarget = this.requestTargetResolver.resolve(request);
        return snapshot.traceId().equals(traceId)
                && Objects.equals(snapshot.method(), normalizeMethod(request.getMethod()))
                && Objects.equals(snapshot.path(), requestTarget.path())
                && Objects.equals(snapshot.securityInput().queryString(), normalizeOptional(request.getQueryString()))
                && CocoWebRequestSnapshotAttributes.headerFingerprint(request)
                        .filter(fingerprint -> fingerprint.equals(headerFingerprint(request)))
                .isPresent()
                && reusableBody(snapshot, request);
    }

    private boolean reusableBody(CocoWebRequestSnapshot snapshot, HttpServletRequest request) {
        return Optional.ofNullable(this.requestBodyResolver.resolve(request))
                .map(CocoResolvedRequestBody::effectiveBody)
                .filter(CocoCachedRequestBody::cached)
                .map(CocoCachedRequestBody::sha256)
                .map(sha256 -> Objects.equals(sha256, snapshot.securityInput().bodySha256()))
                .orElse(true);
    }

    private static String resolveLocale(HttpServletRequest request) {
        Locale locale = request.getLocale();
        return locale == null ? null : locale.toLanguageTag();
    }

    private static String resolveTraceId(String traceId) {
        return traceId == null || traceId.isBlank() ? CocoTraceContext.getOrCreateTraceId() : traceId.trim();
    }

    private static String normalizeMethod(String method) {
        return method == null || method.isBlank() ? null : method.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String headerFingerprint(HttpServletRequest request) {
        List<String> names = Collections.list(request.getHeaderNames()).stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .sorted()
                .toList();
        StringJoiner joiner = new StringJoiner("|");
        for (String name : names) {
            List<String> values = new ArrayList<>(Collections.list(request.getHeaders(name)));
            values.replaceAll(value -> value == null ? "" : value.trim());
            Collections.sort(values);
            joiner.add(framedHeader(name, values));
        }
        return joiner.toString();
    }

    private static String framedHeader(String name, List<String> values) {
        StringBuilder builder = new StringBuilder();
        builder.append(name.length()).append(':').append(name).append('#').append(values.size());
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index);
            builder.append('|').append(index).append('=').append(value.length()).append(':').append(value);
        }
        return builder.toString();
    }

}
