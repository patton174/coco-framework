package io.github.coco.feature.idempotency;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/** MVC 请求幂等拦截器。 */
public final class CocoIdempotencyMvcInterceptor implements AsyncHandlerInterceptor {
    static final String LEASE_ATTRIBUTE = CocoIdempotencyMvcInterceptor.class.getName() + ".lease";
    private static final SecureRandom OWNER_TOKENS = new SecureRandom();
    private final CocoIdempotencyProperties properties;
    private final CocoIdempotencyKeyResolver keyResolver;
    private final CocoIdempotencyStore store;
    private final CocoIdempotencyResponseWriter responseWriter;
    private final Clock clock;
    private final Set<String> allowedMethods;

    /** 创建拦截器。 */
    public CocoIdempotencyMvcInterceptor(CocoIdempotencyProperties properties, CocoIdempotencyKeyResolver keyResolver,
            CocoIdempotencyStore store, CocoIdempotencyResponseWriter responseWriter, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.allowedMethods = properties.getAllowedMethods().stream().filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isEmpty()).map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod method) || request.getAttribute(LEASE_ATTRIBUTE) != null) { return true; }
        CocoIdempotent intent = resolveIntent(method);
        if (intent == null || !this.allowedMethods.contains(request.getMethod().toUpperCase(Locale.ROOT))) { return true; }
        try {
            CocoIdempotencyKey key = this.keyResolver.resolve(request, method, intent);
            CocoIdempotencyLease lease = new CocoIdempotencyLease(key, ownerToken(), expiresAt(intent));
            CocoIdempotencyStore.AcquireResult result = this.store.acquire(lease);
            if (result == CocoIdempotencyStore.AcquireResult.ACQUIRED) {
                request.setAttribute(LEASE_ATTRIBUTE, new LeaseLifecycle(lease));
                return true;
            }
            this.responseWriter.write(result == CocoIdempotencyStore.AcquireResult.DUPLICATE
                    ? CocoIdempotencyErrorCode.DUPLICATE : CocoIdempotencyErrorCode.UNAVAILABLE, request, response);
            return false;
        }
        catch (CocoIdempotencyKeyException exception) {
            this.responseWriter.write(CocoIdempotencyErrorCode.INVALID_KEY, request, response);
            return false;
        }
        catch (RuntimeException exception) {
            this.responseWriter.write(CocoIdempotencyErrorCode.UNAVAILABLE, request, response);
            return false;
        }
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
            org.springframework.web.servlet.ModelAndView modelAndView) {
        Object value = request.getAttribute(LEASE_ATTRIBUTE);
        if (value instanceof LeaseLifecycle lifecycle) { lifecycle.handlerCompleted.set(true); }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception exception) {
        Object value = request.getAttribute(LEASE_ATTRIBUTE);
        if (!(value instanceof LeaseLifecycle lifecycle) || !lifecycle.completed.compareAndSet(false, true)) { return; }
        request.removeAttribute(LEASE_ATTRIBUTE);
        if (exception != null || !lifecycle.handlerCompleted.get() || response.getStatus() < 200
                || response.getStatus() >= 400) {
            this.store.release(lifecycle.lease);
        }
    }

    private Instant expiresAt(CocoIdempotent intent) {
        Duration ttl = intent.ttlSeconds() < 0 ? this.properties.getTtl() : Duration.ofSeconds(intent.ttlSeconds());
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Idempotency TTL must be positive");
        }
        return this.clock.instant().plus(ttl);
    }

    private static CocoIdempotent resolveIntent(HandlerMethod method) {
        CocoIdempotent direct = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), CocoIdempotent.class);
        return direct != null ? direct : AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), CocoIdempotent.class);
    }

    private static String ownerToken() {
        byte[] bytes = new byte[32];
        OWNER_TOKENS.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final class LeaseLifecycle {
        private final CocoIdempotencyLease lease;
        private final AtomicBoolean handlerCompleted = new AtomicBoolean();
        private final AtomicBoolean completed = new AtomicBoolean();
        private LeaseLifecycle(CocoIdempotencyLease lease) { this.lease = lease; }
    }
}
