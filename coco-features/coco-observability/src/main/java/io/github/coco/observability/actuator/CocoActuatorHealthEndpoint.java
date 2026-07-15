package io.github.coco.observability.actuator;

import io.github.coco.observability.CocoObservabilityStatusProvider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * Coco 可观测性健康状态端点。
 */
@Endpoint(id = "cocoobservability")
public final class CocoActuatorHealthEndpoint {

    private final CocoObservabilityStatusProvider statusProvider;

    public CocoActuatorHealthEndpoint(CocoObservabilityStatusProvider statusProvider) {
        this.statusProvider = Objects.requireNonNull(statusProvider, "statusProvider must not be null");
    }

    /**
     * 返回安全的启动和功能计划状态。
     * @return 健康状态
     */
    @ReadOperation
    public Map<String, Object> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.putAll(this.statusProvider.status());
        return Map.copyOf(response);
    }
}
