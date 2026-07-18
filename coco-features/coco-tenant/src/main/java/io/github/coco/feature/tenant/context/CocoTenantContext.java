package io.github.coco.feature.tenant.context;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Coco 租户上下文。
 * <p>
 * 表达当前调用方所属租户，不绑定 HTTP Header、JWT Claim、数据库字段或具体租户解析方式。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-tenant}</li>
 * </ul>
 * @param tenantId 租户标识
 * @param tenantName 租户名称
 * @param attributes 扩展属性
 * @author patton174
 * @since 1.0.0
 */
public record CocoTenantContext(String tenantId, String tenantName, Map<String, Object> attributes) {

    /**
     * <p>
     * 创建租户上下文并复制扩展属性。
     * </p>
     */
    public CocoTenantContext {
        tenantId = requireText(tenantId, "tenantId");
        tenantName = tenantName == null || tenantName.isBlank() ? tenantId : tenantName.trim();
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }

    /**
     * <p>
     * 使用租户标识和名称创建租户上下文。
     * </p>
     * @param tenantId 租户标识
     * @param tenantName 租户名称
     * @return 租户上下文
     */
    public static CocoTenantContext of(String tenantId, String tenantName) {
        return new CocoTenantContext(tenantId, tenantName, Map.of());
    }

    /**
     * <p>
     * 返回指定扩展属性。
     * </p>
     * @param name 属性名
     * @return 扩展属性；不存在时为空
     */
    public Optional<Object> attribute(String name) {
        return Optional.ofNullable(this.attributes.get(name));
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
