package io.github.coco.feature.web.context.target;

/**
 * <p>
 * Coco Web 请求目标来源。
 * </p>
 * <p>
 * 用于描述外部可见请求目标是直接来自 Servlet 容器，还是来自可信代理透传的
 * {@code Forwarded} / {@code X-Forwarded-*} 头，便于日志、审计和安全模块判断目标地址可信度。
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
public enum CocoWebRequestTargetSource {

    /**
     * <p>
     * 直接使用 Servlet 容器提供的目标地址。
     * </p>
     */
    SERVLET,

    /**
     * <p>
     * 仅使用标准 {@code Forwarded} 请求头解析目标地址。
     * </p>
     */
    FORWARDED,

    /**
     * <p>
     * 仅使用 {@code X-Forwarded-*} 这类兼容请求头解析目标地址。
     * </p>
     */
    FORWARDED_HEADERS,

    /**
     * <p>
     * 同时组合使用 {@code Forwarded} 与 {@code X-Forwarded-*} 请求头解析目标地址。
     * </p>
     */
    MIXED,

    /**
     * <p>
     * 由调用方自行构造的目标地址。
     * </p>
     */
    CUSTOM,

    /**
     * <p>
     * 尚未解析出有效目标地址。
     * </p>
     */
    UNRESOLVED
}
