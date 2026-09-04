package io.github.coco.context;

/**
 * Coco 通用字符串工具。
 * <p>
 * 提供框架内部高频使用的字符串判断和归一化方法，消除各模块独立实现的重复代码。
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
public final class CocoStrings {

    private CocoStrings() {
    }

    /**
     * <p>
     * 将空白字符串归一化为 {@code null}，非空白字符串去除首尾空格。
     * </p>
     * @param value 原始字符串
     * @return 归一化后的字符串；空白时返回 {@code null}
     */
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * <p>
     * 判断字符串是否包含可见文本。
     * </p>
     * @param value 待检查字符串
     * @return 包含可见文本时返回 {@code true}
     */
    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
