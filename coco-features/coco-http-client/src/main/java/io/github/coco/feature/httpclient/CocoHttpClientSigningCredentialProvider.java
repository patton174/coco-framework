package io.github.coco.feature.httpclient;

import java.util.Optional;

/**
 * 按命名 HTTP 客户端提供出站签名凭据的业务 SPI。
 *
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoHttpClientSigningCredentialProvider {

    /**
     * 查询指定客户端的签名凭据。
     *
     * @param clientName 客户端名称
     * @return 可用的不可变凭据；未配置时为空
     */
    Optional<CocoHttpClientSigningCredential> resolve(String clientName);
}
