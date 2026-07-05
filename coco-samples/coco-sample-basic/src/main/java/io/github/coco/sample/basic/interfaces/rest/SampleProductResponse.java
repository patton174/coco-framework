package io.github.coco.sample.basic.interfaces.rest;

import io.github.coco.sample.basic.domain.order.SampleProduct;

/**
 * Coco 示例商品响应。
 * <p>
 * 将业务商品快照转换为对外接口响应。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-sample-basic}</li>
 * </ul>
 * @param sku 商品编码
 * @param name 商品名称
 * @param unitPrice 商品单价，单位为分
 * @param availableStock 可售库存
 * @author patton174
 * @since 1.0.0
 */
public record SampleProductResponse(String sku, String name, long unitPrice, int availableStock) {

    /**
     * <p>
     * 从业务商品快照创建接口响应。
     * </p>
     * @param product 业务商品快照
     * @return 商品接口响应
     */
    public static SampleProductResponse from(SampleProduct product) {
        return new SampleProductResponse(product.sku(), product.name(), product.unitPrice(),
                product.availableStock());
    }
}
