package io.github.coco.scheduling;

/**
 * Coco 调度配置或注册异常。
 *
 * @since 1.0.0
 */
public class CocoSchedulingException extends IllegalArgumentException {

    private final String code;

    CocoSchedulingException(CocoSchedulingMessage message, String resolvedMessage) {
        super(resolvedMessage);
        this.code = message.code();
    }

    /**
     * 返回国际化消息编码。
     *
     * @return 消息编码
     */
    public String getCode() {
        return this.code;
    }
}
