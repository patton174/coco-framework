package io.github.coco.feature.web.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.common.i18n.api.CocoMessageService;
import io.github.coco.common.trace.CocoTraceContext;
import java.lang.reflect.Method;
import java.util.Objects;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
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
 * 在 Spring MVC 写出响应体前，将普通业务返回值包装为 {@link CocoApiResponse}，并保留显式跳过、文件下载和已经包装的响应。
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

    private final CocoMessageService messageService;

    private final CocoResponseWrapProperties properties;

    private final ObjectMapper objectMapper;

    /**
     * <p>
     * 创建正常响应包装处理器。
     * </p>
     * @param messageService Coco 消息服务
     * @param properties 正常响应包装配置
     * @param objectMapper JSON 序列化器
     */
    public CocoResponseWrapAdvice(CocoMessageService messageService, CocoResponseWrapProperties properties,
            ObjectMapper objectMapper) {
        this.messageService = Objects.requireNonNull(messageService);
        this.properties = properties == null ? new CocoResponseWrapProperties() : properties;
        this.objectMapper = Objects.requireNonNull(objectMapper);
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
        if (isSkippedBody(body)) {
            return body;
        }
        CocoApiResponse<Object> wrapped = CocoApiResponse.success(
                this.properties.getSuccessCode(),
                this.messageService.getMessage(this.properties.getSuccessMessageCode()),
                body,
                CocoTraceContext.getOrCreateTraceId(),
                resolvePath(request));
        if (StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType)) {
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

    private String writeJson(CocoApiResponse<?> response) {
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
}
