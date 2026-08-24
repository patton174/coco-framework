package io.github.coco.storage;

import io.github.coco.exception.CocoErrorCode;

/**
 * Coco 存储模块错误编码。
 */
public enum CocoStorageErrorCode implements CocoErrorCode {

    /** 对象键不安全。 */
    INVALID_KEY("coco.storage.invalid-key", "对象键不合法。"),
    /** 存储根目录不安全。 */
    INVALID_ROOT("coco.storage.invalid-root", "对象存储根目录不合法。"),
    /** 存储配置不合法。 */
    INVALID_CONFIGURATION("coco.storage.invalid-configuration", "对象存储配置不合法。"),
    /** 对象不存在。 */
    OBJECT_NOT_FOUND("coco.storage.object-not-found", "对象不存在。"),
    /** 对象已存在。 */
    OBJECT_ALREADY_EXISTS("coco.storage.object-already-exists", "对象已存在，拒绝覆盖。"),
    /** 上传内容类型不允许。 */
    CONTENT_TYPE_NOT_ALLOWED("coco.storage.content-type-not-allowed", "不允许的内容类型。"),
    /** 上传扩展名不允许。 */
    EXTENSION_NOT_ALLOWED("coco.storage.extension-not-allowed", "不允许的对象扩展名。"),
    /** 声明长度非法。 */
    CONTENT_LENGTH_MISMATCH("coco.storage.content-length-mismatch", "上传内容长度与声明长度不一致。"),
    /** 上传内容过大。 */
    CONTENT_TOO_LARGE("coco.storage.content-too-large", "上传内容超过允许的最大长度。"),
    /** 本地存储访问失败。 */
    STORAGE_IO_FAILURE("coco.storage.io-failure", "对象存储访问失败。"),
    /** 本地元数据损坏。 */
    CORRUPT_METADATA("coco.storage.corrupt-metadata", "对象存储元数据无效。");

    private final String code;

    private final String defaultMessage;

    CocoStorageErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return this.code;
    }

    @Override
    public String defaultMessage() {
        return this.defaultMessage;
    }
}
