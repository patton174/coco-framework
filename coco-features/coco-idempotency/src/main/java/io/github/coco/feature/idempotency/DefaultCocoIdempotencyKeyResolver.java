package io.github.coco.feature.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/** 默认请求幂等键解析器。 */
public final class DefaultCocoIdempotencyKeyResolver implements CocoIdempotencyKeyResolver {
    private final String headerName;
    private final int maxKeyLength;

    /** 创建默认解析器。 */
    public DefaultCocoIdempotencyKeyResolver(CocoIdempotencyProperties properties) {
        CocoIdempotencyProperties checked = Objects.requireNonNull(properties, "properties must not be null");
        this.headerName = requireHeaderName(checked.getHeaderName());
        this.maxKeyLength = positive(checked.getMaxKeyLength(), "coco.idempotency.max-key-length");
    }

    @Override
    public CocoIdempotencyKey resolve(HttpServletRequest request, HandlerMethod handlerMethod, CocoIdempotent intent) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(handlerMethod, "handlerMethod must not be null");
        Objects.requireNonNull(intent, "intent must not be null");
        String rawKey = request.getHeader(this.headerName);
        if (rawKey == null || rawKey.isBlank()) {
            throw new CocoIdempotencyKeyException("Missing idempotency key");
        }
        if (rawKey.length() > this.maxKeyLength || !rawKey.chars().allMatch(DefaultCocoIdempotencyKeyResolver::isAllowedCharacter)) {
            throw new CocoIdempotencyKeyException("Invalid idempotency key");
        }
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = pattern == null ? handlerMethod.getMethod().toGenericString() : pattern.toString();
        String namespace = intent.namespace().isBlank() ? "default" : intent.namespace().trim();
        return new CocoIdempotencyKey(namespace, request.getMethod().trim().toUpperCase(Locale.ROOT), route, digest(rawKey));
    }

    private static boolean isAllowedCharacter(int character) {
        return character >= '!' && character <= '~';
    }

    private static String digest(String key) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) { result.append(String.format(Locale.ROOT, "%02x", value)); }
            return result.toString();
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static int positive(int value, String property) {
        if (value < 1) { throw new IllegalArgumentException(property + " must be positive"); }
        return value;
    }

    private static String requireHeaderName(String value) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException("coco.idempotency.header-name must not be blank"); }
        return value.trim();
    }
}
