package io.github.coco.common.exception;

/**
 * Coco 通用内置异常编码。
 * <p>
 * 提供 common 基础设施自身使用的稳定异常编码，具体语言文本由 {@code coco-messages} 资源包解析。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-exception}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public enum CocoCommonErrorCode implements CocoErrorCode {

    /**
     * <p>
     * 未知错误。
     * </p>
     */
    UNKNOWN("coco.error.unknown", "Unknown error"),

    /**
     * <p>
     * 请求参数不合法。
     * </p>
     */
    INVALID_ARGUMENT("coco.error.invalid-argument", "Invalid argument: {0}"),

    /**
     * <p>
     * 消息编码为空。
     * </p>
     */
    MISSING_MESSAGE_CODE("coco.error.missing-message-code", "Message code must not be blank"),

    /**
     * <p>
     * 请求未认证。
     * </p>
     */
    UNAUTHORIZED("coco.error.unauthorized", "Unauthorized"),

    /**
     * <p>
     * 请求无访问权限。
     * </p>
     */
    FORBIDDEN("coco.error.forbidden", "Forbidden"),

    /**
     * <p>
     * 请求资源不存在。
     * </p>
     */
    NOT_FOUND("coco.error.not-found", "Resource not found: {0}"),

    /**
     * <p>
     * 请求资源冲突。
     * </p>
     */
    CONFLICT("coco.error.conflict", "Resource conflict: {0}"),

    /**
     * <p>
     * 服务端内部错误。
     * </p>
     */
    INTERNAL_ERROR("coco.error.internal-error", "Internal server error");

    private final String code;

    private final String defaultMessage;

    CocoCommonErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String code() {
        return this.code;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String defaultMessage() {
        return this.defaultMessage;
    }
}
