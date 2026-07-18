package io.github.coco.feature.web.trace;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Coco TraceId 不可放宽的输入安全校验。
 * <p>
 * 统一处理 HTTP 请求头多值聚合、可选空白规范化、长度限制、传输安全字符和重复值冲突。
 * 自定义 {@link CocoTraceIdValidator} 仅在这些框架规则通过后执行，因此只能进一步收紧格式。
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
public final class CocoTraceIdValidation {

    private CocoTraceIdValidation() {
    }

    /**
     * <p>
     * 解析并校验同一 HTTP 请求头的全部 TraceId 值。
     * </p>
     * @param headerValues 同一 TraceId 请求头的全部值
     * @param maxLength 允许接收的 TraceId 最大长度
     * @param validator 仅用于进一步收紧格式的 TraceId 校验器
     * @return 唯一且可安全传播的 TraceId；不存在合法唯一值时为空
     */
    public static Optional<String> resolveHeaderValues(List<String> headerValues, int maxLength,
            CocoTraceIdValidator validator) {
        CocoTraceIdValidator checkedValidator = Objects.requireNonNull(validator, "validator must not be null");
        if (headerValues == null || headerValues.isEmpty()) {
            return Optional.empty();
        }
        int effectiveMaxLength = maxLength <= 0 ? CocoTraceProperties.DEFAULT_MAX_LENGTH : maxLength;
        String resolvedTraceId = null;
        for (String headerValue : headerValues) {
            String candidate = normalizeHeaderValue(headerValue);
            if (candidate == null
                    || candidate.length() > effectiveMaxLength
                    || !isTransportSafe(candidate)) {
                return Optional.empty();
            }
            if (resolvedTraceId != null && !resolvedTraceId.equals(candidate)) {
                return Optional.empty();
            }
            resolvedTraceId = candidate;
        }
        if (resolvedTraceId == null || !checkedValidator.isValid(resolvedTraceId)) {
            return Optional.empty();
        }
        return Optional.of(resolvedTraceId);
    }

    /**
     * <p>
     * 按 HTTP 可选空白规则规范化请求头值，并拒绝空值、控制字符和非 ASCII 字符。
     * </p>
     * @param traceId 原始请求头值
     * @return 规范化后的 TraceId；无法安全规范化时返回 {@code null}
     */
    public static String normalizeHeaderValue(String traceId) {
        if (traceId == null) {
            return null;
        }
        int start = 0;
        int end = traceId.length();
        while (start < end && optionalWhitespace(traceId.charAt(start))) {
            start++;
        }
        while (end > start && optionalWhitespace(traceId.charAt(end - 1))) {
            end--;
        }
        if (start == end) {
            return null;
        }
        String normalized = traceId.substring(start, end);
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character < 0x21 || character > 0x7e) {
                return null;
            }
        }
        return normalized;
    }

    /**
     * <p>
     * 判断规范化后的 TraceId 是否适合作为响应头、Cookie、日志和上下文传播值。
     * </p>
     * @param traceId 已规范化的 TraceId
     * @return 可安全传播时返回 {@code true}
     */
    public static boolean isTransportSafe(String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            return false;
        }
        for (int index = 0; index < traceId.length(); index++) {
            char character = traceId.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= 'A' && character <= 'Z')
                    && !(character >= '0' && character <= '9')
                    && character != '.' && character != '_' && character != ':' && character != '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean optionalWhitespace(char character) {
        return character == ' ' || character == '\t';
    }
}
