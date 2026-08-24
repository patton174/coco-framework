package io.github.coco.scheduling;

/** Coco 任务执行 guard 类型。 */
public enum CocoSchedulingGuardType {

    /** 仅进程内互斥。 */
    IN_MEMORY,

    /** 通过 CocoLockManager 实现跨实例互斥。 */
    COCO_LOCK
}
