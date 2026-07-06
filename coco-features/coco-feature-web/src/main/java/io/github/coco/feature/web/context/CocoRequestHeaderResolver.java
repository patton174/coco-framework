package io.github.coco.feature.web.context;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Coco 请求头解析器。
 * <p>
 * 定义 Web 入口请求头采集契约，默认实现负责普通上下文请求头、签名规范化请求头和安全能力请求头的读取与清洗。
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
public interface CocoRequestHeaderResolver {

    /**
     * <p>
     * 解析需要写入请求上下文的请求头。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 请求头快照
     */
    Map<String, String> resolveIncludedHeaders(HttpServletRequest request);

    /**
     * <p>
     * 按指定请求头名称解析请求头。
     * </p>
     * @param request 当前 Servlet 请求
     * @param headerNames 请求头名称集合
     * @param trimValue 是否按配置裁剪请求头值
     * @return 请求头快照
     */
    Map<String, String> resolveSelectedHeaders(HttpServletRequest request, Iterable<String> headerNames,
            boolean trimValue);
}
