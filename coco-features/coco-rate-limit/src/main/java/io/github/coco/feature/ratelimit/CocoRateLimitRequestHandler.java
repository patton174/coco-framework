package io.github.coco.feature.ratelimit;

import java.io.IOException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import io.github.coco.context.trace.CocoTraceContext;
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

    private final CocoRateLimitResponseWriter responseWriter;

    private final Clock clock;

    /**
     * 创建限流请求执行器。
     * @param keyResolver 限流键解析器
     * @param store 限流原子存储
     * @param responseWriter 限流拒绝响应写出器
     * @param clock 限流时钟
     */
    public CocoRateLimitRequestHandler(CocoRateLimitKeyResolver keyResolver, CocoRateLimitStore store,
            CocoRateLimitResponseWriter responseWriter, Clock clock) {
        this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
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
     */
    public boolean handle(CocoRateLimitRoute route, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        CocoRateLimitRoute checkedRoute = CocoRateLimitRoute.copyOf(
                Objects.requireNonNull(route, "route must not be null"));
        Instant now = this.clock.instant();
        Instant resetAt = fallbackResetAt(now);
        String traceId = CocoTraceContext.currentTraceId().orElseGet(CocoTraceContext::getOrCreateTraceId);
        try {
            resetAt = resetAt(now, checkedRoute.getWindowSeconds());
            CocoRateLimitKey key = this.keyResolver.resolve(request, checkedRoute);
            CocoRateLimitDecision decision = this.store.acquire(
                    new CocoRateLimitPermit(key, checkedRoute.getLimit(), resetAt));
            if (response.isCommitted()) {
                return false;
            }
            writeRateLimitHeaders(response, decision, now);
            if (decision.allowed()) {
                return true;
            }
            reject(checkedRoute, traceId, decision, request, response,
                    decision.capacityExhausted() ? CocoRateLimitErrorCode.UNAVAILABLE : CocoRateLimitErrorCode.EXCEEDED);
            return false;
        }
        catch (RuntimeException exception) {
            if (response.isCommitted()) {
                return false;
            }
            CocoRateLimitDecision decision = new CocoRateLimitDecision(false, checkedRoute.getLimit(), 0, resetAt, true);
            writeRateLimitHeaders(response, decision, now);
            LOGGER.warn("Coco rate-limit unavailable; failing closed route={} traceId={}", checkedRoute.getId(), traceId,
                    exception);
            this.responseWriter.write(CocoRateLimitErrorCode.UNAVAILABLE, request, response);
            return false;
        }
    }

    private void reject(CocoRateLimitRoute route, String traceId, CocoRateLimitDecision decision,
            HttpServletRequest request, HttpServletResponse response, CocoRateLimitErrorCode errorCode)
            throws IOException {
        if (decision.capacityExhausted()) {
            LOGGER.warn("Coco rate-limit rejected because capacity is exhausted route={} traceId={}", route.getId(),
                    traceId);
        }
        else {
            LOGGER.info("Coco rate-limit rejected because quota is exhausted route={} traceId={}", route.getId(),
                    traceId);
        }
        this.responseWriter.write(errorCode, request, response);
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

    private static Instant fallbackResetAt(Instant now) {
        return now.getEpochSecond() < Instant.MAX.getEpochSecond() ? now.plusSeconds(1) : Instant.MAX;
    }
}
