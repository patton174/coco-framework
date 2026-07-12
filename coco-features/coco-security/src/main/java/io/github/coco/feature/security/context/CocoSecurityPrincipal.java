package io.github.coco.feature.security.context;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Coco 安全主体。
 * <p>
 * 表达当前调用方身份、角色、权限和附加属性，不绑定 JWT、Session、OAuth2 或具体网关实现。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-security}</li>
 * </ul>
 * @param principalId 主体标识
 * @param principalName 主体名称
 * @param roles 角色集合
 * @param permissions 权限集合
 * @param attributes 扩展属性
 * @author patton174
 * @since 1.0.0
 */
public record CocoSecurityPrincipal(
        String principalId,
        String principalName,
        Set<String> roles,
        Set<String> permissions,
        Map<String, Object> attributes) {

    /**
     * <p>
     * 创建安全主体并复制集合属性。
     * </p>
     */
    public CocoSecurityPrincipal {
        principalId = requireText(principalId, "principalId");
        principalName = principalName == null || principalName.isBlank() ? principalId : principalName.trim();
        roles = Set.copyOf(roles == null ? Set.of() : roles);
        permissions = Set.copyOf(permissions == null ? Set.of() : permissions);
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }

    /**
     * <p>
     * 使用主体标识和名称创建安全主体。
     * </p>
     * @param principalId 主体标识
     * @param principalName 主体名称
     * @return 安全主体
     */
    public static CocoSecurityPrincipal of(String principalId, String principalName) {
        return new CocoSecurityPrincipal(principalId, principalName, Set.of(), Set.of(), Map.of());
    }

    /**
     * <p>
     * 判断主体是否具有指定角色。
     * </p>
     * @param role 角色编码
     * @return 具有该角色时返回 {@code true}
     */
    public boolean hasRole(String role) {
        return role != null && this.roles.contains(role);
    }

    /**
     * <p>
     * 判断主体是否具有指定权限。
     * </p>
     * @param permission 权限编码
     * @return 具有该权限时返回 {@code true}
     */
    public boolean hasPermission(String permission) {
        return permission != null && this.permissions.contains(permission);
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
