package io.github.coco.feature.web.logging;

import java.util.Objects;

import io.github.coco.common.accesslog.CocoAccessLog;

/**
 * Coco 默认接口访问日志格式化器。
 * <p>
 * 支持键值对文本和 JSON 文本两种内置样式，覆盖常见控制台打印和日志采集场景。
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
        StringBuilder builder = new StringBuilder()
                .append("traceId=").append(accessLog.traceId())
                .append(" method=").append(accessLog.method().orElse(""))
                .append(" path=").append(accessLog.path().orElse(""))
                .append(" status=").append(accessLog.status())
                .append(" durationMs=").append(accessLog.durationMillis())
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
                .append(",\"status\":").append(accessLog.status())
                .append(",\"durationMs\":").append(accessLog.durationMillis())
                .append(",\"success\":").append(accessLog.success());
        accessLog.exceptionType().ifPresent(exceptionType -> builder.append(",\"exceptionType\":\"")
                .append(escape(exceptionType)).append('"'));
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
