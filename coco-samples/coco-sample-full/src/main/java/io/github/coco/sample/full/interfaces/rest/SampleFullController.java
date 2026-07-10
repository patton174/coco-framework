package io.github.coco.sample.full.interfaces.rest;

import java.util.List;
import java.util.Map;

import io.github.coco.feature.security.CocoSecurity;
import io.github.coco.sample.full.application.order.SampleOrderQueryService;
import io.github.coco.sample.full.infrastructure.audit.InMemorySampleAuditRecorder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Coco 完整能力示例接口。
 *
 * @author patton174
 * @since 1.0.0
 */
@RestController
@RequestMapping("/full")
public class SampleFullController {

    private final SampleOrderQueryService orderQueryService;

    private final InMemorySampleAuditRecorder auditRecorder;

    public SampleFullController(SampleOrderQueryService orderQueryService,
            InMemorySampleAuditRecorder auditRecorder) {
        this.orderQueryService = orderQueryService;
        this.auditRecorder = auditRecorder;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "sample", "full");
    }

    @GetMapping("/orders")
    public List<SampleOrderResponse> orders() {
        return this.orderQueryService.listVisibleOrders().stream()
                .map(SampleOrderResponse::from)
                .toList();
    }

    @GetMapping("/audits")
    public List<SampleAuditResponse> audits() {
        CocoSecurity.requireRole("AUDIT_READER");
        return this.auditRecorder.events().stream()
                .map(SampleAuditResponse::from)
                .toList();
    }
}
