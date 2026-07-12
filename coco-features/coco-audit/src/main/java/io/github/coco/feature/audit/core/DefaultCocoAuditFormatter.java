package io.github.coco.feature.audit.core;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Coco 默认审计事件格式化器。
 * <p>
 * 使用固定字段顺序输出单行 JSON，并按属性名称排序。字符串中的换行符和控制字符均进行 JSON 转义，避免伪造额外日志行。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-audit}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class DefaultCocoAuditFormatter implements CocoAuditFormatter {

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(CocoAuditEvent event) {
        CocoAuditEvent checkedEvent = Objects.requireNonNull(event, "event must not be null");
        StringBuilder builder = new StringBuilder(256).append('{');
        appendStringField(builder, "type", checkedEvent.type());
        appendStringField(builder, "action", checkedEvent.action().orElse(null));
        appendStringField(builder, "resourceType", checkedEvent.resourceType().orElse(null));
        appendStringField(builder, "resourceId", checkedEvent.resourceId().orElse(null));
        appendStringField(builder, "traceId", checkedEvent.traceId().orElse(null));
        appendStringField(builder, "actor", checkedEvent.actor().orElse(null));
        appendStringField(builder, "tenantId", checkedEvent.tenantId().orElse(null));
        appendFieldName(builder, "success");
        builder.append(checkedEvent.success());
        appendStringField(builder, "occurredAt", checkedEvent.occurredAt().toString());
        appendFieldName(builder, "attributes");
        appendAttributes(builder, checkedEvent.attributes());
        return builder.append('}').toString();
    }

    private static void appendAttributes(StringBuilder builder, Map<String, Object> attributes) {
        builder.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : attributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            if (!first) {
                builder.append(',');
            }
            appendQuoted(builder, entry.getKey());
            builder.append(':');
            appendAttributeValue(builder, entry.getValue());
            first = false;
        }
        builder.append('}');
    }

    private static void appendAttributeValue(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
            return;
        }
        if (value instanceof Boolean booleanValue) {
            builder.append(booleanValue);
            return;
        }
        if (value instanceof Number numberValue) {
            appendNumber(builder, numberValue);
            return;
        }
        if (value instanceof Enum<?> enumValue) {
            appendQuoted(builder, enumValue.name());
            return;
        }
        appendQuoted(builder, String.valueOf(value));
    }

    private static void appendNumber(StringBuilder builder, Number value) {
        String text = String.valueOf(value);
        try {
            builder.append(new BigDecimal(text));
        }
        catch (NumberFormatException ex) {
            appendQuoted(builder, text);
        }
    }

    private static void appendStringField(StringBuilder builder, String name, String value) {
        appendFieldName(builder, name);
        if (value == null) {
            builder.append("null");
        }
        else {
            appendQuoted(builder, value);
        }
    }

    private static void appendFieldName(StringBuilder builder, String name) {
        if (builder.charAt(builder.length() - 1) != '{') {
            builder.append(',');
        }
        appendQuoted(builder, name);
        builder.append(':');
    }

    private static void appendQuoted(StringBuilder builder, String value) {
        builder.append('"');
        appendEscaped(builder, value == null ? "" : value);
        builder.append('"');
    }

    private static void appendEscaped(StringBuilder builder, String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (Character.isISOControl(character) || character == '\u2028' || character == '\u2029'
                            || isUnpairedSurrogate(value, index, character)) {
                        appendUnicodeEscape(builder, character);
                    }
                    else {
                        builder.append(character);
                    }
                }
            }
        }
    }

    private static boolean isUnpairedSurrogate(String value, int index, char character) {
        if (Character.isHighSurrogate(character)) {
            return index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1));
        }
        return Character.isLowSurrogate(character)
                && (index == 0 || !Character.isHighSurrogate(value.charAt(index - 1)));
    }

    private static void appendUnicodeEscape(StringBuilder builder, char character) {
        builder.append("\\u")
                .append(HEX_DIGITS[(character >>> 12) & 0x0f])
                .append(HEX_DIGITS[(character >>> 8) & 0x0f])
                .append(HEX_DIGITS[(character >>> 4) & 0x0f])
                .append(HEX_DIGITS[character & 0x0f]);
    }
}
