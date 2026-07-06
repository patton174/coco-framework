package io.github.coco.feature.web.context;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Coco Web 请求上下文解析器。
 * <p>
 * 将 Servlet 请求解析为框架内部统一的 Web 请求快照。业务系统可注册自定义 Bean 替换默认解析逻辑。
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
public interface CocoWebRequestContextResolver {

    /**
     * <p>
     * 解析当前 HTTP 请求。
     * </p>
     * @param traceId 当前请求 TraceId
     * @param request 当前 HTTP 请求
     * @return Web 请求快照
     */
    CocoWebRequestSnapshot resolve(String traceId, HttpServletRequest request);
}
