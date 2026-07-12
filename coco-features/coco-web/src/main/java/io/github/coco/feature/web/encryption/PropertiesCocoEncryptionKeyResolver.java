package io.github.coco.feature.web.encryption;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于配置属性的 Coco AES 解密密钥解析器。
 * <p>
 * 优先使用 {@code appId:keyId} 查找密钥；未命中时回退到 {@code appId}。
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
public final class PropertiesCocoEncryptionKeyResolver implements CocoEncryptionKeyResolver {

    private final CocoEncryptionProperties properties;

    private final Map<String, String> keys;

    /**
     * <p>
     * 创建基于配置属性的 AES 解密密钥解析器。
     * </p>
     * @param properties 请求加密配置属性
     */
    public PropertiesCocoEncryptionKeyResolver(CocoEncryptionProperties properties) {
        this.properties = properties == null ? new CocoEncryptionProperties() : properties;
        this.keys = this.properties.getKeys();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<CocoEncryptionKey> resolve(CocoEncryptedRequest request) {
        CocoEncryptedRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        if (checkedRequest.appId() == null) {
            return Optional.empty();
        }
        Optional<String> key = keyedSecret(checkedRequest)
                .or(() -> Optional.ofNullable(this.keys.get(checkedRequest.appId())));
        return key.map(value -> new CocoEncryptionKey(checkedRequest.appId(), checkedRequest.keyId(),
                CocoEncryptionCodecs.decode(value, this.properties.getKeyEncoding())));
    }

    private Optional<String> keyedSecret(CocoEncryptedRequest request) {
        if (request.keyId() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.keys.get(request.appId() + ":" + request.keyId()));
    }
}
