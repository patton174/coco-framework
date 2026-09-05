package io.github.coco.messaging;

/**
 * Coco 消息处理器 SPI。
 * <p>
 * 处理器完成即表示本地传输已交付；本模块不提供确认、重试或持久化语义。
 * </p>
 */
public interface CocoMessageHandler {

    /**
     * 返回处理器订阅的主题。
     * @return 主题
     */
    String topic();

    /**
     * 处理消息。
     * @param envelope 消息信封
     */
    void handle(CocoMessageEnvelope envelope);
}
