package io.github.coco.storage;

import java.util.List;
import java.util.Objects;

/**
 * Coco 文件魔数签名。
 * <p>
 * 一个签名由一个或多个 {@link Part} 组成，只有全部片段都在指定偏移量匹配时才认为签名命中，
 * 用于表达 WebP 这类需要同时校验多段魔数的容器格式。
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
 * @param label 人类可读的签名名称，用于日志和异常参数
 * @param parts 组成该签名的魔数片段，全部命中才算匹配
 */
public record CocoFileSignature(String label, List<Part> parts) {

    /**
     * <p>
     * 创建文件魔数签名。
     * </p>
     */
    public CocoFileSignature {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        Objects.requireNonNull(parts, "parts must not be null");
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("parts must not be empty");
        }
        parts = List.copyOf(parts);
    }

    /**
     * <p>
     * 判断探测字节是否命中当前签名。
     * </p>
     * <p>
     * 探测字节长度不足以覆盖任一片段时返回 {@code false}，不会抛出越界异常。
     * </p>
     * @param probe 从上传内容头部读取的探测字节
     * @return 全部片段命中时返回 {@code true}
     */
    public boolean matches(byte[] probe) {
        if (probe == null) {
            return false;
        }
        for (Part part : this.parts) {
            if (!part.matches(probe)) {
                return false;
            }
        }
        return true;
    }

    /**
     * <p>
     * 创建只有一个魔数片段的签名。
     * </p>
     * <p>
     * 魔数以 {@code int} 传入，便于调用方按 {@code 0xFF, 0xD8, 0xFF} 的可读形式书写，内部逐个转换为字节。
     * </p>
     * @param label 签名名称
     * @param offset 魔数在内容中的起始偏移量
     * @param magicBytes 魔数字节，取每个值的低八位
     * @return 单片段签名
     */
    public static CocoFileSignature of(String label, int offset, int... magicBytes) {
        Objects.requireNonNull(magicBytes, "magicBytes must not be null");
        byte[] magic = new byte[magicBytes.length];
        for (int index = 0; index < magicBytes.length; index++) {
            magic[index] = (byte) magicBytes[index];
        }
        return new CocoFileSignature(label, List.of(new Part(offset, magic)));
    }

    /**
     * <p>
     * 魔数片段：内容中某个固定偏移量上应当出现的字节序列。
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
     * @param offset 片段起始偏移量，不能为负数
     * @param magic 片段期望的字节序列，不能为空
     */
    public record Part(int offset, byte[] magic) {

        /**
         * <p>
         * 创建魔数片段。
         * </p>
         */
        public Part {
            if (offset < 0) {
                throw new IllegalArgumentException("offset must not be negative");
            }
            Objects.requireNonNull(magic, "magic must not be null");
            if (magic.length == 0) {
                throw new IllegalArgumentException("magic must not be empty");
            }
            magic = magic.clone();
        }

        /**
         * <p>
         * 返回片段期望的字节序列。
         * </p>
         * @return 字节数组副本
         */
        @Override
        public byte[] magic() {
            return this.magic.clone();
        }

        /**
         * <p>
         * 判断探测字节在当前偏移量上是否与片段一致。
         * </p>
         * @param probe 探测字节
         * @return 一致时返回 {@code true}
         */
        public boolean matches(byte[] probe) {
            if (probe == null || probe.length < this.offset + this.magic.length) {
                return false;
            }
            for (int index = 0; index < this.magic.length; index++) {
                if (probe[this.offset + index] != this.magic[index]) {
                    return false;
                }
            }
            return true;
        }
    }
}
