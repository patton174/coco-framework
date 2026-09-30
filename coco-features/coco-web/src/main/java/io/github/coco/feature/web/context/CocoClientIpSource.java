package io.github.coco.feature.web.context;

/**
 * Coco 客户端 IP 来源类型。
 * <p>
 * 表示客户端 IP 是从远端地址、代理请求头、业务自定义解析器或未解析状态中得到的。
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
public enum CocoClientIpSource {

    /**
     * 直接使用 Servlet 远端地址。
     */
    REMOTE_ADDRESS,

    /**
     * 从可信代理请求头中解析。
     */
    FORWARDED_HEADER,

    /**
     * 由业务自定义解析器返回。
     */
    CUSTOM,

    /**
     * 未解析到有效客户端 IP。
     */
    UNRESOLVED
}
