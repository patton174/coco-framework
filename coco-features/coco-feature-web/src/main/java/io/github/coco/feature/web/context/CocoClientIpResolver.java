package io.github.coco.feature.web.context;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Coco 客户端 IP 解析器。
 * <p>
 * 框架定义客户端 IP 解析契约，默认实现支持常见代理请求头；业务项目可以提供同类型 Bean 适配自己的网关或负载均衡规则。
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
@FunctionalInterface
public interface CocoClientIpResolver {

    /**
     * <p>
     * 从当前 Servlet 请求解析客户端 IP。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 客户端 IP；无法解析时为空
     */
    String resolve(HttpServletRequest request);

    /**
     * <p>
     * 从当前 Servlet 请求解析客户端 IP 及来源信息。
     * </p>
     * <p>
     * 默认实现兼容旧的字符串解析器，将非空解析结果标记为业务自定义来源。需要暴露可信代理、来源请求头等细节时可以覆盖该方法。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 客户端 IP 解析结果
     */
    default CocoClientIpResolution resolveResolution(HttpServletRequest request) {
        return CocoClientIpResolution.custom(resolve(request));
    }
}
