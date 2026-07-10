package io.github.coco.feature.codegen.core;

/**
 * Coco 代码生成异常。
 * <p>
 * 用于报告模板组、manifest、模板读取、渲染或文件写入阶段的可定位失败。
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
public class CocoCodegenException extends RuntimeException {

    /**
     * <p>
     * 创建代码生成异常。
     * </p>
     * @param message 异常说明
     */
    public CocoCodegenException(String message) {
        super(message);
    }

    /**
     * <p>
     * 创建带根因的代码生成异常。
     * </p>
     * @param message 异常说明
     * @param cause 根因
     */
    public CocoCodegenException(String message, Throwable cause) {
        super(message, cause);
    }
}
