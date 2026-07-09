package io.github.coco.feature.web.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.common.i18n.api.CocoMessageService;
import io.github.coco.common.trace.CocoTraceContext;
import io.github.coco.feature.web.trace.CocoTraceProperties;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Coco Web 正常响应包装处理器。
 * <p>
 * 在 Spring MVC 写出响应体前，将普通业务返回值包装为统一响应体，并保留显式跳过、文件下载和已经包装的响应。
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
public class CocoResponseWrapAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CocoResponseWrapAdvice.class);

    private final CocoMessageService messageService;

    private final CocoResponseWrapProperties properties;

    private final CocoSystemCodeProvider codeProvider;

    private final ObjectMapper objectMapper;

    private final CocoResponseProperties responseProperties;

    private final CocoTraceProperties traceProperties;

    private final CocoResponseBodyFactory responseBodyFactory;

    /**
     * <p>
     * 创建正常响应包装处理器。
     * </p>
     * @param messageService Coco 消息服务
     * @param properties 正常响应包装配置
     * @param codeProvider 系统响应码提供器
     * @param objectMapper JSON 序列化器
     */
    public CocoResponseWrapAdvice(CocoMessageService messageService, CocoResponseWrapProperties properties,
            CocoSystemCodeProvider codeProvider, ObjectMapper objectMapper) {
        this(messageService, properties, codeProvider, objectMapper, new CocoResponseProperties());
    }

    /**
     * <p>
     * 创建正常响应包装处理器。
     * </p>
     * @param messageService Coco 消息服务
     * @param properties 正常响应包装配置
     * @param codeProvider 系统响应码提供器
     * @param objectMapper JSON 序列化器
     * @param responseProperties 统一响应配置
     */
    public CocoResponseWrapAdvice(CocoMessageService messageService, CocoResponseWrapProperties properties,
            CocoSystemCodeProvider codeProvider, ObjectMapper objectMapper,
            CocoResponseProperties responseProperties) {
        this(messageService, properties, codeProvider, objectMapper, responseProperties,
                new CocoTraceProperties(),
                new DefaultCocoResponseBodyFactory());
    }

    /**
     * <p>
     * 创建正常响应包装处理器。
     * </p>
     * @param messageService Coco 消息服务
     * @param properties 正常响应包装配置
     * @param codeProvider 系统响应码提供器
     * @param objectMapper JSON 序列化器
     * @param responseProperties 统一响应配置
     * @param traceProperties Trace 配置
     */
    public CocoResponseWrapAdvice(CocoMessageService messageService, CocoResponseWrapProperties properties,
            CocoSystemCodeProvider codeProvider, ObjectMapper objectMapper,
            CocoResponseProperties responseProperties, CocoTraceProperties traceProperties) {
        this(messageService, properties, codeProvider, objectMapper, responseProperties, traceProperties,
                new DefaultCocoResponseBodyFactory());
    }

    /**
     * <p>
     * 创建正常响应包装处理器。
     * </p>
     * @param messageService Coco 消息服务
     * @param properties 正常响应包装配置
     * @param codeProvider 系统响应码提供器
     * @param objectMapper JSON 序列化器
     * @param responseProperties 统一响应配置
     * @param responseBodyFactory 响应体工厂
     */
    public CocoResponseWrapAdvice(CocoMessageService messageService, CocoResponseWrapProperties properties,
            CocoSystemCodeProvider codeProvider, ObjectMapper objectMapper,
            CocoResponseProperties responseProperties, CocoTraceProperties traceProperties,
            CocoResponseBodyFactory responseBodyFactory) {
        this.messageService = Objects.requireNonNull(messageService);
        this.properties = properties == null ? new CocoResponseWrapProperties() : properties;
        this.codeProvider = Objects.requireNonNull(codeProvider, "codeProvider must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.responseProperties = responseProperties == null ? new CocoResponseProperties() : responseProperties;
        this.traceProperties = traceProperties == null ? new CocoTraceProperties() : traceProperties;
        this.responseBodyFactory = Objects.requireNonNull(responseBodyFactory,
                "responseBodyFactory must not be null");
    }

    /**
     * <p>
     * 判断当前控制器返回值是否需要执行 Coco 正常响应包装。
     * </p>
     * @param returnType 控制器返回值方法参数
     * @param converterType HTTP 消息转换器类型
     * @return 需要包装时返回 {@code true}
     */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Objects.requireNonNull(returnType);
        return !hasIgnoreAnnotation(returnType)
                && !isSkippedReturnType(returnType.getParameterType());
    }

    /**
     * <p>
     * 在响应体写出前执行正常响应包装。
     * </p>
     * @param body 原始响应体
     * @param returnType 控制器返回值方法参数
     * @param selectedContentType 已选择的响应内容类型
     * @param selectedConverterType 已选择的 HTTP 消息转换器类型
     * @param request 当前请求
     * @param response 当前响应
     * @return 包装后的响应体
     */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        if (isSkippedBody(body) || exceedsMaxBodyBytes(body, selectedContentType, request, response)) {
            return body;
        }
        CocoResponseMetadata metadata = CocoResponseMetadata.from(this.responseProperties,
                resolveTraceIdForBody(), resolvePath(request));
        String successMessageCode = this.properties.getSuccessMessageCode();
        Object wrapped = this.responseBodyFactory.success(CocoResponsePayload.success(this.codeProvider.success(),
                this.messageService.getMessageOrDefault(successMessageCode, successMessageCode), body, metadata));
        applyTraceCookie(response);
        if (StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType)) {
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return writeJson(wrapped);
        }
        return wrapped;
    }

    private static boolean hasIgnoreAnnotation(MethodParameter returnType) {
        Method method = returnType.getMethod();
        return method != null && AnnotatedElementUtils.hasAnnotation(method, CocoIgnoreResponseWrap.class)
                || AnnotatedElementUtils.hasAnnotation(returnType.getContainingClass(), CocoIgnoreResponseWrap.class);
    }

    private static boolean isSkippedReturnType(Class<?> returnType) {
        return CocoApiResponse.class.isAssignableFrom(returnType)
                || ResponseEntity.class.isAssignableFrom(returnType)
                || Resource.class.isAssignableFrom(returnType)
                || byte[].class.equals(returnType);
    }

    private static boolean isSkippedBody(Object body) {
        return body instanceof CocoApiResponse<?>
                || body instanceof ResponseEntity<?>
                || body instanceof Resource
                || body instanceof byte[];
    }

    private boolean exceedsMaxBodyBytes(Object body, MediaType selectedContentType, ServerHttpRequest request,
            ServerHttpResponse response) {
        long maxBodyBytes = this.properties.getMaxBodyBytes();
        if (maxBodyBytes < 0) {
            return false;
        }
        long bodyBytes = knownBodyBytes(body, selectedContentType, response);
        if (bodyBytes <= maxBodyBytes) {
            return false;
        }
        LOGGER.warn("Coco response wrap skipped because bodyBytes={} exceeds maxBodyBytes={} for path={}",
                bodyBytes, maxBodyBytes, resolvePath(request));
        return true;
    }

    private static long knownBodyBytes(Object body, MediaType selectedContentType, ServerHttpResponse response) {
        if (response != null) {
            long contentLength = response.getHeaders().getContentLength();
            if (contentLength >= 0) {
                return contentLength;
            }
        }
        if (body instanceof CharSequence text) {
            return text.toString().getBytes(resolveCharset(selectedContentType)).length;
        }
        return -1L;
    }

    private static Charset resolveCharset(MediaType selectedContentType) {
        Charset charset = selectedContentType == null ? null : selectedContentType.getCharset();
        return charset == null ? StandardCharsets.UTF_8 : charset;
    }

    private String writeJson(Object response) {
        try {
            return this.objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String resolvePath(ServerHttpRequest request) {
        if (request == null || request.getURI() == null) {
            return null;
        }
        String path = request.getURI().getPath();
        return path == null || path.isBlank() ? null : path;
    }

    private String resolveTraceIdForBody() {
        if (!this.responseProperties.getMetadataMode().includesTraceId()) {
            return null;
        }
        return CocoTraceContext.getOrCreateTraceId();
    }

    private void applyTraceCookie(ServerHttpResponse response) {
        if (!this.responseProperties.getMetadataMode().writesTraceCookie()
                || this.traceProperties.isResponseCookieEnabled()) {
            return;
        }
        String traceId = CocoTraceContext.currentTraceId().orElseGet(CocoTraceContext::getOrCreateTraceId);
        org.springframework.http.ResponseCookie.ResponseCookieBuilder builder = org.springframework.http.ResponseCookie
                .from(this.traceProperties.getCookieName(), traceId)
                .path(this.traceProperties.getCookiePath())
                .httpOnly(this.traceProperties.isCookieHttpOnly())
                .secure(this.traceProperties.isCookieSecure());
        if (this.traceProperties.getCookieMaxAge() >= 0) {
            builder.maxAge(this.traceProperties.getCookieMaxAge());
        }
        if (this.traceProperties.getCookieSameSite() != null && !this.traceProperties.getCookieSameSite().isBlank()) {
            builder.sameSite(this.traceProperties.getCookieSameSite());
        }
        response.getHeaders().add(HttpHeaders.SET_COOKIE, builder.build().toString());
    }
}
