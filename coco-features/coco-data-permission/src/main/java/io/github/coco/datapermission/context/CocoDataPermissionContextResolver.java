package io.github.coco.datapermission.context;

import java.util.Optional;

/**
 * Coco 数据权限上下文解析器。
 * <p>
 * 安全模块、租户模块、组织架构适配器或测试环境可以替换该接口，把当前调用方的数据范围转换为 Coco 数据权限上下文。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoDataPermissionContextResolver {

    /**
     * <p>
     * 解析当前调用上下文中的数据权限上下文。
     * </p>
     * @return 数据权限上下文；无法解析时为空
     */
    Optional<CocoDataPermissionContext> resolve();
}
