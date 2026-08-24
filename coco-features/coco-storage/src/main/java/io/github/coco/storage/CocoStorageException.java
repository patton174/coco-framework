package io.github.coco.storage;

import io.github.coco.exception.CocoException;

/**
 * Coco 对象存储异常。
 */
public class CocoStorageException extends CocoException {

    /**
     * 使用存储错误编码创建异常。
     * @param errorCode 存储错误编码
     * @param args 国际化参数
     */
    public CocoStorageException(CocoStorageErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    /**
     * 使用存储错误编码和原因创建异常。
     * @param errorCode 存储错误编码
     * @param cause 原始异常
     * @param args 国际化参数
     */
    public CocoStorageException(CocoStorageErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
