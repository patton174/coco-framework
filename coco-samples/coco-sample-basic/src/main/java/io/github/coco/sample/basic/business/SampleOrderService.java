package io.github.coco.sample.basic.business;

import java.util.List;
import java.util.Objects;

import io.github.coco.common.exception.CocoCommonErrorCode;
import org.springframework.stereotype.Service;

/**
 * Coco 示例订单服务。
 * <p>
 * 承载商品查询、下单和订单查询等业务规则，模拟真实业务项目中 service 层对 Coco 异常能力的使用方式。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-sample-basic}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@Service
public class SampleOrderService {

    private final SampleOrderRepository orderRepository;

    /**
     * <p>
     * 创建示例订单服务。
     * </p>
     * @param orderRepository 示例订单仓储
     */
    public SampleOrderService(SampleOrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
    }

    /**
     * <p>
     * 查询全部商品库存。
     * </p>
     * @return 商品库存列表
     */
    public List<SampleProduct> listProducts() {
        return this.orderRepository.findProducts();
    }

    /**
     * <p>
     * 创建订单。
     * </p>
     * @param buyerName 买家名称
     * @param sku 商品编码
     * @param quantity 下单数量
     * @return 已创建订单
     */
    public SampleOrder createOrder(String buyerName, String sku, int quantity) {
        String checkedBuyerName = requireText(buyerName, "buyerName");
        String checkedSku = requireText(sku, "sku");
        if (quantity <= 0) {
            throw SampleBusinessErrorCode.INVALID_ORDER_QUANTITY.request("quantity");
        }
        return this.orderRepository.createOrder(checkedBuyerName, checkedSku, quantity);
    }

    /**
     * <p>
     * 查询订单详情。
     * </p>
     * @param orderId 订单编号
     * @return 订单详情
     */
    public SampleOrder getOrder(String orderId) {
        String checkedOrderId = requireText(orderId, "orderId");
        return this.orderRepository.findOrder(checkedOrderId)
                .orElseThrow(() -> SampleBusinessErrorCode.ORDER_NOT_FOUND.notFound(checkedOrderId));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw CocoCommonErrorCode.INVALID_ARGUMENT.request(fieldName);
        }
        return value.trim();
    }
}
