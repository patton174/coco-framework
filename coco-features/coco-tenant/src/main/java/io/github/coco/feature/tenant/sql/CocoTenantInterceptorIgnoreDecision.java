package io.github.coco.feature.tenant.sql;

/**
 * Coco 租户拦截器忽略治理决策。
 * <p>
 * 用于描述 MyBatis-Plus {@code @InterceptorIgnore(tenantLine = true)} 命中时框架最终选择放行还是阻断。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-tenant}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public enum CocoTenantInterceptorIgnoreDecision {

    /**
     * 当前 Mapper 语句已进入白名单，允许跳过租户 SQL 隔离。
     */
    ALLOWED,

    /**
     * 当前 Mapper 语句未进入白名单，阻断本次执行。
     */
    BLOCKED
}
