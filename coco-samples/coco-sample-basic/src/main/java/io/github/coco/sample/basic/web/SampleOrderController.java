package io.github.coco.sample.basic.web;

import java.util.List;
import java.util.Objects;

import io.github.coco.sample.basic.business.SampleOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Coco 示例订单接口。
 * <p>
 * 通过商品查询、创建订单和订单查询模拟真实业务项目接入 Coco Starter 后的 Web 调用链路。
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
@RestController
@RequestMapping("/sample")
public class SampleOrderController {

    private final SampleOrderService orderService;

    /**
     * <p>
     * 创建 Coco 示例订单接口。
     * </p>
     * @param orderService 示例订单服务
     */
    public SampleOrderController(SampleOrderService orderService) {
        this.orderService = Objects.requireNonNull(orderService, "orderService must not be null");
    }

    /**
     * <p>
     * 查询商品库存。
     * </p>
     * @return 商品库存响应列表
     */
    @GetMapping("/products")
    public List<SampleProductResponse> products() {
        return this.orderService.listProducts().stream()
                .map(SampleProductResponse::from)
                .toList();
    }

    /**
     * <p>
     * 创建订单。
     * </p>
     * @param request 创建订单请求
     * @return 已创建订单响应
     */
    @PostMapping("/orders")
    public SampleOrderResponse createOrder(@RequestBody SampleCreateOrderRequest request) {
        return SampleOrderResponse.from(this.orderService.createOrder(request.buyerName(), request.sku(),
                request.quantity()));
    }

    /**
     * <p>
     * 查询订单详情。
     * </p>
     * @param orderId 订单编号
     * @return 订单详情响应
     */
    @GetMapping("/orders/{orderId}")
    public SampleOrderResponse order(@PathVariable String orderId) {
        return SampleOrderResponse.from(this.orderService.getOrder(orderId));
    }
}
