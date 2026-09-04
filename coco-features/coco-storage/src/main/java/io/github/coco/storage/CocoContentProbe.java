package io.github.coco.storage;

import java.util.Locale;
import java.util.Objects;

/**
 * Coco 上传内容探测快照。
 * <p>
 * 只携带内容头部的若干字节，供校验器和扫描器在不缓存整个上传流的前提下做出判断。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-storage}</li>
 * </ul>
 * @author patton174
 * @since 1.1.0
 * @param key 业务方决定的对象键
 * @param extension 从对象键推导出的小写扩展名，不含点号；无扩展名时为空字符串
 * @param declaredContentType 客户端声明的内容类型，不可信
 * @param probeBytes 内容头部探测字节
 */
public record CocoContentProbe(String key, String extension, String declaredContentType, byte[] probeBytes) {

    /**
     * <p>
     * 创建上传内容探测快照。
     * </p>
     */
    public CocoContentProbe {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        extension = Objects.requireNonNull(extension, "extension must not be null");
        probeBytes = Objects.requireNonNull(probeBytes, "probeBytes must not be null").clone();
    }

    /**
     * <p>
     * 返回内容头部探测字节。
     * </p>
     * @return 字节数组副本
     */
    @Override
    public byte[] probeBytes() {
        return this.probeBytes.clone();
    }

    /**
     * <p>
     * 创建探测快照并从对象键推导扩展名。
     * </p>
     * @param key 对象键
     * @param declaredContentType 客户端声明的内容类型
     * @param probeBytes 内容头部探测字节
     * @return 探测快照
     */
    public static CocoContentProbe of(String key, String declaredContentType, byte[] probeBytes) {
        return new CocoContentProbe(key, extension(key), declaredContentType, probeBytes);
    }

    private static String extension(String key) {
        if (key == null) {
            return "";
        }
        String name = key.substring(key.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot <= 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
