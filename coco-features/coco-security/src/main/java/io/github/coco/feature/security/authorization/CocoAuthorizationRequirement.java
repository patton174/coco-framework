package io.github.coco.feature.security.authorization;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * 已规范化的方法授权要求。
 * <p>
 * 该类型是 {@link CocoMethodAuthorizationManager} 的稳定输入。角色和权限保留声明顺序、去除首尾空白并去重。
 * </p>
 * @param roles 要求的角色编码
 * @param roleMode 角色组合方式
 * @param permissions 要求的权限编码
 * @param permissionMode 权限组合方式
 * @author patton174
 * @since 1.0.0
 */
@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The canonical constructor normalizes lists with "
        + "List.copyOf, so record accessors expose immutable values only.")
public record CocoAuthorizationRequirement(
        List<String> roles,
        CocoAuthorizationMode roleMode,
        List<String> permissions,
        CocoAuthorizationMode permissionMode) {

    /**
     * 创建并严格校验规范化授权要求。
     */
    public CocoAuthorizationRequirement {
        roles = normalize(roles, "roles");
        permissions = normalize(permissions, "permissions");
        roleMode = Objects.requireNonNull(roleMode, "roleMode must not be null");
        permissionMode = Objects.requireNonNull(permissionMode, "permissionMode must not be null");
    }

    /**
     * 由授权注解创建规范化授权要求。
     * @param authorize 授权注解
     * @return 规范化授权要求
     */
    public static CocoAuthorizationRequirement from(CocoAuthorize authorize) {
        Objects.requireNonNull(authorize, "authorize must not be null");
        return new CocoAuthorizationRequirement(List.of(authorize.roles()), authorize.roleMode(),
                List.of(authorize.permissions()), authorize.permissionMode());
    }

    private static List<String> normalize(List<String> values, String name) {
        Objects.requireNonNull(values, name + " must not be null");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                throw new IllegalArgumentException(name + " must not contain null");
            }
            String candidate = value.trim();
            if (candidate.isBlank()) {
                throw new IllegalArgumentException(name + " must not contain blank values");
            }
            if (candidate.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException(name + " must not contain ISO control characters");
            }
            normalized.add(candidate);
        }
        return List.copyOf(new ArrayList<>(normalized));
    }
}
