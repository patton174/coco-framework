package io.github.coco.feature.ratelimit;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Coco Servlet 限流过滤器。
 * <p>
 * 过滤器在业务 Controller 和事务边界前执行，使用 Coco Web 请求快照获取受可信代理策略保护的客户端信息；
 * 本身不解析用户、角色或租户模型。
 * </p>
 */
public final class CocoRateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CocoRateLimitFilter.class);

    private final CocoRateLimitRouteMatcher routeMatcher;

    private final CocoRateLimitKeyResolver keyResolver;

    private final CocoRateLimitStore store;

    private final CocoWebRequestContextResolver requestContextResolver;

    private final CocoRateLimitResponseWriter responseWriter;

    private final Clock clock;

    /**
     * 创建 Coco 限流过滤器。
     * @param routeMatcher 限流路由匹配器
     * @param keyResolver 限流键解析器
     * @param store 限流原子存储
     * @param requestContextResolver Coco Web 请求上下文解析器
     * @param responseWriter 限流拒绝响应写出器
     */
    public CocoRateLimitFilter(CocoRateLimitRouteMatcher routeMatcher, CocoRateLimitKeyResolver keyResolver,
            CocoRateLimitStore store, CocoWebRequestContextResolver requestContextResolver,
            CocoRateLimitResponseWriter responseWriter) {
        this(routeMatcher, keyResolver, store, requestContextResolver, responseWriter, Clock.systemUTC());
    }

    CocoRateLimitFilter(CocoRateLimitRouteMatcher routeMatcher, CocoRateLimitKeyResolver keyResolver,
            CocoRateLimitStore store, CocoWebRequestContextResolver requestContextResolver,
            CocoRateLimitResponseWriter responseWriter, Clock clock) {
        this.routeMatcher = Objects.requireNonNull(routeMatcher, "routeMatcher must not be null");
        this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.requestContextResolver = Objects.requireNonNull(requestContextResolver,
                "requestContextResolver must not be null");
        this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        CocoRateLimitRoute route = this.routeMatcher.resolve(request).orElse(null);
        if (route == null) {
            filterChain.doFilter(request, response);
            return;
        }
        Instant now = this.clock.instant();
        String traceId = CocoTraceContext.currentTraceId().orElseGet(CocoTraceContext::getOrCreateTraceId);
        CocoWebRequestSnapshot snapshot = this.requestContextResolver.resolve(traceId, request);
        CocoRateLimitKey key = this.keyResolver.resolve(snapshot, route);
        Instant resetAt = resetAt(now, route.getWindowSeconds());
        CocoRateLimitDecision decision = this.store.acquire(new CocoRateLimitPermit(key, route.getLimit(), resetAt));
        writeRateLimitHeaders(response, decision, now);
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }
        LOGGER.info("Coco rate-limit rejected route={} traceId={} capacityExhausted={}", route.getId(), traceId,
                decision.capacityExhausted());
        this.responseWriter.write(CocoRateLimitErrorCode.EXCEEDED.request(), request, response);
    }

    private static Instant resetAt(Instant now, long windowSeconds) {
        long windowStart = Math.floorDiv(now.getEpochSecond(), windowSeconds) * windowSeconds;
        return Instant.ofEpochSecond(Math.addExact(windowStart, windowSeconds));
    }

    private static void writeRateLimitHeaders(HttpServletResponse response, CocoRateLimitDecision decision,
            Instant now) {
        response.setHeader("RateLimit-Limit", Long.toString(decision.limit()));
        response.setHeader("RateLimit-Remaining", Long.toString(decision.remaining()));
        long resetSeconds = Math.max(0, Duration.between(now, decision.resetAt()).toSeconds());
        response.setHeader("RateLimit-Reset", Long.toString(resetSeconds));
        if (!decision.allowed()) {
            response.setHeader("Retry-After", Long.toString(Math.max(1, resetSeconds)));
        }
    }
}
