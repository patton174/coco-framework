package io.github.coco.sample.basic.interfaces.rest;

import io.github.coco.sample.basic.domain.order.SampleOrder;

/**
 * Coco 示例订单响应。
 * <p>
 * 将业务订单快照转换为对外接口响应。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-sample-basic}</li>
 * </ul>
 * @param orderId 订单编号
 * @param buyerName 买家名称
 * @param sku 商品编码
 * @param productName 商品名称
 * @param quantity 下单数量
 * @param totalAmount 订单金额，单位为分
 * @param status 订单状态
 * @param remainingStock 下单后的剩余库存
 * @author patton174
 * @since 1.0.0
 */
public record SampleOrderResponse(String orderId, String buyerName, String sku, String productName,
        int quantity, long totalAmount, String status, int remainingStock) {

    /**
     * <p>
     * 从业务订单快照创建接口响应。
     * </p>
     * @param order 业务订单快照
     * @return 订单接口响应
     */
    public static SampleOrderResponse from(SampleOrder order) {
        return new SampleOrderResponse(order.orderId(), order.buyerName(), order.sku(), order.productName(),
                order.quantity(), order.totalAmount(), order.status(), order.remainingStock());
    }
}
