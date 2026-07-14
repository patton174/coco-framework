package io.github.coco.feature.web.trace;

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
     * 规范化 HTTP 请求头中的 TraceId 值。
     * </p>
     * <p>
     * 仅忽略 HTTP 可选空白字符；空值、控制字符和非 ASCII 字符不会被规范化为可接受值。
     * </p>
     * @param traceId 原始请求头值
     * @return 规范化后的 TraceId；无法安全规范化时返回 {@code null}
     */
    static String normalizeHeaderValue(String traceId) {
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
     * 判断规范化后的 TraceId 是否适合作为跨响应头、Cookie、日志和上下文传播的传输值。
     * </p>
     * @param traceId 已规范化的 TraceId
     * @return 可安全传播时返回 {@code true}
     */
    static boolean isTransportSafe(String traceId) {
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

    /**
     * <p>
     * 判断 TraceId 是否可被框架接受。
     * </p>
     * @param traceId 已完成 HTTP 可选空白规范化且通过传输安全校验的 TraceId
     * @return 可接受时返回 {@code true}
     */
    boolean isValid(String traceId);
}
