package io.github.coco.observability;

import java.util.Map;

/**
 * Actuator health 和 info 的安全状态提供 SPI。
 */
@FunctionalInterface
public interface CocoObservabilityStatusProvider {

    /**
     * 返回启动和功能计划的安全状态摘要。
     * @return 状态摘要
     */
    Map<String, Object> status();
}
