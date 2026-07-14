package io.github.coco.context.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SQL 标识符归一化工具。
 * <p>
 * 仅把由合法未引号标识符或 ANSI、MySQL、SQL Server 引号标识符组成的限定名按段归一化，避免将任意
 * 含点字符串误认为 schema-qualified 标识符。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
public final class CocoSqlIdentifierNormalizer {

    private static final String SEGMENT_SEPARATOR = "\u001f";

    private CocoSqlIdentifierNormalizer() {
    }

    /**
     * <p>
     * 按 SQL 标识符段归一化名称。
     * </p>
     * @param identifier SQL 标识符或限定名
     * @return 归一化名称；空值返回空字符串
     */
    public static String normalize(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return "";
        }
        String source = identifier.trim();
        List<String> segments = new ArrayList<>();
        int index = 0;
        while (index < source.length()) {
            index = skipWhitespace(source, index);
            Segment segment = segmentAt(source, index);
            if (segment == null) {
                return source.toLowerCase(Locale.ROOT);
            }
            segments.add(segment.value().toLowerCase(Locale.ROOT));
            index = skipWhitespace(source, segment.end());
            if (index == source.length()) {
                return String.join(SEGMENT_SEPARATOR, segments);
            }
            if (source.charAt(index) != '.') {
                return source.toLowerCase(Locale.ROOT);
            }
            index++;
            if (skipWhitespace(source, index) == source.length()) {
                return source.toLowerCase(Locale.ROOT);
            }
        }
        return source.toLowerCase(Locale.ROOT);
    }

    private static Segment segmentAt(String source, int index) {
        if (index == source.length()) {
            return null;
        }
        char first = source.charAt(index);
        if (first == '"' || first == '`' || first == '[') {
            return quotedSegment(source, index, first == '[' ? ']' : first);
        }
        if (!isIdentifierStart(first)) {
            return null;
        }
        int end = index + 1;
        while (end < source.length() && isIdentifierPart(source.charAt(end))) {
            end++;
        }
        return new Segment(source.substring(index, end), end);
    }

    private static Segment quotedSegment(String source, int index, char closing) {
        StringBuilder value = new StringBuilder();
        for (int current = index + 1; current < source.length(); current++) {
            char character = source.charAt(current);
            if (character != closing) {
                value.append(character);
                continue;
            }
            if (current + 1 < source.length() && source.charAt(current + 1) == closing) {
                value.append(closing);
                current++;
                continue;
            }
            return new Segment(value.toString(), current + 1);
        }
        return null;
    }

    private static int skipWhitespace(String value, int index) {
        int current = index;
        while (current < value.length() && Character.isWhitespace(value.charAt(current))) {
            current++;
        }
        return current;
    }

    private static boolean isIdentifierStart(char character) {
        return Character.isLetter(character) || character == '_' || character == '$';
    }

    private static boolean isIdentifierPart(char character) {
        return isIdentifierStart(character) || Character.isDigit(character);
    }

    private record Segment(String value, int end) {
    }
}
