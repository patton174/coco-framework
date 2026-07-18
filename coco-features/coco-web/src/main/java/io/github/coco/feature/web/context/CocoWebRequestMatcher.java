package io.github.coco.feature.web.context;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Coco Web 请求匹配器。
 * <p>
 * 统一执行请求路径、HTTP 方法和规则列表匹配，供签名、加密、防重放等过滤器复用。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public interface CocoWebRequestMatcher {

    /**
     * <p>
     * 判断请求是否命中任意匹配规则。
     * </p>
     * @param request HTTP 请求
     * @param rules 匹配规则列表
     * @return 命中任意规则时返回 {@code true}
     */
    boolean matches(HttpServletRequest request, List<CocoWebRequestMatchRule> rules);
}
