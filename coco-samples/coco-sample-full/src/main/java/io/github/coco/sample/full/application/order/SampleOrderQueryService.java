package io.github.coco.sample.full.application.order;

import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.coco.common.trace.CocoTraceContext;
import io.github.coco.feature.audit.core.CocoAuditEvent;
import io.github.coco.feature.audit.core.CocoAuditPublisher;
import io.github.coco.feature.datapermission.context.CocoDataPermissionContextHolder;
import io.github.coco.feature.security.CocoSecurity;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import io.github.coco.feature.tenant.context.CocoTenantContext;
import io.github.coco.feature.tenant.context.CocoTenantContextHolder;
import io.github.coco.sample.full.infrastructure.order.SampleOrderEntity;
import io.github.coco.sample.full.infrastructure.order.SampleOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 示例订单查询服务。
 *
 * @author patton174
 * @since 1.0.0
 */
@Service
public class SampleOrderQueryService {

    private final SampleOrderMapper orderMapper;

    private final CocoAuditPublisher auditPublisher;

    public SampleOrderQueryService(SampleOrderMapper orderMapper, CocoAuditPublisher auditPublisher) {
        this.orderMapper = orderMapper;
        this.auditPublisher = auditPublisher;
    }

    @Transactional(readOnly = true)
    public List<SampleOrderView> listVisibleOrders() {
        CocoSecurityPrincipal principal = CocoSecurity.requireRole(SampleOrderAccessPolicy.READER_ROLE);
        CocoTenantContext tenant = CocoTenantContextHolder.requireCurrent();
        CocoDataPermissionContextHolder.requireCurrent();

        List<SampleOrderView> orders = this.orderMapper.selectList(
                        Wrappers.<SampleOrderEntity>lambdaQuery().orderByAsc(SampleOrderEntity::getId))
                .stream()
                .map(SampleOrderQueryService::toView)
                .toList();

        this.auditPublisher.publish(CocoAuditEvent.builder("sample.order.query")
                .action("LIST")
                .resourceType(SampleOrderAccessPolicy.RESOURCE)
                .resourceId("visible")
                .traceId(CocoTraceContext.currentTraceId().orElse(null))
                .actor(principal.principalId())
                .tenantId(tenant.tenantId())
                .attribute("resultCount", orders.size())
                .build());
        return orders;
    }

    private static SampleOrderView toView(SampleOrderEntity entity) {
        return new SampleOrderView(
                entity.getId(),
                entity.getTenantId(),
                entity.getOwnerId(),
                entity.getOrderNo(),
                entity.getAmount());
    }
}
