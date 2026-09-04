package io.github.coco.storage;

import io.github.coco.exception.CocoErrorCode;

/**
 * Coco 存储模块错误编码。
 * <p>
 * 只保留消息编码，消息文本全部由 {@code coco-storage-messages} 国际化资源提供。
 * </p>
 */
public enum CocoStorageErrorCode implements CocoErrorCode {

    /** 对象键不安全。 */
    INVALID_KEY("coco.storage.invalid-key"),
    /** 存储根目录不安全。 */
    INVALID_ROOT("coco.storage.invalid-root"),
    /** 存储配置不合法。 */
    INVALID_CONFIGURATION("coco.storage.invalid-configuration"),
    /** 对象不存在。 */
    OBJECT_NOT_FOUND("coco.storage.object-not-found"),
    /** 对象已存在。 */
    OBJECT_ALREADY_EXISTS("coco.storage.object-already-exists"),
    /** 上传内容类型不允许。 */
    CONTENT_TYPE_NOT_ALLOWED("coco.storage.content-type-not-allowed"),
    /** 上传扩展名不允许。 */
    EXTENSION_NOT_ALLOWED("coco.storage.extension-not-allowed"),
    /** 声明长度非法。 */
    CONTENT_LENGTH_MISMATCH("coco.storage.content-length-mismatch"),
    /** 上传内容过大。 */
    CONTENT_TOO_LARGE("coco.storage.content-too-large"),
    /** 本地存储访问失败。 */
    STORAGE_IO_FAILURE("coco.storage.io-failure"),
    /** 本地元数据损坏。 */
    CORRUPT_METADATA("coco.storage.corrupt-metadata"),
    /** 内容魔数与声明扩展名不一致。 */
    SIGNATURE_MISMATCH("coco.storage.signature-mismatch"),
    /** 内容命中危险文件签名。 */
    DANGEROUS_CONTENT("coco.storage.dangerous-content"),
    /** 内容被扫描器拒绝。 */
    SCAN_REJECTED("coco.storage.scan-rejected");

    private final String code;

    CocoStorageErrorCode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return this.code;
    }
}
