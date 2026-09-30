package io.github.coco.feature.web.signature;

/**
 * Coco 请求签名验证器。
 * <p>
 * 业务项目可以提供同类型 Bean 覆盖默认 HMAC-SHA256 签名验证策略。
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
@FunctionalInterface
public interface CocoSignatureVerifier {

    /**
     * <p>
     * 验证请求签名。
     * </p>
     * @param context 请求签名验证上下文
     * @return 签名有效时返回 {@code true}
     */
    boolean verify(CocoSignatureVerificationContext context);
}
