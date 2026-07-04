package io.github.coco.common.exception;

import java.util.Arrays;

/**
 * Coco 框架异常基类。
 * <p>
 * 保存国际化消息编码、默认文本和参数，具体语言文本由上层消息服务或异常处理器解析。
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
public class CocoException extends RuntimeException {

    private final String code;

    private final String defaultMessage;

    private final Object[] args;

    public CocoException(String code) {
        this(code, null);
    }

    public CocoException(String code, String defaultMessage) {
        this(code, defaultMessage, new Object[0]);
    }

    public CocoException(String code, String defaultMessage, Object... args) {
        super(messageOrCode(code, defaultMessage));
        this.code = requireCode(code);
        this.defaultMessage = defaultMessage;
        this.args = args == null ? new Object[0] : Arrays.copyOf(args, args.length);
    }

    public CocoException(String code, String defaultMessage, Throwable cause) {
        super(messageOrCode(code, defaultMessage), cause);
        this.code = requireCode(code);
        this.defaultMessage = defaultMessage;
        this.args = new Object[0];
    }

    public String code() {
        return this.code;
    }

    public Object[] args() {
        return Arrays.copyOf(this.args, this.args.length);
    }

    public String defaultMessage() {
        return this.defaultMessage;
    }

    private static String messageOrCode(String code, String defaultMessage) {
        String checkedCode = requireCode(code);
        return defaultMessage == null || defaultMessage.isBlank() ? checkedCode : defaultMessage;
    }

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("message code must not be blank");
        }
        return code;
    }
}
