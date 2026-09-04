package io.github.coco.messaging;

/** Coco 消息没有订阅者时的策略。 */
public enum CocoMessageNoSubscriberPolicy {

    /** 忽略没有订阅者的消息。 */
    IGNORE,

    /** 将没有订阅者视为投递失败。 */
    FAIL
}
