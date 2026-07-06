package io.github.coco.feature.web.signature;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于配置属性的 Coco 请求签名密钥解析器。
 * <p>
 * 优先使用 {@code appId:keyId} 查找密钥；未命中时回退到 {@code appId}。
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
public final class PropertiesCocoSignatureSecretResolver implements CocoSignatureSecretResolver {

    private final Map<String, String> secrets;

    /**
     * <p>
     * 创建基于配置属性的请求签名密钥解析器。
     * </p>
     * @param properties 请求签名配置属性
     */
    public PropertiesCocoSignatureSecretResolver(CocoSignatureProperties properties) {
        CocoSignatureProperties checkedProperties = properties == null
                ? new CocoSignatureProperties()
                : properties;
        this.secrets = checkedProperties.getSecrets();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<CocoSignatureSecret> resolve(CocoSignatureRequest request) {
        CocoSignatureRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        if (checkedRequest.appId() == null) {
            return Optional.empty();
        }
        Optional<String> secret = keyedSecret(checkedRequest)
                .or(() -> Optional.ofNullable(this.secrets.get(checkedRequest.appId())));
        return secret.map(value -> new CocoSignatureSecret(checkedRequest.appId(), checkedRequest.keyId(), value));
    }

    private Optional<String> keyedSecret(CocoSignatureRequest request) {
        if (request.keyId() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.secrets.get(request.appId() + ":" + request.keyId()));
    }
}
