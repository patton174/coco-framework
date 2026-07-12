package io.github.coco.feature.security;

import java.util.Optional;

import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityContextHolder;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;

/**
 * Coco 安全访问门面。
 * <p>
 * 为业务代码和框架功能提供显式的安全上下文读取、认证断言和权限断言入口，不绑定具体登录协议、用户模型或权限存储。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-security}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoSecurity {

    private CocoSecurity() {
    }

    /**
     * <p>
     * 返回当前安全上下文。
     * </p>
     * @return 当前安全上下文；未设置时为空
     */
    public static Optional<CocoSecurityContext> current() {
        return CocoSecurityContextHolder.current();
    }

    /**
     * <p>
     * 返回当前安全主体。
     * </p>
     * @return 当前安全主体；未设置安全上下文时为空
     */
    public static Optional<CocoSecurityPrincipal> principal() {
        return current().map(CocoSecurityContext::principal);
    }

    /**
     * <p>
     * 判断当前上下文是否已认证。
     * </p>
     * @return 已认证时返回 {@code true}
     */
    public static boolean isAuthenticated() {
        return current().map(CocoSecurityContext::authenticated).orElse(false);
    }

    /**
     * <p>
     * 返回当前安全上下文，不存在时抛出未认证异常。
     * </p>
     * @return 当前安全上下文
     */
    public static CocoSecurityContext requireCurrent() {
        return CocoSecurityContextHolder.requireCurrent();
    }

    /**
     * <p>
     * 返回已认证的当前安全上下文。
     * </p>
     * @return 已认证安全上下文
     */
    public static CocoSecurityContext requireAuthenticated() {
        CocoSecurityContext securityContext = requireCurrent();
        if (!securityContext.authenticated()) {
            throw CocoSecurityErrorCode.UNAUTHENTICATED.unauthorized();
        }
        return securityContext;
    }

    /**
     * <p>
     * 返回已认证的当前安全主体。
     * </p>
     * @return 已认证安全主体
     */
    public static CocoSecurityPrincipal requirePrincipal() {
        return requireAuthenticated().principal();
    }

    /**
     * <p>
     * 判断当前已认证主体是否具有指定角色。
     * </p>
     * @param role 角色编码
     * @return 具有该角色时返回 {@code true}
     */
    public static boolean hasRole(String role) {
        String checkedRole = normalize(role);
        return checkedRole != null && current()
                .filter(CocoSecurityContext::authenticated)
                .map(CocoSecurityContext::principal)
                .map(principal -> principal.hasRole(checkedRole))
                .orElse(false);
    }

    /**
     * <p>
     * 要求当前已认证主体具有指定角色。
     * </p>
     * @param role 角色编码
     * @return 当前安全主体
     */
    public static CocoSecurityPrincipal requireRole(String role) {
        String checkedRole = requireText(role, "role");
        CocoSecurityPrincipal principal = requirePrincipal();
        if (!principal.hasRole(checkedRole)) {
            throw CocoSecurityErrorCode.ACCESS_DENIED.forbidden();
        }
        return principal;
    }

    /**
     * <p>
     * 判断当前已认证主体是否具有指定权限。
     * </p>
     * @param permission 权限编码
     * @return 具有该权限时返回 {@code true}
     */
    public static boolean hasPermission(String permission) {
        String checkedPermission = normalize(permission);
        return checkedPermission != null && current()
                .filter(CocoSecurityContext::authenticated)
                .map(CocoSecurityContext::principal)
                .map(principal -> principal.hasPermission(checkedPermission))
                .orElse(false);
    }

    /**
     * <p>
     * 要求当前已认证主体具有指定权限。
     * </p>
     * @param permission 权限编码
     * @return 当前安全主体
     */
    public static CocoSecurityPrincipal requirePermission(String permission) {
        String checkedPermission = requireText(permission, "permission");
        CocoSecurityPrincipal principal = requirePrincipal();
        if (!principal.hasPermission(checkedPermission)) {
            throw CocoSecurityErrorCode.ACCESS_DENIED.forbidden();
        }
        return principal;
    }

    private static String requireText(String value, String name) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
