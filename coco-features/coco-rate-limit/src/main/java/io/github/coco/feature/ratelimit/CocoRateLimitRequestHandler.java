package io.github.coco.feature.ratelimit;

import java.io.IOException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coco 限流请求执行器。
 * <p>
 * Filter 和 MVC 注解后备拦截器通过同一执行器占用配额，保证路径路由与显式注解不会形成两个不同的计数语义。
 * </p>
 */
public final class CocoRateLimitRequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CocoRateLimitRequestHandler.class);

    private final CocoRateLimitKeyResolver keyResolver;

    private final CocoRateLimitStore store;

    private final CocoWebRequestContextResolver requestContextResolver;

    private final CocoRateLimitResponseWriter responseWriter;

    private final Clock clock;

    /**
     * 创建限流请求执行器。
     * @param keyResolver 限流键解析器
     * @param store 限流原子存储
     * @param requestContextResolver Coco Web 请求上下文解析器
     * @param responseWriter 限流拒绝响应写出器
     * @param clock 限流时钟
     */
    public CocoRateLimitRequestHandler(CocoRateLimitKeyResolver keyResolver, CocoRateLimitStore store,
            CocoWebRequestContextResolver requestContextResolver, CocoRateLimitResponseWriter responseWriter,
            Clock clock) {
        this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.requestContextResolver = Objects.requireNonNull(requestContextResolver,
                "requestContextResolver must not be null");
        this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * 尝试为已解析路由占用一个配额。
     * @param route 当前路由
     * @param request 当前请求
     * @param response 当前响应
     * @return 请求可继续执行时为 {@code true}
     * @throws IOException 响应写出失败时抛出
     * @throws RuntimeException 路由、请求快照、键解析或限流存储失败时原样抛出
     */
    public boolean handle(CocoRateLimitRoute route, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        CocoRateLimitRoute checkedRoute = CocoRateLimitRoute.copyOf(
                Objects.requireNonNull(route, "route must not be null"));
        Instant now = this.clock.instant();
        Optional<String> previousTraceId = CocoTraceContext.currentTraceId();
        try {
            String traceId = previousTraceId.orElseGet(CocoTraceContext::getOrCreateTraceId);
            Instant resetAt = resetAt(now, checkedRoute.getWindowSeconds());
            CocoWebRequestSnapshot snapshot = this.requestContextResolver.resolve(traceId, request);
            CocoRateLimitKey key = this.keyResolver.resolve(snapshot, checkedRoute);
            CocoRateLimitDecision decision = this.store.acquire(
                    new CocoRateLimitPermit(key, checkedRoute.getLimit(), resetAt));
            writeRateLimitHeaders(response, decision, now);
            if (decision.allowed()) {
                return true;
            }
            reject(checkedRoute, traceId, decision, request, response, CocoRateLimitErrorCode.EXCEEDED);
            return false;
        }
        finally {
            restoreTraceId(previousTraceId);
        }
    }

    private static void restoreTraceId(Optional<String> previousTraceId) {
        CocoTraceContext.clear();
        previousTraceId.ifPresent(CocoTraceContext::setTraceId);
    }

    private void reject(CocoRateLimitRoute route, String traceId, CocoRateLimitDecision decision,
            HttpServletRequest request, HttpServletResponse response, CocoRateLimitErrorCode errorCode)
            throws IOException {
        if (decision.capacityExhausted()) {
            LOGGER.warn("Coco rate-limit storage capacity exhausted route={} traceId={}", route.getId(), traceId);
        }
        else {
            LOGGER.info("Coco rate-limit rejected route={} traceId={}", route.getId(), traceId);
        }
        this.responseWriter.write(errorCode.request(), request, response);
    }

    static Instant resetAt(Instant now, long windowSeconds) {
        Objects.requireNonNull(now, "now must not be null");
        if (!CocoRateLimitRoute.isSupportedWindowSeconds(windowSeconds)) {
            throw new IllegalArgumentException("windowSeconds must be between 1 and "
                    + CocoRateLimitRoute.MAX_WINDOW_SECONDS);
        }
        long windowStart = Math.floorDiv(now.getEpochSecond(), windowSeconds) * windowSeconds;
        try {
            return Instant.ofEpochSecond(Math.addExact(windowStart, windowSeconds));
        }
        catch (DateTimeException exception) {
            return Instant.MAX;
        }
    }

    static void writeRateLimitHeaders(HttpServletResponse response, CocoRateLimitDecision decision, Instant now) {
        String limit = Long.toString(decision.limit());
        String remaining = Long.toString(decision.remaining());
        long resetSeconds = remainingSeconds(now, decision.resetAt());
        String reset = Long.toString(resetSeconds);
        response.setHeader("RateLimit-Limit", limit);
        response.setHeader("RateLimit-Remaining", remaining);
        response.setHeader("RateLimit-Reset", reset);
        response.setHeader("X-RateLimit-Limit", limit);
        response.setHeader("X-RateLimit-Remaining", remaining);
        response.setHeader("X-RateLimit-Reset", reset);
        if (!decision.allowed()) {
            response.setHeader("Retry-After", Long.toString(Math.max(1, resetSeconds)));
        }
    }

    private static long remainingSeconds(Instant now, Instant resetAt) {
        Duration remaining = Duration.between(now, resetAt);
        if (remaining.isZero() || remaining.isNegative()) {
            return 0;
        }
        return Math.addExact(remaining.getSeconds(), remaining.getNano() == 0 ? 0 : 1);
    }

}
