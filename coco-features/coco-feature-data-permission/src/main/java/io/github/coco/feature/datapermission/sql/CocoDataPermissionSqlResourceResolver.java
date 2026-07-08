package io.github.coco.feature.datapermission.sql;

import java.util.Optional;

/**
 * 数据权限 SQL 资源解析器。
 * <p>
 * 将 SQL 中的表和 Mapper 语句标识解析为业务资源标识，业务系统可以替换该接口实现动态资源映射。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoDataPermissionSqlResourceResolver {

    /**
     * <p>
     * 解析当前 SQL 表对应的业务资源标识。
     * </p>
     * @param context SQL 资源解析上下文
     * @return 业务资源标识；未命中时为空
     */
    Optional<String> resolve(CocoDataPermissionSqlResourceContext context);
}
