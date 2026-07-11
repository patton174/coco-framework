package io.github.coco.web.body;

/**
 * Coco 请求体缓存模式。
 * <p>
 * 控制请求体缓存过滤器在什么条件下读取并复用请求体，为后续 Sign 验签和 AES 解密提供稳定输入。
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
public enum CocoRequestBodyCachingMode {

    /**
     * 仅当请求包含安全触发头时缓存请求体。
     */
    SECURITY_HEADERS,

    /**
     * 对符合方法和内容类型条件的请求始终缓存请求体。
     */
    ALWAYS
}
