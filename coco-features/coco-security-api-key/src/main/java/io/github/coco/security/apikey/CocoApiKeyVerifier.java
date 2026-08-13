package io.github.coco.security.apikey;

import java.util.Optional;

import io.github.coco.feature.security.context.CocoSecurityPrincipal;

/**
 * Coco API Key 校验 SPI。
 * <p>
 * 集成方可以替换默认实现，将 API Key 验证结果映射为 Coco 安全主体。实现不得保存、记录或返回传入的原始 Key。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoApiKeyVerifier {

    /**
     * 校验原始 API Key。
     * @param key 当前请求中唯一的 API Key
     * @return 已验证的安全主体；校验失败时为空
     */
    Optional<CocoSecurityPrincipal> verify(String key);
}
