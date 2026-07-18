package io.github.coco.feature.web.encryption;

/**
 * Coco 请求解密异常。
 * <p>
 * 表示 AES 解密失败、密文格式错误或认证标签校验失败。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public class CocoRequestDecryptException extends RuntimeException {

    private static final String DECRYPT_FAILED_CODE = "coco.web.encryption.decrypt-failed";

    private final String messageCode;

    private final FailureKind failureKind;

    /**
     * <p>
     * 创建请求解密异常。
     * </p>
     * @param message 异常消息
     * @param cause 异常原因
     */
    public CocoRequestDecryptException(String message, Throwable cause) {
        this(message, DECRYPT_FAILED_CODE, cause, FailureKind.AUTHENTICATION_FAILED);
    }

    private CocoRequestDecryptException(String message, String messageCode, Throwable cause, FailureKind failureKind) {
        super(message, cause);
        this.messageCode = messageCode;
        this.failureKind = failureKind == null ? FailureKind.AUTHENTICATION_FAILED : failureKind;
    }

    /**
     * <p>
     * 创建请求格式错误的解密异常。
     * </p>
     * @param messageCode 消息编码
     * @param cause 异常原因
     * @return 请求格式错误的解密异常
     */
    public static CocoRequestDecryptException malformed(String messageCode, Throwable cause) {
        return new CocoRequestDecryptException(messageCode, messageCode, cause, FailureKind.MALFORMED_REQUEST);
    }

    /**
     * <p>
     * 创建认证或完整性校验失败的解密异常。
     * </p>
     * @param messageCode 消息编码
     * @param cause 异常原因
     * @return 认证或完整性校验失败的解密异常
     */
    public static CocoRequestDecryptException authenticationFailed(String messageCode, Throwable cause) {
        return new CocoRequestDecryptException(messageCode, messageCode, cause, FailureKind.AUTHENTICATION_FAILED);
    }

    /**
     * <p>
     * 返回消息编码。
     * </p>
     * @return 消息编码
     */
    public String messageCode() {
        return this.messageCode;
    }

    /**
     * <p>
     * 返回解密失败分类。
     * </p>
     * @return 解密失败分类
     */
    public FailureKind failureKind() {
        return this.failureKind;
    }

    /**
     * Coco 请求解密失败分类。
     */
    public enum FailureKind {

        /**
         * 请求加密协议材料格式错误。
         */
        MALFORMED_REQUEST,

        /**
         * 密文认证或完整性校验失败。
         */
        AUTHENTICATION_FAILED
    }
}
