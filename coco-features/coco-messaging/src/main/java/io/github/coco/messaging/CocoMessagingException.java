package io.github.coco.messaging;

import io.github.coco.messaging.support.CocoMessagingMessages;

/**
 * Coco 消息模块异常。
 */
public class CocoMessagingException extends RuntimeException {

    private final String messageCode;

    /**
     * 创建消息模块异常。
     * @param messageCode 国际化消息编码
     * @param arguments 消息参数
     */
    public CocoMessagingException(String messageCode, Object... arguments) {
        super(CocoMessagingMessages.message(messageCode, arguments));
        this.messageCode = messageCode;
    }

    /**
     * 创建带原因的消息模块异常。
     * @param messageCode 国际化消息编码
     * @param cause 原始异常
     * @param arguments 消息参数
     */
    public CocoMessagingException(String messageCode, Throwable cause, Object... arguments) {
        super(CocoMessagingMessages.message(messageCode, arguments), cause);
        this.messageCode = messageCode;
    }

    /** @return 国际化消息编码 */
    public String messageCode() {
        return this.messageCode;
    }

    /**
     * 创建参数校验异常。
     * @param messageCode 国际化消息编码
     * @param arguments 消息参数
     * @return 参数异常
     */
    public static CocoMessagingException invalidArgument(String messageCode, Object... arguments) {
        return new CocoMessagingException(messageCode, arguments);
    }
}
