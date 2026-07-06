package io.github.coco.feature.web.context;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Coco 请求参数解析器。
 * <p>
 * 定义查询字符串和请求参数采集契约，默认实现同时提供访问日志使用的清洗视图和安全输入使用的原始视图。
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
public interface CocoRequestParameterResolver {

    /**
     * <p>
     * 解析清洗后的查询字符串。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 清洗后的查询字符串
     */
    String resolveQueryString(HttpServletRequest request);

    /**
     * <p>
     * 解析原始查询字符串。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 原始查询字符串
     */
    String resolveRawQueryString(HttpServletRequest request);

    /**
     * <p>
     * 解析清洗后的请求参数。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 清洗后的请求参数快照
     */
    Map<String, List<String>> resolveParameters(HttpServletRequest request);

    /**
     * <p>
     * 解析原始请求参数。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 原始请求参数快照
     */
    Map<String, List<String>> resolveRawParameters(HttpServletRequest request);
}
