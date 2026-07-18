package io.github.coco.feature.web.encryption;

/**
 * Coco 请求解密器。
 * <p>
 * 业务项目可以提供同类型 Bean 覆盖默认 AES-GCM 解密策略。
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
@FunctionalInterface
public interface CocoRequestDecryptor {

    /**
     * <p>
     * 解密请求体。
     * </p>
     * @param context 请求解密上下文
     * @return 明文请求体字节
     */
    byte[] decrypt(CocoRequestDecryptionContext context);
}
