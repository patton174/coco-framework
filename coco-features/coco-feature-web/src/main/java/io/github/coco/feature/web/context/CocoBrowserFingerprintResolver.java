package io.github.coco.feature.web.context;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Coco 浏览器指纹解析器。
 * <p>
 * 框架只定义浏览器指纹的解析契约，默认实现基于请求头信号生成服务端摘要；业务项目可以提供同类型 Bean 覆盖默认策略。
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
public interface CocoBrowserFingerprintResolver {

    /**
     * <p>
     * 从当前 Servlet 请求解析浏览器指纹。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 浏览器指纹
     */
    CocoBrowserFingerprint resolve(HttpServletRequest request);
}
