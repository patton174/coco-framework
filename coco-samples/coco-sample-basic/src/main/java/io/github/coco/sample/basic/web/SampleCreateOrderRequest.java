package io.github.coco.sample.basic.web;

/**
 * Coco 示例创建订单请求。
 * <p>
 * 表示真实业务接口接收的下单参数。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-sample-basic}</li>
 * </ul>
 * @param buyerName 买家名称
 * @param sku 商品编码
 * @param quantity 下单数量
 * @author patton174
 * @since 1.0.0
 */
public record SampleCreateOrderRequest(String buyerName, String sku, int quantity) {
}
