package io.github.coco.web.request.metadata;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Coco Web 请求安全输入解析器�? * <p>
 * 定义 Sign、AES 加密、防重放等安全能力共用的请求输入生成契约，业务项目可以提供同类型 Bean 覆盖默认策略�? * </p>
 * <p>
 * 项目信息�? * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库�?a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoWebRequestSecurityInputResolver {

    /**
     * <p>
     * 解析当前请求的安全输入�?     * </p>
     * @param request 当前 Servlet 请求
     * @param method 已归一化前�?HTTP 方法
     * @param path 已归一化前的请求路�?     * @return 请求安全输入
     */
    CocoWebRequestSecurityInput resolve(HttpServletRequest request, String method, String path);
}
