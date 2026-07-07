package io.github.coco.sample.basic.infrastructure.order;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.coco.sample.basic.domain.order.SampleBusinessErrorCode;
import io.github.coco.sample.basic.domain.order.SampleOrder;
import io.github.coco.sample.basic.domain.order.SampleOrderRepository;
import io.github.coco.sample.basic.domain.order.SampleProduct;
import org.springframework.stereotype.Repository;

/**
 * Coco 示例内存订单仓储实现。
 * <p>
 * 使用内存数据模拟商品库存和订单状态，保持示例可直接运行；真实业务项目可以在 infrastructure 层替换为数据库 Mapper 或 Repository。
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
@Repository
public class InMemorySampleOrderRepository implements SampleOrderRepository {

    private final Map<String, ProductState> products = new LinkedHashMap<>();

    private final Map<String, SampleOrder> orders = new LinkedHashMap<>();

    private long orderSequence = 1000L;

    /**
     * <p>
     * 创建示例订单仓储，并初始化可售商品。
     * </p>
     */
    public InMemorySampleOrderRepository() {
        this.products.put("COCO-STARTER", new ProductState("COCO-STARTER", "Coco Starter", 9900L, 5));
        this.products.put("COCO-AUDIT", new ProductState("COCO-AUDIT", "Coco Audit", 12900L, 2));
        this.products.put("COCO-SIGNATURE", new ProductState("COCO-SIGNATURE", "Coco Signature", 11900L, 20));
        this.products.put("COCO-REPLAY", new ProductState("COCO-REPLAY", "Coco Replay", 10900L, 20));
        this.products.put("COCO-ENCRYPTION", new ProductState("COCO-ENCRYPTION", "Coco Encryption", 13900L, 20));
    }

    /**
     * <p>
     * 查询全部商品库存快照。
     * </p>
     * @return 商品库存快照列表
     */
    @Override
    public synchronized List<SampleProduct> findProducts() {
        return this.products.values().stream()
                .map(ProductState::snapshot)
                .toList();
    }

    /**
     * <p>
     * 创建订单并扣减库存。
     * </p>
     * @param buyerName 买家名称
     * @param sku 商品编码
     * @param quantity 下单数量
     * @return 已创建订单
     */
    @Override
    public synchronized SampleOrder createOrder(String buyerName, String sku, int quantity) {
        ProductState product = this.products.get(sku);
        if (product == null) {
            throw SampleBusinessErrorCode.PRODUCT_NOT_FOUND.notFound(sku);
        }
        if (product.availableStock < quantity) {
            throw SampleBusinessErrorCode.INSUFFICIENT_STOCK.conflict(sku, product.availableStock, quantity);
        }
        product.availableStock = product.availableStock - quantity;
        String orderId = "ORD-" + ++this.orderSequence;
        SampleOrder order = new SampleOrder(orderId, buyerName, product.sku, product.name, quantity,
                product.unitPrice * quantity, "CREATED", product.availableStock);
        this.orders.put(orderId, order);
        return order;
    }

    /**
     * <p>
     * 根据订单编号查询订单。
     * </p>
     * @param orderId 订单编号
     * @return 订单快照；不存在时为空
     */
    @Override
    public synchronized Optional<SampleOrder> findOrder(String orderId) {
        return Optional.ofNullable(this.orders.get(orderId));
    }

    private static final class ProductState {

        private final String sku;

        private final String name;

        private final long unitPrice;

        private int availableStock;

        private ProductState(String sku, String name, long unitPrice, int availableStock) {
            this.sku = sku;
            this.name = name;
            this.unitPrice = unitPrice;
            this.availableStock = availableStock;
        }

        private SampleProduct snapshot() {
            return new SampleProduct(this.sku, this.name, this.unitPrice, this.availableStock);
        }
    }
}
