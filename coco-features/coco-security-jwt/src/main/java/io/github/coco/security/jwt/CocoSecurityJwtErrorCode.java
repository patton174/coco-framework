package io.github.coco.security.jwt;

import io.github.coco.exception.CocoErrorCode;

/**
 * Coco JWT Resource Server 错误码。
 *
 * @author patton174
 * @since 1.0.0
 */
public enum CocoSecurityJwtErrorCode implements CocoErrorCode {

    /**
     * Bearer 令牌认证失败。
     */
    AUTHENTICATION_FAILED("coco.security.jwt.error.authentication-failed"),

    /**
     * 已认证主体没有访问权限。
     */
    ACCESS_DENIED("coco.security.jwt.error.access-denied");

    private final String code;

    CocoSecurityJwtErrorCode(String code) {
        this.code = code;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String code() {
        return this.code;
    }
}
