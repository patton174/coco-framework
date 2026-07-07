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
import io.github.coco.feature.web.response.CocoResponseBodyFactory;
import io.github.coco.feature.web.response.CocoResponseMetadata;
import io.github.coco.feature.web.response.CocoResponsePayload;
import io.github.coco.feature.web.response.CocoResponseProperties;
import io.github.coco.feature.web.response.CocoSystemCodeProvider;
import io.github.coco.feature.web.response.DefaultCocoResponseBodyFactory;
import io.github.coco.feature.web.trace.CocoTraceProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
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

    private static final String BAD_REQUEST_MESSAGE_CODE = "coco.web.error.bad-request";

    private static final String METHOD_NOT_ALLOWED_MESSAGE_CODE = "coco.web.error.method-not-allowed";

    private final CocoMessageService messageService;

    private final CocoExceptionHttpStatusResolver httpStatusResolver;

    private final CocoSystemCodeProvider codeProvider;

    private final CocoResponseProperties responseProperties;

    private final CocoTraceProperties traceProperties;

    private final CocoResponseBodyFactory responseBodyFactory;

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
        this(messageService, httpStatusResolver, codeProvider, new CocoResponseProperties());
    }

    /**
     * <p>
     * 创建 Coco Web 全局异常处理器。
     * </p>
     * @param messageService Coco 消息服务
     * @param httpStatusResolver 异常 HTTP 状态解析器
     * @param codeProvider 系统响应码提供器
     * @param responseProperties 统一响应配置
     */
    public CocoWebExceptionHandler(CocoMessageService messageService,
            CocoExceptionHttpStatusResolver httpStatusResolver, CocoSystemCodeProvider codeProvider,
            CocoResponseProperties responseProperties) {
        this(messageService, httpStatusResolver, codeProvider, responseProperties,
                new CocoTraceProperties(),
                new DefaultCocoResponseBodyFactory());
    }

    /**
     * <p>
     * 创建 Coco Web 全局异常处理器。
     * </p>
     * @param messageService Coco 消息服务
     * @param httpStatusResolver 异常 HTTP 状态解析器
     * @param codeProvider 系统响应码提供器
     * @param responseProperties 统一响应配置
     * @param traceProperties Trace 配置
     */
    public CocoWebExceptionHandler(CocoMessageService messageService,
            CocoExceptionHttpStatusResolver httpStatusResolver, CocoSystemCodeProvider codeProvider,
            CocoResponseProperties responseProperties, CocoTraceProperties traceProperties) {
        this(messageService, httpStatusResolver, codeProvider, responseProperties, traceProperties,
                new DefaultCocoResponseBodyFactory());
    }

    /**
     * <p>
     * 创建 Coco Web 全局异常处理器。
     * </p>
     * @param messageService Coco 消息服务
     * @param httpStatusResolver 异常 HTTP 状态解析器
     * @param codeProvider 系统响应码提供器
     * @param responseProperties 统一响应配置
     * @param responseBodyFactory 响应体工厂
     */
    public CocoWebExceptionHandler(CocoMessageService messageService,
            CocoExceptionHttpStatusResolver httpStatusResolver, CocoSystemCodeProvider codeProvider,
            CocoResponseProperties responseProperties, CocoTraceProperties traceProperties,
            CocoResponseBodyFactory responseBodyFactory) {
        this.messageService = Objects.requireNonNull(messageService, "messageService must not be null");
        this.httpStatusResolver = Objects.requireNonNull(httpStatusResolver,
                "httpStatusResolver must not be null");
        this.codeProvider = Objects.requireNonNull(codeProvider, "codeProvider must not be null");
        this.responseProperties = responseProperties == null ? new CocoResponseProperties() : responseProperties;
        this.traceProperties = traceProperties == null ? new CocoTraceProperties() : traceProperties;
        this.responseBodyFactory = Objects.requireNonNull(responseBodyFactory,
                "responseBodyFactory must not be null");
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
    public ResponseEntity<Object> handleCocoException(CocoException exception, WebRequest request) {
        CocoException checkedException = Objects.requireNonNull(exception, "exception must not be null");
        HttpStatusCode statusCode = Objects.requireNonNull(this.httpStatusResolver.resolve(checkedException),
                "resolved http status must not be null");
        String message = this.messageService.resolve(checkedException.message());
        int code = checkedException.businessCode()
                .orElseGet(() -> resolveSystemCode(checkedException, statusCode));
        return error(statusCode, code, message, request);
    }

    /**
     * <p>
     * 处理 Spring MVC 请求参数异常，并返回统一异常响应。
     * </p>
     * @param exception Spring MVC 请求参数异常
     * @param request 当前 Web 请求
     * @return 统一异常响应实体
     */
    @ExceptionHandler({
            BindException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<Object> handleBadRequestException(Exception exception, WebRequest request) {
        Objects.requireNonNull(exception, "exception must not be null");
        String message = this.messageService.getMessage(BAD_REQUEST_MESSAGE_CODE);
        return error(HttpStatus.BAD_REQUEST, this.codeProvider.invalidArgument(), message, request);
    }

    /**
     * <p>
     * 处理 Spring MVC 请求资源不存在异常，并返回统一异常响应。
     * </p>
     * @param exception Spring MVC 资源不存在异常
     * @param request 当前 Web 请求
     * @return 统一异常响应实体
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Object> handleNotFoundException(NoHandlerFoundException exception, WebRequest request) {
        Objects.requireNonNull(exception, "exception must not be null");
        String message = this.messageService.getMessage(CocoCommonErrorCode.NOT_FOUND, resolvePath(request));
        return error(HttpStatus.NOT_FOUND, this.codeProvider.notFound(), message, request);
    }

    /**
     * <p>
     * 处理 Spring MVC 请求方法不支持异常，并返回统一异常响应。
     * </p>
     * @param exception Spring MVC 请求方法不支持异常
     * @param request 当前 Web 请求
     * @return 统一异常响应实体
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> handleMethodNotAllowedException(
            HttpRequestMethodNotSupportedException exception, WebRequest request) {
        Objects.requireNonNull(exception, "exception must not be null");
        String message = this.messageService.getMessage(METHOD_NOT_ALLOWED_MESSAGE_CODE);
        return error(HttpStatus.METHOD_NOT_ALLOWED, this.codeProvider.invalidArgument(), message, request);
    }

    /**
     * <p>
     * 处理未捕获异常，并返回统一异常响应。
     * </p>
     * @param exception 未捕获异常
     * @param request 当前 Web 请求
     * @return 统一异常响应实体
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnhandledException(Exception exception, WebRequest request) {
        Objects.requireNonNull(exception, "exception must not be null");
        String message = this.messageService.getMessage(CocoCommonErrorCode.INTERNAL_ERROR);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, this.codeProvider.internalError(), message, request);
    }

    private ResponseEntity<Object> error(HttpStatusCode statusCode, int code, String message, WebRequest request) {
        CocoResponseMetadata metadata = CocoResponseMetadata.from(this.responseProperties,
                resolveTraceIdForBody(), resolvePath(request));
        Object response = this.responseBodyFactory.error(CocoResponsePayload.error(code, message, metadata));
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(statusCode);
        applyTraceCookie(builder);
        return builder.body(response);
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
        if (exception instanceof CocoPayloadTooLargeException) {
            return statusCode == null ? 413 : statusCode.value();
        }
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
        if (statusCode != null && statusCode.value() == 413) {
            return statusCode.value();
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

    private String resolveTraceIdForBody() {
        if (!this.responseProperties.getMetadataMode().includesTraceId()) {
            return null;
        }
        return CocoTraceContext.getOrCreateTraceId();
    }

    private void applyTraceCookie(ResponseEntity.BodyBuilder builder) {
        if (!this.responseProperties.getMetadataMode().writesTraceCookie()
                || this.traceProperties.isResponseCookieEnabled()) {
            return;
        }
        String traceId = CocoTraceContext.currentTraceId().orElseGet(CocoTraceContext::getOrCreateTraceId);
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from(this.traceProperties.getCookieName(),
                traceId)
                .path(this.traceProperties.getCookiePath())
                .httpOnly(this.traceProperties.isCookieHttpOnly())
                .secure(this.traceProperties.isCookieSecure());
        if (this.traceProperties.getCookieMaxAge() >= 0) {
            cookieBuilder.maxAge(this.traceProperties.getCookieMaxAge());
        }
        if (this.traceProperties.getCookieSameSite() != null && !this.traceProperties.getCookieSameSite().isBlank()) {
            cookieBuilder.sameSite(this.traceProperties.getCookieSameSite());
        }
        builder.header(HttpHeaders.SET_COOKIE, cookieBuilder.build().toString());
    }
}
