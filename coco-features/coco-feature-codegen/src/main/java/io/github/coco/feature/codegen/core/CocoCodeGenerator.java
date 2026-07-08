package io.github.coco.feature.codegen.core;

/**
 * Coco 代码生成器。
 * <p>
 * 定义框架级代码生成入口，具体模板引擎、数据库元数据读取和文件落盘策略由实现方决定。
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
@FunctionalInterface
public interface CocoCodeGenerator {

    /**
     * <p>
     * 执行代码生成。
     * </p>
     * @param request 代码生成请求
     * @return 代码生成结果
     */
    CocoCodegenResult generate(CocoCodegenRequest request);
}
