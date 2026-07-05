package io.github.coco.common.exception;

import java.util.Arrays;

import io.github.coco.common.i18n.api.CocoMessage;

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
 *   <li>模块：{@code coco-common-exception}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public class CocoException extends RuntimeException {

    /**
     * 国际化消息编码。
     */
    private final String code;

    /**
     * 消息资源缺失时使用的默认文本。
     */
    private final String defaultMessage;

    /**
     * 消息格式化参数。
     */
    private final Object[] args;

    /**
     * <p>
     * 使用异常编码契约创建 Coco 异常。
     * </p>
     * @param errorCode 异常编码契约
     * @param args 消息格式化参数
     */
    public CocoException(CocoErrorCode errorCode, Object... args) {
        this(code(errorCode), defaultMessage(errorCode), args);
    }

    /**
     * <p>
     * 使用异常编码契约和异常原因创建 Coco 异常。
     * </p>
     * @param errorCode 异常编码契约
     * @param cause 异常原因
     * @param args 消息格式化参数
     */
    public CocoException(CocoErrorCode errorCode, Throwable cause, Object... args) {
        this(code(errorCode), defaultMessage(errorCode), cause, args);
    }

    /**
     * <p>
     * 使用消息编码创建 Coco 异常。
     * </p>
     * @param code 消息编码
     */
    public CocoException(String code) {
        this(code, null);
    }

    /**
     * <p>
     * 使用消息编码和默认文本创建 Coco 异常。
     * </p>
     * @param code 消息编码
     * @param defaultMessage 默认消息文本
     */
    public CocoException(String code, String defaultMessage) {
        this(code, defaultMessage, new Object[0]);
    }

    /**
     * <p>
     * 使用消息编码、默认文本和格式化参数创建 Coco 异常。
     * </p>
     * @param code 消息编码
     * @param defaultMessage 默认消息文本
     * @param args 消息格式化参数
     */
    public CocoException(String code, String defaultMessage, Object... args) {
        super(messageOrCode(code, defaultMessage));
        this.code = requireCode(code);
        this.defaultMessage = defaultMessage;
        this.args = args == null ? new Object[0] : Arrays.copyOf(args, args.length);
    }

    /**
     * <p>
     * 使用消息编码、默认文本和异常原因创建 Coco 异常。
     * </p>
     * @param code 消息编码
     * @param defaultMessage 默认消息文本
     * @param cause 异常原因
     */
    public CocoException(String code, String defaultMessage, Throwable cause) {
        this(code, defaultMessage, cause, new Object[0]);
    }

    /**
     * <p>
     * 使用消息编码、默认文本、异常原因和格式化参数创建 Coco 异常。
     * </p>
     * @param code 消息编码
     * @param defaultMessage 默认消息文本
     * @param cause 异常原因
     * @param args 消息格式化参数
     */
    public CocoException(String code, String defaultMessage, Throwable cause, Object... args) {
        super(messageOrCode(code, defaultMessage), cause);
        this.code = requireCode(code);
        this.defaultMessage = defaultMessage;
        this.args = args == null ? new Object[0] : Arrays.copyOf(args, args.length);
    }

    /**
     * <p>
     * 返回国际化消息编码。
     * </p>
     * @return 消息编码
     */
    public String code() {
        return this.code;
    }

    /**
     * <p>
     * 返回消息格式化参数的防御性副本。
     * </p>
     * @return 消息格式化参数
     */
    public Object[] args() {
        return Arrays.copyOf(this.args, this.args.length);
    }

    /**
     * <p>
     * 返回消息资源缺失时使用的默认文本。
     * </p>
     * @return 默认消息文本
     */
    public String defaultMessage() {
        return this.defaultMessage;
    }

    /**
     * <p>
     * 返回可交给国际化消息服务解析的消息描述。
     * </p>
     * @return Coco 消息描述
     */
    public CocoMessage message() {
        return new CocoMessage(this.code, this.defaultMessage, this.args);
    }

    private static String messageOrCode(String code, String defaultMessage) {
        String checkedCode = requireCode(code);
        return defaultMessage == null || defaultMessage.isBlank() ? checkedCode : defaultMessage;
    }

    private static String code(CocoErrorCode errorCode) {
        return requireErrorCode(errorCode).code();
    }

    private static String defaultMessage(CocoErrorCode errorCode) {
        return requireErrorCode(errorCode).defaultMessage();
    }

    private static CocoErrorCode requireErrorCode(CocoErrorCode errorCode) {
        if (errorCode == null) {
            throw CocoCommonErrorCode.MISSING_ERROR_CODE.request();
        }
        return errorCode;
    }

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw CocoCommonErrorCode.MISSING_MESSAGE_CODE.request();
        }
        return code;
    }
}
