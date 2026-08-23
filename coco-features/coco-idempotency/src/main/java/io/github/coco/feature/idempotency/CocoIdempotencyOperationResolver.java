package io.github.coco.feature.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.method.HandlerMethod;

/** 解析稳定且不含请求键的 MVC 操作标识。 */
@FunctionalInterface
public interface CocoIdempotencyOperationResolver {
    /** @return 包含完整映射条件的稳定操作标识 */
    String resolve(HttpServletRequest request, HandlerMethod handlerMethod);
}
