package io.github.coco.feature.web.context;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Coco 请求 Cookie 解析器。
 * <p>
 * 定义从 Servlet 请求中采集 Cookie 快照的扩展契约，供请求上下文、浏览器指纹和后续安全能力复用。
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
public interface CocoRequestCookieResolver {

    /**
     * <p>
     * 解析允许写入请求上下文的 Cookie。
     * </p>
     * @param request 当前 Servlet 请求
     * @return Cookie 快照
     */
    Map<String, String> resolveIncludedCookies(HttpServletRequest request);
}
