package io.github.coco.observability;

import java.util.Map;

/**
 * 功能计划安全状态贡献 SPI。
 */
@FunctionalInterface
public interface CocoFeaturePlanStatusContributor {

    /**
     * 返回只包含安全、有界信息的功能计划摘要。
     * @return 功能计划状态
     */
    Map<String, Object> contribute();
}
