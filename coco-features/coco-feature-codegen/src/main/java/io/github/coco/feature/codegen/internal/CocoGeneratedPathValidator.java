package io.github.coco.feature.codegen.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import io.github.coco.feature.codegen.core.CocoCodegenException;

/**
 * 生成文件路径内部校验器。
 */
public final class CocoGeneratedPathValidator {

    private static final Pattern WINDOWS_DRIVE = Pattern.compile("^[A-Za-z]:.*");

    private static final Pattern WINDOWS_RESERVED_NAME = Pattern.compile(
            "(?i)^(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$");

    private CocoGeneratedPathValidator() {
    }

    /**
     * 归一化并校验生成文件相对路径。
     * @param value 待校验路径
     * @return 使用正斜线分隔的规范化相对路径
     */
    public static String normalizeRelativePath(String value) {
        if (value == null || value.isBlank()) {
            throw new CocoCodegenException("generated path must not be blank");
        }
        String path = value.trim();
        if (path.startsWith("/") || path.startsWith("\\") || WINDOWS_DRIVE.matcher(path).matches()) {
            throw new CocoCodegenException("generated path must be relative: " + value);
        }
        String[] rawSegments = path.replace('\\', '/').split("/", -1);
        List<String> segments = new ArrayList<>(rawSegments.length);
        for (String segment : rawSegments) {
            if (segment.isEmpty() || ".".equals(segment)) {
                throw new CocoCodegenException("generated path must be normalized: " + value);
            }
            if ("..".equals(segment)) {
                throw new CocoCodegenException("generated path must not traverse outside the output directory: " + value);
            }
            if (containsWindowsUnsafeCharacter(segment)
                    || segment.endsWith(".")
                    || segment.endsWith(" ")
                    || WINDOWS_RESERVED_NAME.matcher(segment).matches()
                    || segment.chars().anyMatch(Character::isISOControl)) {
                throw new CocoCodegenException("generated path contains an unsafe segment: " + value);
            }
            segments.add(segment);
        }
        return String.join("/", segments);
    }

    private static boolean containsWindowsUnsafeCharacter(String segment) {
        return segment.chars().anyMatch(character -> character == ':'
                || character == '<'
                || character == '>'
                || character == '"'
                || character == '|'
                || character == '?'
                || character == '*');
    }
}
