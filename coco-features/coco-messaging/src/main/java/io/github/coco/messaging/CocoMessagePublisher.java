package io.github.coco.messaging;

/**
 * Coco 消息发布入口。
 * <p>
 * 默认实现只委托给当前 {@link CocoMessageTransport}，不会提供持久化、事务或消息代理语义。
 * </p>
 */
public interface CocoMessagePublisher {

    /**
     * 发布已创建的消息信封。
     * @param envelope 消息信封
     */
    void publish(CocoMessageEnvelope envelope);

    /**
     * 使用当前上下文创建并发布消息。
     * @param topic 主题
     * @param payload 业务负载
     * @return 已提交给传输层的消息信封
     */
    default CocoMessageEnvelope publish(String topic, Object payload) {
        CocoMessageEnvelope envelope = CocoMessageEnvelope.create(topic, payload);
        publish(envelope);
        return envelope;
    }
}
