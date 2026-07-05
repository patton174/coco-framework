package io.github.coco.common.logging.access;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Coco 默认接口访问日志格式化器。
 * <p>
 * 支持方向箭头文本和 JSON 文本两种内置样式，覆盖控制台排障和日志采集场景。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-logging}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class DefaultCocoAccessLogFormatter implements CocoAccessLogFormatter {

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(CocoAccessLog accessLog, CocoAccessLogProperties properties) {
        CocoAccessLog checkedAccessLog = Objects.requireNonNull(accessLog, "accessLog must not be null");
        CocoAccessLogProperties checkedProperties = properties == null ? new CocoAccessLogProperties() : properties;
        if (checkedProperties.getStyle() == CocoAccessLogStyle.JSON) {
            return formatJson(checkedAccessLog);
        }
        return formatText(checkedAccessLog);
    }

    private static String formatText(CocoAccessLog accessLog) {
        String parameters = formatParameters(accessLog.requestParameters());
        StringBuilder builder = new StringBuilder()
                .append("▸ request  ")
                .append(accessLog.method().orElse(""))
                .append(' ')
                .append(accessLog.path().orElse(""));
        accessLog.queryString().ifPresent(queryString -> builder.append('?').append(queryString));
        builder.append(" | trace=").append(accessLog.traceId())
                .append(" ip=").append(accessLog.clientIp().orElse(""))
                .append(" ua=\"").append(escape(accessLog.userAgent().orElse(""))).append('"');
        if (!parameters.isBlank()) {
            builder.append(" params=\"").append(escape(parameters)).append('"');
        }
        builder.append(" ◂ response ")
                .append(accessLog.status())
                .append(' ')
                .append(accessLog.durationMillis()).append("ms")
                .append(" success=").append(accessLog.success());
        accessLog.exceptionType().ifPresent(exceptionType -> builder.append(" exception=").append(exceptionType));
        return builder.toString();
    }

    private static String formatJson(CocoAccessLog accessLog) {
        StringBuilder builder = new StringBuilder()
                .append('{')
                .append("\"traceId\":\"").append(escape(accessLog.traceId())).append('"')
                .append(",\"method\":\"").append(escape(accessLog.method().orElse(""))).append('"')
                .append(",\"path\":\"").append(escape(accessLog.path().orElse(""))).append('"')
                .append(",\"clientIp\":\"").append(escape(accessLog.clientIp().orElse(""))).append('"')
                .append(",\"queryString\":\"").append(escape(accessLog.queryString().orElse(""))).append('"')
                .append(",\"parameters\":").append(formatParametersJson(accessLog.requestParameters()))
                .append(",\"userAgent\":\"").append(escape(accessLog.userAgent().orElse(""))).append('"')
                .append(",\"status\":").append(accessLog.status())
                .append(",\"durationMs\":").append(accessLog.durationMillis())
                .append(",\"success\":").append(accessLog.success());
        accessLog.exceptionType().ifPresent(exceptionType -> builder.append(",\"exceptionType\":\"")
                .append(escape(exceptionType)).append('"'));
        return builder.append('}').toString();
    }

    /**
     * <p>
     * 将请求参数格式化为可读的键值对文本。
     * </p>
     * @param parameters 请求参数
     * @return 键值对文本
     */
    private static String formatParameters(Map<String, List<String>> parameters) {
        if (parameters.isEmpty()) {
            return "";
        }
        return parameters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    /**
     * <p>
     * 将请求参数格式化为 JSON 对象文本。
     * </p>
     * @param parameters 请求参数
     * @return JSON 对象文本
     */
    private static String formatParametersJson(Map<String, List<String>> parameters) {
        StringBuilder builder = new StringBuilder().append('{');
        boolean firstEntry = true;
        for (Map.Entry<String, List<String>> entry : parameters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            if (!firstEntry) {
                builder.append(',');
            }
            builder.append('"').append(escape(entry.getKey())).append("\":[");
            boolean firstValue = true;
            for (String value : entry.getValue()) {
                if (!firstValue) {
                    builder.append(',');
                }
                builder.append('"').append(escape(value)).append('"');
                firstValue = false;
            }
            builder.append(']');
            firstEntry = false;
        }
        return builder.append('}').toString();
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(character);
            }
        }
        return builder.toString();
    }
}
