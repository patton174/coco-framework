package io.github.coco.security.apikey;

import io.github.coco.exception.type.CocoUnauthorizedException;

/**
 * API Key 认证失败异常。
 * <p>
 * 异常不携带请求头、原始 Key 或摘要，避免认证材料进入统一错误响应或日志。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
final class CocoApiKeyAuthenticationException extends CocoUnauthorizedException {

    private static final long serialVersionUID = 1L;

    CocoApiKeyAuthenticationException() {
        super("coco.security.api-key.authentication-failed", "API Key authentication failed");
    }
}
