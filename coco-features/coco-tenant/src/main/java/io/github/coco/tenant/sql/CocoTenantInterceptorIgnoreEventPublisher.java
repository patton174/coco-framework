package io.github.coco.tenant.sql;

/**
 * Coco 租户拦截器忽略治理事件发布器。
 * <p>
 * 这是租户模块预留的审计扩展点。业务系统可注册自定义实现，将事件转发到审计模块、日志中心或告警系统；
 * 租户模块本身不依赖具体审计实现。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-tenant}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoTenantInterceptorIgnoreEventPublisher {

    /**
     * <p>
     * 发布租户拦截器忽略治理事件。
     * </p>
     * @param event 治理事件
     */
    void publish(CocoTenantInterceptorIgnoreEvent event);
}
