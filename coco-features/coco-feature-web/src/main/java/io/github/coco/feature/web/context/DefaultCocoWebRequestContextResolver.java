package io.github.coco.feature.web.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import io.github.coco.feature.web.accesslog.CocoAccessLogCaptureProperties;
import io.github.coco.feature.web.body.CocoCachedBodyHttpServletRequest;
import io.github.coco.feature.web.body.CocoCachedRequestBody;
import io.github.coco.feature.web.body.CocoRequestBodyMetadata;
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

    private final CocoRequestParameterResolver requestParameterResolver;

    private final CocoWebRequestSecurityInputResolver securityInputResolver;

    private final CocoWebRequestSecurityMetadataResolver securityMetadataResolver;

    /**
     * <p>
     * 创建默认请求上下文解析器。
     * </p>
     * @param properties Web 请求上下文配置
     * @param accessLogProperties 访问日志采集配置
     */
    public DefaultCocoWebRequestContextResolver(CocoWebContextProperties properties,
            CocoAccessLogCaptureProperties accessLogProperties) {
        this(properties, accessLogProperties, null);
    }

    /**
     * <p>
     * 创建默认请求上下文解析器。
     * </p>
     * @param properties Web 请求上下文配置
     * @param accessLogProperties 访问日志采集配置
     * @param browserFingerprintResolver 浏览器指纹解析器
     */
    public DefaultCocoWebRequestContextResolver(CocoWebContextProperties properties,
            CocoAccessLogCaptureProperties accessLogProperties,
            CocoBrowserFingerprintResolver browserFingerprintResolver) {
        this(properties, accessLogProperties, null, browserFingerprintResolver, null, null);
    }

    /**
     * <p>
     * 创建默认请求上下文解析器。
     * </p>
     * @param properties Web 请求上下文配置
     * @param accessLogProperties 访问日志采集配置
     * @param clientIpResolver 客户端 IP 解析器
     * @param browserFingerprintResolver 浏览器指纹解析器
     */
    public DefaultCocoWebRequestContextResolver(CocoWebContextProperties properties,
            CocoAccessLogCaptureProperties accessLogProperties, CocoClientIpResolver clientIpResolver,
            CocoBrowserFingerprintResolver browserFingerprintResolver) {
        this(properties, accessLogProperties, clientIpResolver, browserFingerprintResolver, null, null);
    }

    /**
     * <p>
     * 创建默认请求上下文解析器。
     * </p>
     * @param properties Web 请求上下文配置
     * @param accessLogProperties 访问日志采集配置
     * @param clientIpResolver 客户端 IP 解析器
     * @param browserFingerprintResolver 浏览器指纹解析器
     * @param requestHeaderResolver 请求头解析器
     * @param requestParameterResolver 请求参数解析器
     */
    public DefaultCocoWebRequestContextResolver(CocoWebContextProperties properties,
            CocoAccessLogCaptureProperties accessLogProperties, CocoClientIpResolver clientIpResolver,
            CocoBrowserFingerprintResolver browserFingerprintResolver, CocoRequestHeaderResolver requestHeaderResolver,
            CocoRequestParameterResolver requestParameterResolver) {
        this(properties, accessLogProperties, clientIpResolver, browserFingerprintResolver, requestHeaderResolver,
                requestParameterResolver, null);
    }

    /**
     * <p>
     * 创建默认请求上下文解析器。
     * </p>
     * @param properties Web 请求上下文配置
     * @param accessLogProperties 访问日志采集配置
     * @param clientIpResolver 客户端 IP 解析器
     * @param browserFingerprintResolver 浏览器指纹解析器
     * @param requestHeaderResolver 请求头解析器
     * @param requestParameterResolver 请求参数解析器
     * @param securityInputResolver 请求安全输入解析器
     */
    public DefaultCocoWebRequestContextResolver(CocoWebContextProperties properties,
            CocoAccessLogCaptureProperties accessLogProperties, CocoClientIpResolver clientIpResolver,
            CocoBrowserFingerprintResolver browserFingerprintResolver, CocoRequestHeaderResolver requestHeaderResolver,
            CocoRequestParameterResolver requestParameterResolver,
            CocoWebRequestSecurityInputResolver securityInputResolver) {
        this(properties, accessLogProperties, clientIpResolver, browserFingerprintResolver, requestHeaderResolver,
                requestParameterResolver, securityInputResolver, null);
    }

    /**
     * <p>
     * 创建默认请求上下文解析器。
     * </p>
     * @param properties Web 请求上下文配置
     * @param accessLogProperties 访问日志采集配置
     * @param clientIpResolver 客户端 IP 解析器
     * @param browserFingerprintResolver 浏览器指纹解析器
     * @param requestHeaderResolver 请求头解析器
     * @param requestParameterResolver 请求参数解析器
     * @param securityInputResolver 请求安全输入解析器
     * @param securityMetadataResolver 请求安全元数据解析器
     */
    public DefaultCocoWebRequestContextResolver(CocoWebContextProperties properties,
            CocoAccessLogCaptureProperties accessLogProperties, CocoClientIpResolver clientIpResolver,
            CocoBrowserFingerprintResolver browserFingerprintResolver, CocoRequestHeaderResolver requestHeaderResolver,
            CocoRequestParameterResolver requestParameterResolver,
            CocoWebRequestSecurityInputResolver securityInputResolver,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver) {
        CocoWebContextProperties contextProperties = properties == null ? new CocoWebContextProperties() : properties;
        CocoWebParameterProperties parameterProperties = accessLogProperties == null
                ? contextProperties.getParameter()
                : CocoWebParameterProperties.fromAccessLog(accessLogProperties);
        this.clientIpResolver = clientIpResolver == null
                ? new DefaultCocoClientIpResolver(contextProperties)
                : clientIpResolver;
        this.browserFingerprintResolver = browserFingerprintResolver == null
                ? new DefaultCocoBrowserFingerprintResolver(contextProperties)
                : browserFingerprintResolver;
        this.requestHeaderResolver = requestHeaderResolver == null
                ? new DefaultCocoRequestHeaderResolver(contextProperties)
                : requestHeaderResolver;
        this.requestParameterResolver = requestParameterResolver == null
                ? new DefaultCocoRequestParameterResolver(parameterProperties)
                : requestParameterResolver;
        this.securityInputResolver = securityInputResolver == null
                ? new DefaultCocoWebRequestSecurityInputResolver(contextProperties, this.requestHeaderResolver,
                        this.requestParameterResolver)
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
        return CocoWebRequestSnapshotAttributes.get(checkedRequest)
                .filter(snapshot -> reusable(snapshot, traceId, checkedRequest))
                .orElseGet(() -> resolveAndCache(traceId, checkedRequest));
    }

    private CocoWebRequestSnapshot resolveAndCache(String traceId, HttpServletRequest request) {
        String method = request.getMethod();
        String path = resolvePath(request);
        CocoClientIpResolution clientIpResolution = this.clientIpResolver.resolveResolution(request);
        CocoBrowserFingerprint browserFingerprint = this.browserFingerprintResolver.resolve(request);
        CocoWebRequestSecurityInput securityInput = this.securityInputResolver.resolve(request, method, path);
        CocoRequestBodyMetadata requestBody = CocoRequestBodyMetadata.from(request);
        CocoWebRequestSecurityMetadata securityMetadata = this.securityMetadataResolver.resolve(securityInput);
        Map<String, List<String>> parameters = this.requestParameterResolver.resolveParameters(request);
        Map<String, List<String>> queryParameters = this.requestParameterResolver.resolveQueryParameters(request);
        Map<String, List<String>> payloadParameters = this.requestParameterResolver.resolvePayloadParameters(request);
        CocoWebRequestSnapshot snapshot = new CocoWebRequestSnapshot(traceId, method, path, resolveQueryString(request),
                clientIpResolution.clientIp(), request.getHeader("User-Agent"),
                resolveLocale(request),
                request.getScheme(), request.getServerName(), request.getServerPort(),
                request.getContentType(), this.requestHeaderResolver.resolveIncludedHeaders(request),
                parameters, queryParameters, payloadParameters, securityInput, requestBody, securityMetadata,
                browserFingerprint, clientIpResolution);
        CocoWebRequestSnapshotAttributes.set(request, snapshot, headerFingerprint(request));
        return snapshot;
    }

    private static boolean reusable(CocoWebRequestSnapshot snapshot, String traceId, HttpServletRequest request) {
        return snapshot.traceId().equals(normalizeTraceId(traceId))
                && Objects.equals(snapshot.method(), normalizeMethod(request.getMethod()))
                && Objects.equals(snapshot.path(), resolvePath(request))
                && Objects.equals(snapshot.securityInput().queryString(), normalizeOptional(request.getQueryString()))
                && CocoWebRequestSnapshotAttributes.headerFingerprint(request)
                        .filter(fingerprint -> fingerprint.equals(headerFingerprint(request)))
                        .isPresent()
                && reusableBody(snapshot, request);
    }

    private static boolean reusableBody(CocoWebRequestSnapshot snapshot, HttpServletRequest request) {
        return CocoCachedBodyHttpServletRequest.cachedBody(request)
                .map(CocoCachedRequestBody::sha256)
                .map(sha256 -> Objects.equals(sha256, snapshot.securityInput().bodySha256()))
                .orElse(true);
    }

    private static String resolvePath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return requestUri == null || requestUri.isBlank() ? null : requestUri;
    }

    private String resolveQueryString(HttpServletRequest request) {
        return this.requestParameterResolver.resolveQueryString(request);
    }

    private static String resolveLocale(HttpServletRequest request) {
        Locale locale = request.getLocale();
        return locale == null ? null : locale.toLanguageTag();
    }

    private static String normalizeTraceId(String traceId) {
        return traceId == null || traceId.isBlank() ? "" : traceId.trim();
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
