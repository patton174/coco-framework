package io.github.coco.feature.storage;

/** 对象状态；不存在对象使用 {@link #notFound(String)} 表示。 */
public record CocoObjectStat(boolean found, CocoObjectMetadata metadata) {
    public CocoObjectStat { if (found && metadata == null) throw new IllegalArgumentException("metadata is required"); }
    public static CocoObjectStat found(CocoObjectMetadata metadata) { return new CocoObjectStat(true, metadata); }
    public static CocoObjectStat notFound(String key) { CocoObjectKey.validate(key); return new CocoObjectStat(false, null); }
}
