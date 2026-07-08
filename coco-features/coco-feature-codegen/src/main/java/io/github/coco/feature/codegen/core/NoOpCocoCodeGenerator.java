package io.github.coco.feature.codegen.core;

import java.util.Objects;

/**
 * 空操作 Coco 代码生成器。
 * <p>
 * 作为默认实现仅校验请求对象并返回空结果，业务侧或后续框架模块可替换为真实模板生成器。
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
