package io.github.coco.feature.web.context.target;

import jakarta.servlet.http.HttpServletRequest;

/**
 * <p>
 * Coco Web 请求目标解析器。
 * </p>
 * <p>
 * 根据 Servlet 请求和可信代理转发头解析外部可见的请求目标。
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
public interface CocoWebRequestTargetResolver {

    /**
     * <p>
     * 解析当前请求的外部目标地址。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 外部可见的请求目标
     */
    CocoWebRequestTarget resolve(HttpServletRequest request);

    /**
     * <p>
     * 解析当前请求的外部目标地址及其来源信息。
     * </p>
     * <p>
     * 默认实现基于 {@link #resolve(HttpServletRequest)} 构造自定义解析结果。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 外部可见的请求目标解析结果
     */
    default CocoWebRequestTargetResolution resolveResolution(HttpServletRequest request) {
        return CocoWebRequestTargetResolution.custom(resolve(request));
    }
}
