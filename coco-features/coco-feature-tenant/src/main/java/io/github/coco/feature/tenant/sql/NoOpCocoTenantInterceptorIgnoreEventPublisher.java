package io.github.coco.feature.tenant.sql;

/**
 * 空操作的租户拦截器忽略治理事件发布器。
 * <p>
 * 作为默认实现保留事件扩展边界，避免租户模块强制依赖审计模块。
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
public final class NoOpCocoTenantInterceptorIgnoreEventPublisher
        implements CocoTenantInterceptorIgnoreEventPublisher {

    /**
     * {@inheritDoc}
     */
    @Override
    public void publish(CocoTenantInterceptorIgnoreEvent event) {
    }
}
