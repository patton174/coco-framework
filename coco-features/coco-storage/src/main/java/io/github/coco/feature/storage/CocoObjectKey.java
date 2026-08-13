package io.github.coco.feature.storage;

import java.nio.file.Path;
/** 安全对象键校验器。对象键始终使用正斜杠，不映射绝对文件系统路径。 */
public final class CocoObjectKey {
    public static final int MAX_LENGTH = 1024;
    private CocoObjectKey() { }
    public static String validate(String key) {
        if (key == null || key.isEmpty() || key.length() > MAX_LENGTH || key.startsWith("/") || key.startsWith("\\")) invalid();
        if (key.indexOf('\\') >= 0 || key.startsWith("//") || key.matches("(?i)^[a-z]:.*")) invalid();
        for (String segment : key.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) invalid();
            for (int index = 0; index < segment.length(); index++) if (Character.isISOControl(segment.charAt(index))) invalid();
        }
        Path path = Path.of(key).normalize();
        if (path.isAbsolute() || path.startsWith("..")) invalid();
        return key;
    }
    private static void invalid() { throw new IllegalArgumentException("invalid object key"); }
}
