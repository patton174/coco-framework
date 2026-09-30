package io.github.coco.feature.web.body;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Coco 已缓存请求体。
 * <p>
 * 保存可复读的请求体字节、请求体长度和 SHA-256 摘要，避免签名、解密和业务读取阶段重复消费原始输入流。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @param content 请求体字节
 * @param sha256 请求体 SHA-256 摘要
 * @param length 请求体长度
 * @param cached 是否真实缓存了请求体
 * @author patton174
 * @since 1.0.0
 */
public record CocoCachedRequestBody(byte[] content, String sha256, long length, boolean cached) {

    private static final CocoCachedRequestBody EMPTY = new CocoCachedRequestBody(new byte[0], null, 0L, false);

    /**
     * <p>
     * 创建已缓存请求体，并复制字节数组避免外部修改。
     * </p>
     * @param content 请求体字节
     * @param sha256 请求体 SHA-256 摘要
     * @param length 请求体长度
     * @param cached 是否真实缓存了请求体
     */
    public CocoCachedRequestBody {
        content = content == null ? new byte[0] : content.clone();
        length = Math.max(0L, length);
        if (cached) {
            sha256 = normalizeSha256(sha256, content);
            length = content.length;
        }
        else {
            sha256 = null;
            length = 0L;
        }
    }

    /**
     * <p>
     * 返回未缓存请求体。
     * </p>
     * @return 未缓存请求体
     */
    public static CocoCachedRequestBody empty() {
        return EMPTY;
    }

    /**
     * <p>
     * 根据字节数组创建已缓存请求体。
     * </p>
     * @param content 请求体字节
     * @return 已缓存请求体
     */
    public static CocoCachedRequestBody cached(byte[] content) {
        byte[] copiedContent = content == null ? new byte[0] : content.clone();
        return new CocoCachedRequestBody(copiedContent, sha256(copiedContent), copiedContent.length, true);
    }

    /**
     * <p>
     * 返回请求体字节副本。
     * </p>
     * @return 请求体字节副本
     */
    @Override
    public byte[] content() {
        return this.content.clone();
    }

    /**
     * <p>
     * 按指定字符集读取请求体文本。
     * </p>
     * @param charset 字符集；为空时使用 UTF-8
     * @return 请求体文本
     */
    public String text(Charset charset) {
        Charset checkedCharset = charset == null ? StandardCharsets.UTF_8 : charset;
        return new String(this.content, checkedCharset);
    }

    private static String normalizeSha256(String sha256, byte[] content) {
        if (sha256 == null || sha256.isBlank()) {
            return sha256(content);
        }
        return sha256.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256(byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }
}
