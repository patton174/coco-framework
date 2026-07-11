package io.github.coco.web.context.payload;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Coco 请求体参数解析器。
 * <p>
 * 从已缓存请求体中解析 payload 参数，提供给访问日志上下文的清洗视图和安全能力使用的原始视图。
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
public interface CocoPayloadParameterResolver {

    /**
     * <p>
     * 解析清洗后的请求体参数结果。
     * </p>
     * <p>
     * 默认实现直接组合清洗后的请求体参数与解析状态。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 清洗后的请求体参数解析结果
     */
    default CocoWebPayloadParseResult resolvePayloadParseResult(HttpServletRequest request) {
        return new CocoWebPayloadParseResult(resolvePayloadParameters(request), resolvePayloadParseStatus(request), null);
    }

    /**
     * <p>
     * 解析清洗后的请求体参数。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 清洗后的请求体参数
     */
    Map<String, List<String>> resolvePayloadParameters(HttpServletRequest request);

    /**
     * <p>
     * 解析原始请求体参数结果。
     * </p>
     * <p>
     * 默认实现直接组合原始请求体参数与解析状态。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 原始请求体参数解析结果
     */
    default CocoWebPayloadParseResult resolveRawPayloadParseResult(HttpServletRequest request) {
        return new CocoWebPayloadParseResult(resolveRawPayloadParameters(request), resolvePayloadParseStatus(request),
                null);
    }

    /**
     * <p>
     * 解析原始请求体参数。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 原始请求体参数
     */
    Map<String, List<String>> resolveRawPayloadParameters(HttpServletRequest request);

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
}
