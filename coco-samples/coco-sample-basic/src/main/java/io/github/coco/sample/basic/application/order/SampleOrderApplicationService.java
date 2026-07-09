package io.github.coco.sample.basic.application.order;

import java.util.List;
import java.util.Objects;

import io.github.coco.common.exception.CocoCommonErrorCode;
import io.github.coco.sample.basic.domain.order.SampleBusinessErrorCode;
import io.github.coco.sample.basic.domain.order.SampleOrder;
import io.github.coco.sample.basic.domain.order.SampleOrderRepository;
import io.github.coco.sample.basic.domain.order.SampleProduct;
import io.github.coco.sample.basic.domain.order.SampleProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coco 示例订单应用服务。
 * <p>
 * 承载商品查询、下单和订单查询等应用用例，模拟真实业务项目中 application 层对 Coco 异常能力的使用方式。
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
public class SampleOrderApplicationService {

    private final SampleOrderRepository orderRepository;

    private final SampleProductRepository productRepository;

    /**
     * <p>
     * 创建示例订单服务。
     * </p>
     * @param orderRepository 示例订单仓储
     * @param productRepository 示例商品仓储
     */
    public SampleOrderApplicationService(SampleOrderRepository orderRepository,
            SampleProductRepository productRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.productRepository = Objects.requireNonNull(productRepository, "productRepository must not be null");
    }

    /**
     * <p>
     * 查询全部商品库存。
     * </p>
     * @return 商品库存列表
     */
    @Transactional(readOnly = true)
    public List<SampleProduct> listProducts() {
        return this.productRepository.findProducts();
    }

    /**
     * <p>
     * 创建订单。
     * </p>
     * <p>
     * 示例通过应用服务声明事务边界。内存实现仅用于演示，真实数据库实现应依赖数据库事务保证库存扣减与订单创建一致。
     * </p>
     * @param buyerName 买家名称
     * @param sku 商品编码
     * @param quantity 下单数量
     * @return 已创建订单
     */
    @Transactional
    public SampleOrder createOrder(String buyerName, String sku, int quantity) {
        String checkedBuyerName = requireText(buyerName, "buyerName");
        String checkedSku = requireText(sku, "sku");
        if (quantity <= 0) {
            throw SampleBusinessErrorCode.INVALID_ORDER_QUANTITY.request("quantity");
        }
        SampleProduct product = this.productRepository.findProduct(checkedSku)
                .orElseThrow(() -> SampleBusinessErrorCode.PRODUCT_NOT_FOUND.notFound(checkedSku));
        if (product.availableStock() < quantity) {
            throw SampleBusinessErrorCode.INSUFFICIENT_STOCK.conflict(checkedSku, product.availableStock(), quantity);
        }
        SampleProduct reservedProduct = this.productRepository.decreaseStock(checkedSku, quantity)
                .orElseThrow(() -> SampleBusinessErrorCode.INSUFFICIENT_STOCK.conflict(checkedSku,
                        product.availableStock(), quantity));
        return this.orderRepository.createOrder(checkedBuyerName, reservedProduct, quantity);
    }

    /**
     * <p>
     * 查询订单详情。
     * </p>
     * @param orderId 订单编号
     * @return 订单详情
     */
    @Transactional(readOnly = true)
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
