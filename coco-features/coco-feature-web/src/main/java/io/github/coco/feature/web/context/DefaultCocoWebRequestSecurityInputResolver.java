package io.github.coco.feature.web.context;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.coco.feature.web.accesslog.CocoAccessLogCaptureProperties;
import io.github.coco.feature.web.body.CocoCachedBodyHttpServletRequest;
import io.github.coco.feature.web.body.CocoCachedRequestBody;
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

    private final CocoWebContextProperties properties;

    private final CocoRequestHeaderResolver requestHeaderResolver;

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
        CocoWebContextProperties contextProperties = properties == null ? new CocoWebContextProperties() : properties;
        this.properties = contextProperties;
        this.requestHeaderResolver = requestHeaderResolver == null
                ? new DefaultCocoRequestHeaderResolver(contextProperties)
                : requestHeaderResolver;
        this.requestParameterResolver = requestParameterResolver == null
                ? new DefaultCocoRequestParameterResolver(new CocoAccessLogCaptureProperties())
                : requestParameterResolver;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoWebRequestSecurityInput resolve(HttpServletRequest request, String method, String path) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        String rawQueryString = this.requestParameterResolver.resolveRawQueryString(checkedRequest);
        Map<String, List<String>> rawParameters = this.requestParameterResolver.resolveRawParameters(checkedRequest);
        Map<String, String> securityHeaders = this.requestHeaderResolver.resolveSelectedHeaders(checkedRequest,
                this.properties.getSecurityHeaderNames(), false);
        Map<String, String> canonicalHeaders = this.requestHeaderResolver.resolveSelectedHeaders(checkedRequest,
                this.properties.getCanonicalHeaderNames(), false);
        CocoCachedRequestBody cachedBody = CocoCachedBodyHttpServletRequest.cachedBody(checkedRequest)
                .orElse(CocoCachedRequestBody.empty());
        return new CocoWebRequestSecurityInput(method, path, rawQueryString, rawParameters, securityHeaders,
                canonicalHeaders, cachedBody.sha256(), cachedBody.cached() ? cachedBody.length() : null,
                cachedBody.cached());
    }
}
