package io.github.coco.sample.full;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import io.github.coco.feature.datapermission.context.CocoDataPermissionContext;
import io.github.coco.feature.datapermission.context.CocoDataPermissionContextHolder;
import io.github.coco.feature.datapermission.context.CocoDataPermissionRule;
import io.github.coco.feature.datapermission.context.CocoDataScope;
import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityContextHolder;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import io.github.coco.feature.tenant.context.CocoTenantContext;
import io.github.coco.feature.tenant.context.CocoTenantContextHolder;
import io.github.coco.sample.full.application.order.SampleOrderAccessPolicy;
import io.github.coco.sample.full.application.order.SampleOrderQueryService;
import io.github.coco.sample.full.infrastructure.audit.InMemorySampleAuditRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CocoSampleFullApplicationTest {

    @Autowired
    private SampleOrderQueryService orderQueryService;

    @Autowired
    private InMemorySampleAuditRecorder auditRecorder;

    @BeforeEach
    void clearAuditEvents() {
        this.auditRecorder.clear();
    }

    @Test
    void appliesSecurityTenantDataPermissionAndAuditTogether() {
        CocoSecurityPrincipal principal = new CocoSecurityPrincipal(
                "user-a", "User A", Set.of("ORDER_READER"), Set.of(), Map.of());
        CocoSecurityContext securityContext = CocoSecurityContext.authenticated(principal);
        CocoTenantContext tenantContext = CocoTenantContext.of("tenant-a", "Tenant A");
        CocoDataPermissionContext dataPermissionContext = CocoDataPermissionContext.of(Set.of(
                new CocoDataPermissionRule(
                        SampleOrderAccessPolicy.RESOURCE,
                        CocoDataScope.SELF,
                        Set.of("user-a"))));

        var orders = CocoSecurityContextHolder.callWithContext(securityContext,
                () -> CocoTenantContextHolder.callWithContext(tenantContext,
                        () -> CocoDataPermissionContextHolder.callWithContext(dataPermissionContext,
                                this.orderQueryService::listVisibleOrders)));

        assertThat(orders).singleElement().satisfies(order -> {
            assertThat(order.orderNo()).isEqualTo("A-100");
            assertThat(order.tenantId()).isEqualTo("tenant-a");
            assertThat(order.ownerId()).isEqualTo("user-a");
        });
        assertThat(this.auditRecorder.events()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo("sample.order.query");
            assertThat(event.actor()).contains("user-a");
            assertThat(event.tenantId()).contains("tenant-a");
            assertThat(event.attributes()).containsEntry("resultCount", 1);
        });
        assertThat(CocoSecurityContextHolder.current()).isEmpty();
        assertThat(CocoTenantContextHolder.current()).isEmpty();
        assertThat(CocoDataPermissionContextHolder.current()).isEmpty();
    }
}
