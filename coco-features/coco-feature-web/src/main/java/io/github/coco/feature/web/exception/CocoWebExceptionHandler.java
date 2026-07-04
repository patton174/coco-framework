package io.github.coco.feature.web.exception;

import java.util.Objects;

import io.github.coco.common.exception.CocoException;
import io.github.coco.common.i18n.CocoMessageService;
import io.github.coco.common.trace.CocoTraceContext;
import io.github.coco.feature.web.response.CocoApiResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Coco Web 全局异常处理器。
 * <p>
 * 捕获 Web 请求中的 {@link CocoException}，解析国际化消息，并返回统一的 Coco Web 响应结构。
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
@RestControllerAdvice
public class CocoWebExceptionHandler {

    private final CocoMessageService messageService;

    private final CocoExceptionHttpStatusResolver httpStatusResolver;

    /**
     * <p>
     * 创建 Coco Web 全局异常处理器。
     * </p>
     * @param messageService Coco 消息服务
     * @param httpStatusResolver 异常 HTTP 状态解析器
     */
    public CocoWebExceptionHandler(CocoMessageService messageService,
            CocoExceptionHttpStatusResolver httpStatusResolver) {
        this.messageService = Objects.requireNonNull(messageService, "messageService must not be null");
        this.httpStatusResolver = Objects.requireNonNull(httpStatusResolver,
                "httpStatusResolver must not be null");
    }

    /**
     * <p>
     * 处理 Coco 框架异常，并返回统一异常响应。
     * </p>
     * @param exception Coco 异常
     * @param request 当前 Web 请求
     * @return 统一异常响应实体
     */
    @ExceptionHandler(CocoException.class)
    public ResponseEntity<CocoApiResponse<Void>> handleCocoException(CocoException exception, WebRequest request) {
        CocoException checkedException = Objects.requireNonNull(exception, "exception must not be null");
        HttpStatusCode statusCode = Objects.requireNonNull(this.httpStatusResolver.resolve(checkedException),
                "resolved http status must not be null");
        String message = this.messageService.resolve(checkedException);
        String traceId = CocoTraceContext.getOrCreateTraceId();
        CocoApiResponse<Void> response = CocoApiResponse.error(checkedException.code(), message, traceId,
                resolvePath(request));
        return ResponseEntity.status(statusCode).body(response);
    }

    private static String resolvePath(WebRequest request) {
        if (request == null) {
            return null;
        }
        String description = request.getDescription(false);
        if (description == null || description.isBlank()) {
            return null;
        }
        String prefix = "uri=";
        int start = description.indexOf(prefix);
        if (start < 0) {
            return description;
        }
        int pathStart = start + prefix.length();
        int pathEnd = description.indexOf(';', pathStart);
        return pathEnd < 0 ? description.substring(pathStart) : description.substring(pathStart, pathEnd);
    }
}
