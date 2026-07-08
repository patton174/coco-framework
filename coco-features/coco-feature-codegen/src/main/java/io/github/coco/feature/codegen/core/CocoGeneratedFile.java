package io.github.coco.feature.codegen.core;

/**
 * Coco 代码生成文件结果。
 * <p>
 * 表示一次 dry-run 或真实生成中得到的单个文件路径和内容。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-codegen}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public record CocoGeneratedFile(String path, String content) {

    /**
     * <p>
     * 创建生成文件结果。
     * </p>
     * @param path 文件路径
     * @param content 文件内容
     */
    public CocoGeneratedFile {
        path = requireText(path, "path");
        content = content == null ? "" : content;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
