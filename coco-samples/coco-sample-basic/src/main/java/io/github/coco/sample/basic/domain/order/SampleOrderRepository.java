package io.github.coco.sample.basic.domain.order;

import java.util.Optional;

/**
 * Coco 示例订单仓储契约。
 * <p>
 * 领域层只声明订单持久化能力，商品库存由 {@link SampleProductRepository} 单独建模。
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
public interface SampleOrderRepository {

    /**
     * <p>
     * 创建订单。
     * </p>
     * @param buyerName 买家名称
     * @param product 已完成库存扣减后的商品快照
     * @param quantity 下单数量
     * @return 已创建订单
     */
    SampleOrder createOrder(String buyerName, SampleProduct product, int quantity);

    /**
     * <p>
     * 根据订单编号查询订单。
     * </p>
     * @param orderId 订单编号
     * @return 订单快照；不存在时为空
     */
    Optional<SampleOrder> findOrder(String orderId);
}
