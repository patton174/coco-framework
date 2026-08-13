package io.github.coco.feature.security.authorization;

import io.github.coco.feature.security.CocoSecurityErrorCode;
import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityContextResolver;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;

/**
 * 基于 {@link CocoSecurityContextResolver} 的默认方法授权决策器。
 * <p>
 * 缺少上下文或未经认证时沿用 Coco 的未授权错误语义；角色或权限不满足时沿用拒绝访问错误语义。
 * </p>
 * @author patton174
 * @since 1.0.0
 */
public final class DefaultCocoMethodAuthorizationManager implements CocoMethodAuthorizationManager {

    /**
     * {@inheritDoc}
     */
    @Override
    public void authorize(CocoAuthorizationRequirement requirement, CocoSecurityContextResolver contextResolver) {
        CocoSecurityContext context = contextResolver.resolve()
                .orElseThrow(() -> CocoSecurityErrorCode.CONTEXT_MISSING.unauthorized());
        if (!context.authenticated()) {
            throw CocoSecurityErrorCode.UNAUTHENTICATED.unauthorized();
        }
        CocoSecurityPrincipal principal = context.principal();
        if (!matchesRoles(requirement, principal) || !matchesPermissions(requirement, principal)) {
            throw CocoSecurityErrorCode.ACCESS_DENIED.forbidden();
        }
    }

    private static boolean matchesRoles(CocoAuthorizationRequirement requirement, CocoSecurityPrincipal principal) {
        return matches(requirement.roles(), requirement.roleMode(), principal::hasRole);
    }

    private static boolean matchesPermissions(CocoAuthorizationRequirement requirement, CocoSecurityPrincipal principal) {
        return matches(requirement.permissions(), requirement.permissionMode(), principal::hasPermission);
    }

    private static boolean matches(java.util.List<String> values, CocoAuthorizationMode mode,
            java.util.function.Predicate<String> checker) {
        if (values.isEmpty()) {
            return true;
        }
        return mode == CocoAuthorizationMode.ALL ? values.stream().allMatch(checker) : values.stream().anyMatch(checker);
    }
}
