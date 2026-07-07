package io.github.coco.feature.web.response;

/**
 * Coco Web 响应体工厂。
 * <p>
 * 将框架解析出的响应语义负载转换为最终写出的响应体对象。业务项目可以注册自定义 Bean 替换默认实现，从而适配已有
 * 响应协议。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public interface CocoResponseBodyFactory {

    /**
     * <p>
     * 创建成功响应体。
     * </p>
     * @param payload 成功响应语义负载
     * @return 最终写出的响应体对象
     */
    Object success(CocoResponsePayload<?> payload);

    /**
     * <p>
     * 创建异常响应体。
     * </p>
     * @param payload 异常响应语义负载
     * @return 最终写出的响应体对象
     */
    Object error(CocoResponsePayload<?> payload);
}
