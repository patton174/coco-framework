package io.github.coco.feature.web.encryption;

/**
 * Coco 加密文本编码。
 * <p>
 * 定义密钥、IV 和密文在请求头或请求体中传输时使用的文本编码方式。
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
public enum CocoCryptoTextEncoding {

    /**
     * Base64 文本编码。
     */
    BASE64,

    /**
     * 十六进制文本编码。
     */
    HEX,

    /**
     * UTF-8 文本编码。
     */
    UTF8,

    /**
     * 原始字节，不做文本解码。
     */
    RAW
}
