package io.github.coco.web.context;

import java.util.List;
import java.util.Map;

import io.github.coco.web.context.payload.CocoWebPayloadParseStatus;
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
 *   <li>模块：{@code coco-web}</li>
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
     * 解析清洗后的查询参数。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 清洗后的查询参数快照
     */
    default Map<String, List<String>> resolveQueryParameters(HttpServletRequest request) {
        return Map.of();
    }

    /**
     * <p>
     * 解析清洗后的请求体参数。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 清洗后的请求体参数快照
     */
    default Map<String, List<String>> resolvePayloadParameters(HttpServletRequest request) {
        return Map.of();
    }

    /**
     * <p>
     * 解析原始请求参数。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 原始请求参数快照
     */
    Map<String, List<String>> resolveRawParameters(HttpServletRequest request);

    /**
     * <p>
     * 解析清洗后的请求参数快照。
     * </p>
     * <p>
     * 默认实现组合查询字符串、合并参数、查询参数和请求体参数解析方法。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 清洗后的请求参数快照
     */
    default CocoWebRequestParameters resolveParameterSnapshot(HttpServletRequest request) {
        return new CocoWebRequestParameters(resolveQueryString(request), resolveParameters(request),
                resolveQueryParameters(request), resolvePayloadParameters(request));
    }

    /**
     * <p>
     * 解析原始查询参数。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 原始查询参数快照
     */
    default Map<String, List<String>> resolveRawQueryParameters(HttpServletRequest request) {
        return Map.of();
    }

    /**
     * <p>
     * 解析原始请求体参数。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 原始请求体参数快照
     */
    default Map<String, List<String>> resolveRawPayloadParameters(HttpServletRequest request) {
        return Map.of();
    }

    /**
     * <p>
     * 解析当前请求体参数解析状态。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 请求体参数解析状态
     */
    default CocoWebPayloadParseStatus resolvePayloadParseStatus(HttpServletRequest request) {
        return CocoWebPayloadParseStatus.DISABLED;
    }

    /**
     * <p>
     * 解析原始请求参数快照。
     * </p>
     * <p>
     * 默认实现组合原始查询字符串、原始合并参数、原始查询参数和原始请求体参数解析方法。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 原始请求参数快照
     */
    default CocoWebRequestParameters resolveRawParameterSnapshot(HttpServletRequest request) {
        return new CocoWebRequestParameters(resolveRawQueryString(request), resolveRawParameters(request),
                resolveRawQueryParameters(request), resolveRawPayloadParameters(request));
    }
}
