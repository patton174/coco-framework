package io.github.coco.feature.tenant;

import io.github.coco.exception.CocoErrorCode;

/**
 * Coco 租户功能错误码。
 * <p>
 * 仅维护租户模块自身的框架级错误编码，具体提示文本由租户模块消息资源负责解析。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-tenant}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public enum CocoTenantErrorCode implements CocoErrorCode {

    /**
     * 当前线程不存在租户上下文。
     */
    CONTEXT_MISSING("coco.feature.tenant.error.context-missing"),

    /**
     * MyBatis-Plus 租户隔离忽略未进入白名单。
     */
    INTERCEPTOR_IGNORE_BLOCKED("coco.feature.tenant.error.interceptor-ignore-blocked");

    private final String code;

    CocoTenantErrorCode(String code) {
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
