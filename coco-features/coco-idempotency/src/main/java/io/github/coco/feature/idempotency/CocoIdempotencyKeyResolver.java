package io.github.coco.feature.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.method.HandlerMethod;

/** 解析并校验当前请求的非敏感幂等逻辑键。 */
@FunctionalInterface
public interface CocoIdempotencyKeyResolver {

    /** @throws CocoIdempotencyKeyException 请求键无效时抛出 */
    CocoIdempotencyKey resolve(HttpServletRequest request, HandlerMethod handlerMethod, CocoIdempotent intent);
}
