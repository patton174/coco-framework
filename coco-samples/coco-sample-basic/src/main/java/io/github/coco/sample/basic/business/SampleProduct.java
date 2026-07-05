package io.github.coco.sample.basic.business;

/**
 * Coco 示例商品快照。
 * <p>
 * 表示业务接口可以读取到的商品库存状态。
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
public record SampleProduct(String sku, String name, long unitPrice, int availableStock) {
}
