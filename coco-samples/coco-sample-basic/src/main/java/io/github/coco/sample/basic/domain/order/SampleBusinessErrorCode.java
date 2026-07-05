package io.github.coco.sample.basic.domain.order;

import io.github.coco.common.exception.CocoBusinessCode;

/**
 * Coco 示例业务错误码。
 * <p>
 * 业务项目可以定义自己的错误码枚举，并直接复用 Coco 统一异常、统一响应和国际化消息解析能力。
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
public enum SampleBusinessErrorCode implements CocoBusinessCode {

    /**
     * <p>
     * 商品不存在。
     * </p>
     */
    PRODUCT_NOT_FOUND(1001, "sample.product.not-found"),

    /**
     * <p>
     * 订单不存在。
     * </p>
     */
    ORDER_NOT_FOUND(1002, "sample.order.not-found"),

    /**
     * <p>
     * 下单数量不合法。
     * </p>
     */
    INVALID_ORDER_QUANTITY(1003, "sample.order.invalid-quantity"),

    /**
     * <p>
     * 商品库存不足。
     * </p>
     */
    INSUFFICIENT_STOCK(1004, "sample.order.insufficient-stock");

    private final int code;

    private final String messageCode;

    SampleBusinessErrorCode(int code, String messageCode) {
        this.code = code;
        this.messageCode = messageCode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int code() {
        return this.code;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String messageCode() {
        return this.messageCode;
    }
}
