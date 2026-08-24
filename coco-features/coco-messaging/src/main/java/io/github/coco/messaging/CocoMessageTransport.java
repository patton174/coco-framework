package io.github.coco.messaging;

/**
 * Coco 消息传输 SPI。
 * <p>
 * SPI 只描述本地发布和订阅边界。外部 MQ Adapter 可实现该接口，但持久化、重试、事务和 exactly-once 不属于此模块。
 * </p>
 */
public interface CocoMessageTransport extends AutoCloseable {

    /**
     * 发布消息。
     * @param envelope 消息信封
     */
    void publish(CocoMessageEnvelope envelope);

    /**
     * 订阅一个主题。
     * @param topic 主题
     * @param handler 消息处理器
     * @return 可关闭的订阅
     */
    CocoMessageSubscription subscribe(String topic, CocoMessageHandler handler);

    /**
     * 关闭传输层。
     */
    @Override
    void close();
}
