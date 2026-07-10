package io.github.coco.feature.codegen.core;

import java.util.Objects;

/**
 * 显式空操作 Coco 代码生成器。
 * <p>
 * 仅校验请求对象并返回空结果，可供测试或显式占位使用；框架默认 bean 使用真实 FreeMarker 模板生成器。
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
public final class NoOpCocoCodeGenerator implements CocoCodeGenerator {

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoCodegenResult generate(CocoCodegenRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return CocoCodegenResult.empty();
    }
}
