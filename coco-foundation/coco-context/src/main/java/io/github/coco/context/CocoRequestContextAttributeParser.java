package io.github.coco.context;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 请求上下文属性解析工具。
 * <p>
 * 从属性 {@code Map} 中提取字符串、数值、布尔和列表类型的属性值，
 * 供 {@link CocoRequestContext} 及其视图对象复用。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-context}</li>
 * </ul>
 * @author patton174
 * @since 1.1.0
 */
final class CocoRequestContextAttributeParser {

    private CocoRequestContextAttributeParser() {
    }

    static Optional<String> attribute(Map<String, String> attributes, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(attributes.get(name.trim()));
    }

    static Optional<Long> longAttribute(Map<String, String> attributes, String name) {
        return attribute(attributes, name).flatMap(CocoRequestContextAttributeParser::parseLong);
    }

    static Optional<Integer> intAttribute(Map<String, String> attributes, String name) {
        return attribute(attributes, name).flatMap(CocoRequestContextAttributeParser::parseInteger);
    }

    static boolean booleanAttribute(Map<String, String> attributes, String name) {
        return attribute(attributes, name)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    static Optional<List<String>> listAttribute(Map<String, String> attributes, String name,
            boolean legacyCsvFallback) {
        return attribute(attributes, name)
                .map(value -> decodeAttributeList(value, legacyCsvFallback));
    }

    static Map<String, String> prefixedAttributes(Map<String, String> attributes, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        attributes.forEach((name, value) -> {
            if (name.startsWith(prefix) && name.length() > prefix.length()) {
                values.put(name.substring(prefix.length()), value);
            }
        });
        return values.isEmpty() ? Map.of() : Collections.unmodifiableMap(values);
    }

    private static Optional<Long> parseLong(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        }
        catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> parseInteger(String value) {
        try {
            return Optional.of(Integer.parseInt(value));
        }
        catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static List<String> decodeAttributeList(String value, boolean legacyCsvFallback) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        if (CocoRequestContextValueCodec.isEncodedList(value)) {
            try {
                return CocoRequestContextValueCodec.decodeList(value);
            }
            catch (IllegalArgumentException ignored) {
                return List.of(value.trim());
            }
        }
        if (legacyCsvFallback) {
            return Arrays.stream(value.split(",", -1))
                    .map(String::trim)
                    .toList();
        }
        return List.of(value.trim());
    }
}
