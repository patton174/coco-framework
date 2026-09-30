package io.github.coco.feature.web.body;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Coco 请求体解析器。
 * <p>
 * 为框架内部安全、日志和上下文能力提供统一请求体解析入口，业务项目可以提供同类型 Bean 覆盖默认策略。
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
public interface CocoRequestBodyResolver {

    /**
     * <p>
     * 解析当前 Servlet 请求体。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 已解析请求体
     */
    CocoResolvedRequestBody resolve(HttpServletRequest request);
}
