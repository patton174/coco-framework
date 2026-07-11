package io.github.coco.logging.access;

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
 *   <li>模块：{@code coco-logging}</li>
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
        return String.join(System.lineSeparator(), formatEntries(accessLog, properties));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> formatEntries(CocoAccessLog accessLog, CocoAccessLogProperties properties) {
        CocoAccessLog checkedAccessLog = Objects.requireNonNull(accessLog, "accessLog must not be null");
        CocoAccessLogProperties checkedProperties = properties == null ? new CocoAccessLogProperties() : properties;
        if (checkedProperties.getStyle() == CocoAccessLogStyle.JSON) {
            return List.of(formatJson(checkedAccessLog));
        }
        return List.of(formatTextRequest(checkedAccessLog), formatTextResponse(checkedAccessLog));
    }

    private static String formatTextRequest(CocoAccessLog accessLog) {
        StringBuilder builder = new StringBuilder();
        builder.append("▸ request").append(System.lineSeparator());
        builder.append(detail("traceId", accessLog.traceId())).append(System.lineSeparator());
        builder.append(detail("method", accessLog.method().orElse(""))).append(System.lineSeparator());
        builder.append(detail("path", formatPath(accessLog.path().orElse(null), accessLog.queryString().orElse(null))));
        appendOptionalLine(builder, accessLog.clientIp(), "clientIp");
        appendOptionalLine(builder, accessLog.clientIpSource(), "clientIpSource");
        appendOptionalLine(builder, accessLog.userAgent().map(DefaultCocoAccessLogFormatter::quote), "userAgent");
        appendOptionalLine(builder, accessLog.contentType(), "contentType");
        appendOptionalLine(builder, accessLog.requestTargetSource(), "targetSource");
        appendOptionalLine(builder, accessLog.payloadParseStatus(), "payloadParseStatus");
        appendOptionalLine(builder, accessLog.browserFingerprint(), "browserFingerprint");
        appendOptionalLine(builder, accessLog.requestBodyStage(), "bodyStage");
        accessLog.requestBodyLength().ifPresent(value ->
                builder.append(System.lineSeparator()).append(detail("bodyLength", Long.toString(value))));
        accessLog.requestBodySha256().ifPresent(value ->
                builder.append(System.lineSeparator()).append(detail("bodySha256", value)));
        if (!accessLog.headers().isEmpty()) {
            builder.append(System.lineSeparator()).append(detail("headers", formatEntries(accessLog.headers())));
        }
        if (!accessLog.requestParameters().isEmpty()) {
            builder.append(System.lineSeparator()).append(detail("params", formatParameters(accessLog.requestParameters())));
        }
        return builder.toString();
    }

    private static String formatTextResponse(CocoAccessLog accessLog) {
        StringBuilder builder = new StringBuilder();
        builder.append("◂ response")
                .append(System.lineSeparator())
                .append(detail("traceId", accessLog.traceId()))
                .append(System.lineSeparator())
                .append(detail("status", Integer.toString(accessLog.status())))
                .append(System.lineSeparator())
                .append(detail("duration", accessLog.durationMillis() + "ms"))
                .append(System.lineSeparator())
                .append(detail("success", Boolean.toString(accessLog.success())));
        accessLog.exceptionType().ifPresent(value ->
                builder.append(System.lineSeparator()).append(detail("exception", value)));
        return builder.toString();
    }

    private static String formatJson(CocoAccessLog accessLog) {
        StringBuilder builder = new StringBuilder().append('{');
        appendJsonField(builder, "traceId", accessLog.traceId(), true);
        appendJsonField(builder, "method", accessLog.method().orElse(null), false);
        appendJsonField(builder, "path", accessLog.path().orElse(null), false);
        appendJsonField(builder, "clientIp", accessLog.clientIp().orElse(null), false);
        appendJsonField(builder, "clientIpSource", accessLog.clientIpSource().orElse(null), false);
        appendJsonField(builder, "userAgent", accessLog.userAgent().orElse(null), false);
        appendJsonField(builder, "contentType", accessLog.contentType().orElse(null), false);
        appendJsonField(builder, "queryString", accessLog.queryString().orElse(null), false);
        if (!accessLog.headers().isEmpty()) {
            appendJsonField(builder, "headers", formatMapJson(accessLog.headers()), false, false);
        }
        appendJsonField(builder, "requestBodySha256", accessLog.requestBodySha256().orElse(null), false);
        appendJsonField(builder, "requestBodyLength", accessLog.requestBodyLength().map(Object::toString).orElse(null),
                false, false);
        appendJsonField(builder, "requestBodyStage", accessLog.requestBodyStage().orElse(null), false);
        appendJsonField(builder, "browserFingerprint", accessLog.browserFingerprint().orElse(null), false);
        appendJsonField(builder, "payloadParseStatus", accessLog.payloadParseStatus().orElse(null), false);
        appendJsonField(builder, "requestTargetSource", accessLog.requestTargetSource().orElse(null), false);
        appendJsonField(builder, "parameters", formatParametersJson(accessLog.requestParameters()), false, false);
        appendJsonField(builder, "status", Integer.toString(accessLog.status()), false, false);
        appendJsonField(builder, "durationMs", Long.toString(accessLog.durationMillis()), false, false);
        appendJsonField(builder, "success", Boolean.toString(accessLog.success()), false, false);
        accessLog.exceptionType().ifPresent(value -> appendJsonField(builder, "exceptionType", value, false));
        return builder.append('}').toString();
    }

    private static String detail(String label, String value) {
        return String.format("  %-18s %s", label, value == null ? "" : value);
    }

    private static void appendOptionalLine(StringBuilder builder, java.util.Optional<String> value, String label) {
        value.filter(item -> !item.isBlank())
                .ifPresent(item -> builder.append(System.lineSeparator()).append(detail(label, item)));
    }

    private static String formatPath(String path, String queryString) {
        if (path == null || path.isBlank()) {
            return queryString == null || queryString.isBlank() ? "" : "?" + queryString;
        }
        if (queryString == null || queryString.isBlank()) {
            return path;
        }
        return path + '?' + queryString;
    }

    private static String formatEntries(Map<String, String> entries) {
        if (entries.isEmpty()) {
            return "";
        }
        return entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; "));
    }

    private static String formatParameters(Map<String, List<String>> parameters) {
        if (parameters.isEmpty()) {
            return "";
        }
        return parameters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                .collect(Collectors.joining("&"));
    }

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

    private static String formatMapJson(Map<String, String> entries) {
        StringBuilder builder = new StringBuilder().append('{');
        boolean firstEntry = true;
        for (Map.Entry<String, String> entry : entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            if (!firstEntry) {
                builder.append(',');
            }
            builder.append('"').append(escape(entry.getKey())).append("\":\"")
                    .append(escape(entry.getValue())).append('"');
            firstEntry = false;
        }
        return builder.append('}').toString();
    }

    private static void appendJsonField(StringBuilder builder, String name, String value, boolean first) {
        appendJsonField(builder, name, value, first, true);
    }

    private static void appendJsonField(StringBuilder builder, String name, String value, boolean first,
            boolean quoteValue) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!first && builder.charAt(builder.length() - 1) != '{') {
            builder.append(',');
        }
        builder.append('"').append(escape(name)).append("\":");
        if (quoteValue) {
            builder.append('"').append(escape(value)).append('"');
        }
        else {
            builder.append(value);
        }
    }

    private static String quote(String value) {
        return '"' + value + '"';
    }

    private static String escape(String value) {
        String source = value == null ? "" : value;
        StringBuilder builder = new StringBuilder(source.length());
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
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
