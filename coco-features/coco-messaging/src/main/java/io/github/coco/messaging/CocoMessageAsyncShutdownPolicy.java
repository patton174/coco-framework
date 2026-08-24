package io.github.coco.messaging;

/** Coco 异步消息传输关闭策略。 */
public enum CocoMessageAsyncShutdownPolicy {

    /** 停止接收新消息，并在超时前排空已入队消息。 */
    DRAIN,

    /** 停止接收新消息，并立即取消尚未开始的投递。 */
    DISCARD
}
