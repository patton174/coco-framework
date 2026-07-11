package io.github.coco.web.encryption;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Coco 加密文本编码工具。
 * <p>
 * 为 AES 密钥、IV 和密文请求体提供统一的文本编码解码逻辑。
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
final class CocoEncryptionCodecs {

    private CocoEncryptionCodecs() {
    }

    static byte[] decode(String value, CocoCryptoTextEncoding encoding) {
        if (value == null) {
            return new byte[0];
        }
        return switch (encoding == null ? CocoCryptoTextEncoding.BASE64 : encoding) {
            case BASE64 -> Base64.getDecoder().decode(value.trim());
            case HEX -> HexFormat.of().parseHex(value.trim());
            case UTF8, RAW -> value.getBytes(StandardCharsets.UTF_8);
        };
    }

    static byte[] decode(byte[] value, CocoCryptoTextEncoding encoding) {
        byte[] checkedValue = value == null ? new byte[0] : value;
        return switch (encoding == null ? CocoCryptoTextEncoding.BASE64 : encoding) {
            case BASE64 -> Base64.getDecoder().decode(new String(checkedValue, StandardCharsets.UTF_8).trim());
            case HEX -> HexFormat.of().parseHex(new String(checkedValue, StandardCharsets.UTF_8).trim());
            case UTF8 -> new String(checkedValue, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
            case RAW -> checkedValue.clone();
        };
    }
}
