package io.github.coco.feature.web.context;

/**
 * Coco Web 请求规范化用途。
 * <p>
 * 区分同一份请求安全输入在 Sign 验签、AES 附加认证数据、防重放键和浏览器指纹等场景下的使用目的。
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
public enum CocoWebRequestCanonicalizationPurpose {

    /**
     * 通用请求规范化。
     */
    GENERAL,

    /**
     * Sign 请求签名。
     */
    SIGNATURE,

    /**
     * AES 附加认证数据。
     */
    ENCRYPTION_AAD,

    /**
     * 防重放键。
     */
    REPLAY_KEY,

    /**
     * 浏览器指纹。
     */
    BROWSER_FINGERPRINT
}
