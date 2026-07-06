package io.github.coco.feature.web.exception;

import java.util.Objects;

import io.github.coco.common.exception.CocoCommonErrorCode;
import io.github.coco.common.exception.CocoException;
import io.github.coco.common.exception.type.CocoConflictException;
import io.github.coco.common.exception.type.CocoForbiddenException;
import io.github.coco.common.exception.type.CocoNotFoundException;
import io.github.coco.common.exception.type.CocoRequestException;
import io.github.coco.common.exception.type.CocoSystemException;
import io.github.coco.common.exception.type.CocoUnauthorizedException;
import io.github.coco.common.i18n.api.CocoMessageService;
import io.github.coco.common.trace.CocoTraceContext;
import io.github.coco.feature.web.response.CocoApiResponse;
import io.github.coco.feature.web.response.CocoSystemCodeProvider;
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

    private final CocoSystemCodeProvider codeProvider;

    /**
     * <p>
     * 创建 Coco Web 全局异常处理器。
     * </p>
     * @param messageService Coco 消息服务
     * @param httpStatusResolver 异常 HTTP 状态解析器
     * @param codeProvider 系统响应码提供器
     */
    public CocoWebExceptionHandler(CocoMessageService messageService,
            CocoExceptionHttpStatusResolver httpStatusResolver, CocoSystemCodeProvider codeProvider) {
        this.messageService = Objects.requireNonNull(messageService, "messageService must not be null");
        this.httpStatusResolver = Objects.requireNonNull(httpStatusResolver,
                "httpStatusResolver must not be null");
        this.codeProvider = Objects.requireNonNull(codeProvider, "codeProvider must not be null");
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
        String message = this.messageService.resolve(checkedException.message());
        String traceId = CocoTraceContext.getOrCreateTraceId();
        int code = checkedException.businessCode()
                .orElseGet(() -> resolveSystemCode(checkedException, statusCode));
        CocoApiResponse<Void> response = CocoApiResponse.error(code, message, traceId,
                resolvePath(request));
        return ResponseEntity.status(statusCode).body(response);
    }

    /**
     * <p>
     * 解析框架默认系统响应码。
     * </p>
     * <p>
     * 解析顺序为类型化异常、Coco 内置消息编码、HTTP 状态兜底；业务自定义响应码由调用方提前处理。
     * </p>
     * @param exception Coco 异常
     * @param statusCode 当前异常对应的 HTTP 状态码
     * @return 系统响应码
     */
    private int resolveSystemCode(CocoException exception, HttpStatusCode statusCode) {
        if (exception instanceof CocoRequestException) {
            return this.codeProvider.invalidArgument();
        }
        if (exception instanceof CocoUnauthorizedException) {
            return this.codeProvider.unauthorized();
        }
        if (exception instanceof CocoForbiddenException) {
            return this.codeProvider.forbidden();
        }
        if (exception instanceof CocoNotFoundException) {
            return this.codeProvider.notFound();
        }
        if (exception instanceof CocoConflictException) {
            return this.codeProvider.conflict();
        }
        if (exception instanceof CocoSystemException) {
            return this.codeProvider.internalError();
        }
        Integer messageCode = resolveSystemCodeByMessageCode(exception.messageCode());
        if (messageCode != null) {
            return messageCode;
        }
        return resolveSystemCodeByHttpStatus(statusCode);
    }

    /**
     * <p>
     * 根据 Coco 内置消息编码解析系统响应码。
     * </p>
     * @param messageCode 国际化消息编码
     * @return 匹配到的系统响应码；未匹配时为空
     */
    private Integer resolveSystemCodeByMessageCode(String messageCode) {
        if (CocoCommonErrorCode.INVALID_ARGUMENT.code().equals(messageCode)
                || CocoCommonErrorCode.MISSING_MESSAGE_CODE.code().equals(messageCode)
                || CocoCommonErrorCode.MISSING_ERROR_CODE.code().equals(messageCode)) {
            return this.codeProvider.invalidArgument();
        }
        if (CocoCommonErrorCode.UNAUTHORIZED.code().equals(messageCode)) {
            return this.codeProvider.unauthorized();
        }
        if (CocoCommonErrorCode.FORBIDDEN.code().equals(messageCode)) {
            return this.codeProvider.forbidden();
        }
        if (CocoCommonErrorCode.NOT_FOUND.code().equals(messageCode)) {
            return this.codeProvider.notFound();
        }
        if (CocoCommonErrorCode.CONFLICT.code().equals(messageCode)) {
            return this.codeProvider.conflict();
        }
        if (CocoCommonErrorCode.UNKNOWN.code().equals(messageCode)
                || CocoCommonErrorCode.INTERNAL_ERROR.code().equals(messageCode)) {
            return this.codeProvider.internalError();
        }
        return null;
    }

    /**
     * <p>
     * 根据 HTTP 状态码兜底解析系统响应码。
     * </p>
     * @param statusCode HTTP 状态码
     * @return 系统响应码
     */
    private int resolveSystemCodeByHttpStatus(HttpStatusCode statusCode) {
        if (statusCode != null && statusCode.value() == 400) {
            return this.codeProvider.invalidArgument();
        }
        if (statusCode != null && statusCode.value() == 401) {
            return this.codeProvider.unauthorized();
        }
        if (statusCode != null && statusCode.value() == 403) {
            return this.codeProvider.forbidden();
        }
        if (statusCode != null && statusCode.value() == 404) {
            return this.codeProvider.notFound();
        }
        if (statusCode != null && statusCode.value() == 409) {
            return this.codeProvider.conflict();
        }
        return this.codeProvider.internalError();
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
