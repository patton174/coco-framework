package io.github.coco.sample.basic.domain.order;

import java.util.List;
import java.util.Optional;

/**
 * Coco 示例商品仓储契约。
 * <p>
 * 领域层以独立仓储声明商品库存读取与扣减能力，避免订单仓储同时承担商品查询职责。
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
public interface SampleProductRepository {

    /**
     * <p>
     * 查询全部商品库存快照。
     * </p>
     * @return 商品库存快照列表
     */
    List<SampleProduct> findProducts();

    /**
     * <p>
     * 根据商品编码查询商品库存快照。
     * </p>
     * @param sku 商品编码
     * @return 商品库存快照；不存在时为空
     */
    Optional<SampleProduct> findProduct(String sku);

    /**
     * <p>
     * 扣减商品库存，并返回扣减后的商品快照。
     * </p>
     * @param sku 商品编码
     * @param quantity 扣减数量
     * @return 扣减后的商品库存快照；商品不存在或库存不足时为空
     */
    Optional<SampleProduct> decreaseStock(String sku, int quantity);
}
