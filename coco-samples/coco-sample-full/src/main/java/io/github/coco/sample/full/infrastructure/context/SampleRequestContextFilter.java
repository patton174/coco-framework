package io.github.coco.sample.full.infrastructure.context;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import io.github.coco.feature.datapermission.context.CocoDataPermissionContext;
import io.github.coco.feature.datapermission.context.CocoDataPermissionContextHolder;
import io.github.coco.feature.datapermission.context.CocoDataPermissionRule;
import io.github.coco.feature.datapermission.context.CocoDataScope;
import io.github.coco.feature.security.CocoSecurity;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import io.github.coco.feature.tenant.context.CocoTenantContext;
import io.github.coco.feature.tenant.context.CocoTenantContextHolder;
import io.github.coco.sample.full.application.order.SampleOrderAccessPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 将示例请求中的租户标识和已认证主体映射为 Coco 数据上下文。
 * <p>
 * 该过滤器代表业务系统自己的入口适配层，不属于 Coco 的认证模型。安全主体仍由 Coco
 * trusted-header bridge 建立，过滤器只负责业务租户和数据范围映射。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class SampleRequestContextFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Coco-Tenant-Id";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/full/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Optional<CocoTenantContext> previousTenant = CocoTenantContextHolder.current();
        Optional<CocoDataPermissionContext> previousDataPermission = CocoDataPermissionContextHolder.current();
        String tenantId = normalize(request.getHeader(TENANT_HEADER));
        Optional<CocoSecurityPrincipal> principal = CocoSecurity.current()
                .filter(context -> context.authenticated())
                .map(context -> context.principal());

        bindTenantContext(tenantId);
        bindDataPermissionContext(principal);
        try {
            filterChain.doFilter(request, response);
        }
        finally {
            restoreDataPermissionContext(previousDataPermission);
            restoreTenantContext(previousTenant);
        }
    }

    private static void bindTenantContext(String tenantId) {
        if (tenantId == null) {
            CocoTenantContextHolder.clear();
            return;
        }
        CocoTenantContextHolder.set(CocoTenantContext.of(tenantId, tenantId));
    }

    private static void bindDataPermissionContext(Optional<CocoSecurityPrincipal> principal) {
        if (principal.isEmpty()) {
            CocoDataPermissionContextHolder.clear();
            return;
        }
        CocoDataPermissionRule rule = new CocoDataPermissionRule(
                SampleOrderAccessPolicy.RESOURCE,
                CocoDataScope.SELF,
                Set.of(principal.get().principalId()));
        CocoDataPermissionContextHolder.set(CocoDataPermissionContext.of(Set.of(rule)));
    }

    private static void restoreTenantContext(Optional<CocoTenantContext> previous) {
        previous.ifPresentOrElse(CocoTenantContextHolder::set, CocoTenantContextHolder::clear);
    }

    private static void restoreDataPermissionContext(Optional<CocoDataPermissionContext> previous) {
        previous.ifPresentOrElse(CocoDataPermissionContextHolder::set, CocoDataPermissionContextHolder::clear);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
