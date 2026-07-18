package io.github.coco.feature.web.replay;

import java.io.IOException;
import java.util.Objects;

import io.github.coco.exception.CocoException;
import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.feature.web.context.DefaultCocoWebRequestMatcher;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadata;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadataResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Coco Web 防重放请求形态预检过滤器。
 * <p>
 * 在签名验签、请求解密和 replay store 占用之前，先对需要防重放保护的请求执行廉价协议形态校验。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoReplayRequestShapeFilter extends OncePerRequestFilter {

    private final CocoReplayProperties properties;

    private final CocoReplayKeyResolver replayKeyResolver;

    private final CocoWebRequestContextResolver requestContextResolver;

    private final CocoWebRequestSecurityMetadataResolver securityMetadataResolver;

    private final CocoWebRequestMatcher requestMatcher;

    private final CocoFilterExceptionResponseWriter exceptionResponseWriter;

    /**
     * <p>
     * 创建 Coco Web 防重放请求形态预检过滤器。
     * </p>
     * @param properties 防重放配置属性
     * @param replayKeyResolver 防重放键解析器
     * @param requestContextResolver Web 请求上下文解析器
     * @param securityMetadataResolver Web 请求安全元数据解析器
     * @param exceptionResponseWriter 过滤器异常响应写出器
     * @param requestMatcher Web 请求匹配器
     */
    public CocoReplayRequestShapeFilter(CocoReplayProperties properties, CocoReplayKeyResolver replayKeyResolver,
            CocoWebRequestContextResolver requestContextResolver,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver,
            CocoFilterExceptionResponseWriter exceptionResponseWriter, CocoWebRequestMatcher requestMatcher) {
        this.properties = properties == null ? new CocoReplayProperties() : properties;
        this.replayKeyResolver = Objects.requireNonNull(replayKeyResolver, "replayKeyResolver must not be null");
        this.requestContextResolver = Objects.requireNonNull(requestContextResolver,
                "requestContextResolver must not be null");
        this.securityMetadataResolver = Objects.requireNonNull(securityMetadataResolver,
                "securityMetadataResolver must not be null");
        this.exceptionResponseWriter = Objects.requireNonNull(exceptionResponseWriter,
                "exceptionResponseWriter must not be null");
        this.requestMatcher = requestMatcher == null ? new DefaultCocoWebRequestMatcher() : requestMatcher;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!this.properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (matchesIgnoredRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        boolean replayRequired = CocoReplayRequestShape.replayRequired(this.properties, this.requestMatcher, request);
        try {
            preflight(request, replayRequired);
        }
        catch (CocoException ex) {
            this.exceptionResponseWriter.write(ex, request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean matchesIgnoredRequest(HttpServletRequest request) {
        return this.requestMatcher.matches(request, this.properties.getMatcher().getIgnored());
    }

    private void preflight(HttpServletRequest request, boolean replayRequired) {
        String traceId = CocoTraceContext.currentTraceId().orElseGet(CocoTraceContext::getOrCreateTraceId);
        CocoWebRequestSnapshot snapshot = this.requestContextResolver.resolve(traceId, request);
        CocoWebRequestSecurityMetadata metadata = this.securityMetadataResolver.resolve(snapshot.securityInput());
        if (!CocoReplayRequestShape.shouldProtect(this.properties, metadata, replayRequired)) {
            return;
        }
        CocoReplayKey replayKey = this.replayKeyResolver.resolve(snapshot, metadata);
        CocoReplayRequestShape.validateRequiredFields(replayKey);
        CocoReplayRequestShape.parseTimestamp(replayKey.timestamp());
    }
}
