package io.github.coco.feature.codegen.core;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Coco 代码生成结果。
 * <p>
 * 保存一次生成调用得到的文件集合。No-Op 或校验型生成器可以返回空结果。
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
public final class CocoCodegenResult {

    private final List<CocoGeneratedFile> files;

    private CocoCodegenResult(Collection<CocoGeneratedFile> files) {
        this.files = files == null ? List.of() : files.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * <p>
     * 创建空代码生成结果。
     * </p>
     * @return 空代码生成结果
     */
    public static CocoCodegenResult empty() {
        return new CocoCodegenResult(List.of());
    }

    /**
     * <p>
     * 根据文件集合创建代码生成结果。
     * </p>
     * @param files 生成文件集合
     * @return 代码生成结果
     */
    public static CocoCodegenResult of(Collection<CocoGeneratedFile> files) {
        return new CocoCodegenResult(files);
    }

    /**
     * <p>
     * 返回生成文件集合。
     * </p>
     * @return 生成文件集合
     */
    public List<CocoGeneratedFile> files() {
        return this.files;
    }

    /**
     * <p>
     * 返回是否生成了文件。
     * </p>
     * @return 存在生成文件时返回 {@code true}
     */
    public boolean hasFiles() {
        return !this.files.isEmpty();
    }
}
