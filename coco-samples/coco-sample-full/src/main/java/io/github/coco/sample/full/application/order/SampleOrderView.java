package io.github.coco.sample.full.application.order;

/**
 * 示例订单查询视图。
 *
 * @param id 订单标识
 * @param tenantId 租户标识
 * @param ownerId 数据所有者标识
 * @param orderNo 订单号
 * @param amount 金额，单位为分
 * @author patton174
 * @since 1.0.0
 */
public record SampleOrderView(Long id, String tenantId, String ownerId, String orderNo, Long amount) {
}
