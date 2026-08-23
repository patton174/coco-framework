package io.github.coco.feature.idempotency;

import java.util.Locale;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.method.HandlerMethod;

/** 默认请求幂等键解析器。 */
public final class DefaultCocoIdempotencyKeyResolver implements CocoIdempotencyKeyResolver {
    private final String headerName;
    private final int maxKeyLength;
    private final CocoIdempotencyOperationResolver operationResolver;

    /** 创建默认解析器。 */
    public DefaultCocoIdempotencyKeyResolver(CocoIdempotencyProperties properties) {
        this(properties, new DefaultCocoIdempotencyOperationResolver());
    }

    /** 创建使用指定操作标识解析器的默认解析器。 */
    public DefaultCocoIdempotencyKeyResolver(CocoIdempotencyProperties properties,
            CocoIdempotencyOperationResolver operationResolver) {
        CocoIdempotencyProperties checked = Objects.requireNonNull(properties, "properties must not be null");
        this.headerName = requireHeaderName(checked.getHeaderName());
        this.maxKeyLength = positive(checked.getMaxKeyLength(), "coco.idempotency.max-key-length");
        this.operationResolver = Objects.requireNonNull(operationResolver, "operationResolver must not be null");
    }

    @Override
    public CocoIdempotencyKey resolve(HttpServletRequest request, HandlerMethod handlerMethod, CocoIdempotent intent) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(handlerMethod, "handlerMethod must not be null");
        Objects.requireNonNull(intent, "intent must not be null");
        String rawKey = request.getHeader(this.headerName);
        if (rawKey == null || rawKey.isBlank()) {
            throw new CocoIdempotencyKeyException();
        }
        if (rawKey.length() > this.maxKeyLength || !rawKey.chars().allMatch(DefaultCocoIdempotencyKeyResolver::isAllowedCharacter)) {
            throw new CocoIdempotencyKeyException();
        }
        String namespace = intent.namespace().isBlank() ? "default" : intent.namespace().trim();
        return CocoIdempotencyKey.fromRawKey(namespace, request.getMethod().trim().toUpperCase(Locale.ROOT),
                this.operationResolver.resolve(request, handlerMethod), rawKey);
    }

    private static boolean isAllowedCharacter(int character) {
        return character >= '!' && character <= '~';
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
