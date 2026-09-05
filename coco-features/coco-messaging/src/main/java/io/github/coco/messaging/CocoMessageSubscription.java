package io.github.coco.messaging;

/**
 * Coco 消息订阅句柄。
 */
@FunctionalInterface
public interface CocoMessageSubscription extends AutoCloseable {

    /** 取消订阅；重复调用必须安全。 */
    @Override
    void close();
}
