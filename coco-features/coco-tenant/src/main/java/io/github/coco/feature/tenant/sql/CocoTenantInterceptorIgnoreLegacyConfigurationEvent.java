package io.github.coco.feature.tenant.sql;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Coco 租户隔离旧模式白名单配置事件。
 * <p>
 * 应用仍配置已废弃的 {@code allowed-mapped-statements} 时，框架在启动阶段发布该结构化事件，
 * 便于业务接入日志、告警或配置治理系统，而无需让租户模块依赖具体审计功能。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-tenant}</li>
 * </ul>
 * @param patterns 已配置的旧 MappedStatement ID 模式
 * @param strictMode 是否启用严格模式
 * @author patton174
 * @since 1.0.0
 */
public record CocoTenantInterceptorIgnoreLegacyConfigurationEvent(Set<String> patterns, boolean strictMode) {

    /**
     * 创建不可变的旧模式白名单配置事件。
     */
    public CocoTenantInterceptorIgnoreLegacyConfigurationEvent {
        patterns = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(patterns,
                "patterns must not be null")));
    }
}
