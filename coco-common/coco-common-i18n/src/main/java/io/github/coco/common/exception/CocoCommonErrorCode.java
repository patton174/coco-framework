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
 *   <li>模块：{@code coco-common-i18n}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public enum CocoCommonErrorCode implements CocoErrorCode {

    UNKNOWN("coco.error.unknown", "Unknown error"),

    INVALID_ARGUMENT("coco.error.invalid-argument", "Invalid argument: {0}"),

    MISSING_MESSAGE_CODE("coco.error.missing-message-code", "Message code must not be blank");

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
