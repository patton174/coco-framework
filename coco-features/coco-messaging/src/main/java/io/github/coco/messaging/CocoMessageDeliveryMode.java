package io.github.coco.messaging;

/** Coco 消息投递模式。 */
public enum CocoMessageDeliveryMode {

    /** 在发布线程同步投递。 */
    SYNC,

    /** 提交到默认本地传输的有界异步队列。 */
    ASYNC
}
