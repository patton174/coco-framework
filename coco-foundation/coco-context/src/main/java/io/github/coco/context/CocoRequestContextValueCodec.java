package io.github.coco.context;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Coco 请求上下文字段值编解码器。
 * </p>
 * <p>
 * 负责将请求上下文中的多值字段编码为单个字符串，并在读取时恢复为原始列表，
 * 避免参数值、代理链或其他上下文字段中包含逗号时发生信息丢失。
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
 * @since 1.0.0
 */
public final class CocoRequestContextValueCodec {

    private static final String LIST_PREFIX = "coco:list:";

    private CocoRequestContextValueCodec() {
    }

    /**
     * <p>
     * 将多值字段编码为请求上下文可安全传递的字符串。
     * </p>
     * @param values 多值字段
     * @return 编码后的字符串；无可用值时返回 {@code null}
     */
    public static String encodeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> normalizedValues = new ArrayList<>(values.size());
        for (String value : values) {
            normalizedValues.add(value == null ? "" : value);
        }
        StringBuilder builder = new StringBuilder(LIST_PREFIX).append(normalizedValues.size());
        for (String value : normalizedValues) {
            builder.append('|').append(value.length()).append(':').append(value);
        }
        return builder.toString();
    }

    /**
     * <p>
     * 将请求上下文中的结构化多值字段恢复为原始列表。
     * </p>
     * @param encodedValue 编码后的字段值
     * @return 解码后的值列表
     * @throws IllegalArgumentException 当输入不是 Coco 结构化列表编码时抛出
     */
    public static List<String> decodeList(String encodedValue) {
        if (encodedValue == null || encodedValue.isBlank()) {
            return List.of();
        }
        String value = encodedValue.trim();
        if (!value.startsWith(LIST_PREFIX)) {
            throw new IllegalArgumentException("encodedValue is not a coco structured list");
        }
        String payload = value.substring(LIST_PREFIX.length());
        int separatorIndex = payload.indexOf('|');
        String sizeSegment = separatorIndex < 0 ? payload : payload.substring(0, separatorIndex);
        int expectedSize = parsePositiveInteger(sizeSegment, "list size");
        if (expectedSize == 0) {
            return List.of();
        }
        if (separatorIndex < 0) {
            throw new IllegalArgumentException("structured list payload is incomplete");
        }
        int cursor = separatorIndex + 1;
        List<String> values = new ArrayList<>(expectedSize);
        for (int index = 0; index < expectedSize; index++) {
            int colonIndex = payload.indexOf(':', cursor);
            if (colonIndex < 0) {
                throw new IllegalArgumentException("structured list item length is missing");
            }
            int itemLength = parsePositiveInteger(payload.substring(cursor, colonIndex), "item length");
            int itemStart = colonIndex + 1;
            int itemEnd = itemStart + itemLength;
            if (itemEnd > payload.length()) {
                throw new IllegalArgumentException("structured list item exceeds payload length");
            }
            values.add(payload.substring(itemStart, itemEnd));
            cursor = itemEnd;
            if (index + 1 < expectedSize) {
                if (cursor >= payload.length() || payload.charAt(cursor) != '|') {
                    throw new IllegalArgumentException("structured list item separator is missing");
                }
                cursor++;
            }
        }
        if (cursor != payload.length()) {
            throw new IllegalArgumentException("structured list payload contains trailing content");
        }
        return List.copyOf(values);
    }

    /**
     * <p>
     * 判断字段值是否为 Coco 结构化列表编码。
     * </p>
     * @param value 字段值
     * @return 结构化列表编码时返回 {@code true}
     */
    public static boolean isEncodedList(String value) {
        return value != null && value.startsWith(LIST_PREFIX);
    }

    private static int parsePositiveInteger(String value, String label) {
        try {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " is invalid", ex);
        }
    }
}
