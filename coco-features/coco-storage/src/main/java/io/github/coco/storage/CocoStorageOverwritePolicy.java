package io.github.coco.storage;

/**
 * Coco 对象覆盖策略。
 */
public enum CocoStorageOverwritePolicy {

    /** 拒绝写入已存在对象。 */
    REJECT,

    /** 原子替换已发布对象的元数据引用。 */
    REPLACE
}
