package io.github.coco.web.signature;

import java.util.Objects;

/**
 * Coco 请求签名验证上下文。
 * <p>
 * 聚合请求签名材料和已解析密钥，作为签名验证器的稳定输入。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @param request 请求签名材料
 * @param secret 请求签名密钥
 * @author patton174
 * @since 1.0.0
 */
public record CocoSignatureVerificationContext(CocoSignatureRequest request, CocoSignatureSecret secret) {

    /**
     * <p>
     * 创建请求签名验证上下文。
     * </p>
     * @param request 请求签名材料
     * @param secret 请求签名密钥
     */
    public CocoSignatureVerificationContext {
        request = Objects.requireNonNull(request, "request must not be null");
        secret = Objects.requireNonNull(secret, "secret must not be null");
    }
}
