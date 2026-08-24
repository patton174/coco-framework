package io.github.coco.messaging;

/** Coco 消息处理器异常策略。 */
public enum CocoMessageFailurePolicy {

    /** 立即终止当前消息的后续处理器并向调用方报告异常。 */
    FAIL_FAST,

    /** 记录异常并继续投递到后续处理器。 */
    LOG_AND_CONTINUE
}
