package io.github.coco.sample.full.interfaces.rest;

import io.github.coco.sample.full.application.order.SampleOrderView;

/**
 * 示例订单响应。
 *
 * @param id 订单标识
 * @param tenantId 租户标识
 * @param ownerId 数据所有者标识
 * @param orderNo 订单号
 * @param amount 金额，单位为分
 * @author patton174
 * @since 1.0.0
 */
public record SampleOrderResponse(Long id, String tenantId, String ownerId, String orderNo, Long amount) {

    public static SampleOrderResponse from(SampleOrderView view) {
        return new SampleOrderResponse(
                view.id(),
                view.tenantId(),
                view.ownerId(),
                view.orderNo(),
                view.amount());
    }
}
