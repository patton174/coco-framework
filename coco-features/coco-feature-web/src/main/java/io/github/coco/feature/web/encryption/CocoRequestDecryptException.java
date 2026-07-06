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
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public class CocoRequestDecryptException extends RuntimeException {

    /**
     * <p>
     * 创建请求解密异常。
     * </p>
     * @param message 异常消息
     * @param cause 异常原因
     */
    public CocoRequestDecryptException(String message, Throwable cause) {
        super(message, cause);
    }
}
