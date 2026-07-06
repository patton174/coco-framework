package io.github.coco.feature.web.context;

import java.util.Locale;
import java.util.Objects;

import io.github.coco.feature.web.accesslog.CocoAccessLogCaptureProperties;
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
        CocoWebContextProperties contextProperties = properties == null ? new CocoWebContextProperties() : properties;
        CocoAccessLogCaptureProperties logProperties = accessLogProperties == null
                ? new CocoAccessLogCaptureProperties()
                : accessLogProperties;
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
                ? new DefaultCocoRequestParameterResolver(logProperties)
                : requestParameterResolver;
        this.securityInputResolver = securityInputResolver == null
                ? new DefaultCocoWebRequestSecurityInputResolver(contextProperties, this.requestHeaderResolver,
                        this.requestParameterResolver)
                : securityInputResolver;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoWebRequestSnapshot resolve(String traceId, HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        String method = checkedRequest.getMethod();
        String path = resolvePath(checkedRequest);
        CocoBrowserFingerprint browserFingerprint = this.browserFingerprintResolver.resolve(checkedRequest);
        CocoWebRequestSecurityInput securityInput = this.securityInputResolver.resolve(checkedRequest, method, path);
        return new CocoWebRequestSnapshot(traceId, method, path, resolveQueryString(checkedRequest),
                this.clientIpResolver.resolve(checkedRequest), checkedRequest.getHeader("User-Agent"),
                resolveLocale(checkedRequest),
                checkedRequest.getScheme(), checkedRequest.getServerName(), checkedRequest.getServerPort(),
                checkedRequest.getContentType(), this.requestHeaderResolver.resolveIncludedHeaders(checkedRequest),
                this.requestParameterResolver.resolveParameters(checkedRequest), securityInput, browserFingerprint);
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

}
