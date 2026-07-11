package io.github.coco.security;

import io.github.coco.exception.CocoErrorCode;

/**
 * Coco 安全功能错误码。
 * <p>
 * 仅维护安全模块自身的框架级错误编码，具体提示文本由安全模块消息资源负责解析。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-security}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public enum CocoSecurityErrorCode implements CocoErrorCode {

    /**
     * 当前线程不存在安全上下文。
     */
    CONTEXT_MISSING("coco.feature.security.error.context-missing"),

    /**
     * 当前安全上下文尚未完成认证。
     */
    UNAUTHENTICATED("coco.feature.security.error.unauthenticated"),

    /**
     * 当前安全主体无权访问目标资源。
     */
    ACCESS_DENIED("coco.feature.security.error.access-denied");

    private final String code;

    CocoSecurityErrorCode(String code) {
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
