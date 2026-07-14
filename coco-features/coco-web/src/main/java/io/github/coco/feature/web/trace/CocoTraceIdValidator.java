package io.github.coco.feature.web.trace;

import java.util.List;
import java.util.Optional;

/**
 * Coco TraceId 校验器。
 * <p>
 * 用于约束从外部请求头接收的 TraceId，避免控制字符、超长文本或不受控字符进入响应头、Cookie、日志 MDC 和请求上下文。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoTraceIdValidator {

    /**
     * <p>
     * 解析并校验同一 HTTP 请求头的全部 TraceId 值。
     * </p>
     * @param headerValues 同一 TraceId 请求头的全部值
     * @param maxLength 允许接收的 TraceId 最大长度
     * @return 唯一且可安全传播的 TraceId；不存在合法唯一值时为空
     * @deprecated 仅为二进制和源码兼容保留。框架调用方必须直接使用
     * {@link CocoTraceIdValidation#resolveHeaderValues(List, int, CocoTraceIdValidator)}，避免实现类覆盖安全规则。
     */
    @Deprecated(since = "1.0.0", forRemoval = false)
    default Optional<String> resolveHeaderValues(List<String> headerValues, int maxLength) {
        return CocoTraceIdValidation.resolveHeaderValues(headerValues, maxLength, this);
    }

    /**
     * <p>
     * 规范化 HTTP 请求头中的 TraceId 值。
     * </p>
     * <p>
     * 仅忽略 HTTP 可选空白字符；空值、控制字符和非 ASCII 字符不会被规范化为可接受值。
     * </p>
     * @param traceId 原始请求头值
     * @return 规范化后的 TraceId；无法安全规范化时返回 {@code null}
     */
    static String normalizeHeaderValue(String traceId) {
        return CocoTraceIdValidation.normalizeHeaderValue(traceId);
    }

    /**
     * <p>
     * 判断规范化后的 TraceId 是否适合作为跨响应头、Cookie、日志和上下文传播的传输值。
     * </p>
     * @param traceId 已规范化的 TraceId
     * @return 可安全传播时返回 {@code true}
     */
    static boolean isTransportSafe(String traceId) {
        return CocoTraceIdValidation.isTransportSafe(traceId);
    }

    /**
     * <p>
     * 判断 TraceId 是否可被框架接受。
     * </p>
     * @param traceId 已完成 HTTP 可选空白规范化且通过传输安全校验的 TraceId
     * @return 可接受时返回 {@code true}
     */
    boolean isValid(String traceId);
}
