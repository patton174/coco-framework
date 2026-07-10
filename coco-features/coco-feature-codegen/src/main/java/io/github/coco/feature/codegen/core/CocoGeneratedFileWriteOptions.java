package io.github.coco.feature.codegen.core;

/**
 * Coco 生成文件写入选项。
 * <p>
 * 默认拒绝覆盖已有文件；dry-run 只返回经过校验的目标路径，不创建目录或文件。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-codegen}</li>
 * </ul>
 * @param overwrite 是否覆盖已有文件
 * @param dryRun 是否只执行计算和校验
 * @author patton174
 * @since 1.0.0
 */
public record CocoGeneratedFileWriteOptions(boolean overwrite, boolean dryRun) {

    /**
     * <p>
     * 返回默认写入选项。
     * </p>
     * @return 默认不覆盖且真实写入的选项
     */
    public static CocoGeneratedFileWriteOptions defaults() {
        return new CocoGeneratedFileWriteOptions(false, false);
    }
}
