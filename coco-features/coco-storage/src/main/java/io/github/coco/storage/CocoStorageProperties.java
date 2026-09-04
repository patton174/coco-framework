package io.github.coco.storage;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco 对象存储配置。
 * <p>
 * 本地参考实现仅在 {@code coco.storage.enabled=true} 且没有业务自定义 {@link CocoObjectStorage} Bean 时创建。
 * </p>
 */
@ConfigurationProperties("coco.storage")
public class CocoStorageProperties {

    /** 默认允许的最大上传字节数。 */
    public static final long DEFAULT_MAX_SIZE_BYTES = 10L * 1024L * 1024L;

    /** 默认孤儿 blob 宽限期。 */
    public static final Duration DEFAULT_ORPHAN_GRACE_PERIOD = Duration.ofMinutes(5);

    /** 默认本地孤儿回收间隔。 */
    public static final Duration DEFAULT_GC_INTERVAL = Duration.ofMinutes(5);

    /** 默认允许的扩展名集合。 */
    public static final Set<String> DEFAULT_ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "pdf",
            "txt", "csv");

    /** 默认内容探测字节数。 */
    public static final int DEFAULT_PROBE_SIZE = 512;

    private boolean enabled;

    private LocalProperties local = new LocalProperties();

    @NestedConfigurationProperty
    private ValidationProperties validation = new ValidationProperties();

    private long maxSizeBytes = DEFAULT_MAX_SIZE_BYTES;

    private Set<String> allowedContentTypes = new LinkedHashSet<>();

    private Set<String> allowedExtensions = new LinkedHashSet<>(DEFAULT_ALLOWED_EXTENSIONS);

    private CocoStorageOverwritePolicy overwritePolicy = CocoStorageOverwritePolicy.REJECT;

    /** @return 是否启用存储自动配置 */
    public boolean isEnabled() {
        return this.enabled;
    }

    /** @param enabled 是否启用存储自动配置 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return 本地参考实现配置 */
    public LocalProperties getLocal() {
        return this.local;
    }

    /** @param local 本地参考实现配置 */
    public void setLocal(LocalProperties local) {
        this.local = local == null ? new LocalProperties() : local;
    }

    /** @return 内容校验配置 */
    public ValidationProperties getValidation() {
        return this.validation;
    }

    /** @param validation 内容校验配置 */
    public void setValidation(ValidationProperties validation) {
        this.validation = validation == null ? new ValidationProperties() : validation;
    }

    /** @return 最大上传字节数 */
    public long getMaxSizeBytes() {
        return this.maxSizeBytes;
    }

    /** @param maxSizeBytes 最大上传字节数，必须大于零 */
    public void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    /** @return 允许的内容类型集合；为空时不限制 */
    public Set<String> getAllowedContentTypes() {
        return this.allowedContentTypes;
    }

    /** @param allowedContentTypes 允许的内容类型集合 */
    public void setAllowedContentTypes(Set<String> allowedContentTypes) {
        this.allowedContentTypes = copy(allowedContentTypes);
    }

    /** @return 允许的扩展名集合；为空时不限制 */
    public Set<String> getAllowedExtensions() {
        return this.allowedExtensions;
    }

    /**
     * <p>
     * 设置允许的扩展名集合。
     * </p>
     * <p>
     * 默认集合为 {@link #DEFAULT_ALLOWED_EXTENSIONS}，以下扩展名被有意排除：
     * </p>
     * <ul>
     *   <li>{@code docx}、{@code xlsx}、{@code pptx}：基于 ZIP 容器，魔数与普通压缩包完全相同，无法区分实际内容</li>
     *   <li>{@code zip}：任意内容的容器，且存在解压炸弹风险</li>
     *   <li>{@code svg}：可内嵌 JavaScript，是 XSS 载体</li>
     * </ul>
     * <p>
     * 业务方确实需要这些类型时，应在放开白名单的同时自行补充容器内容解析或渲染隔离措施。
     * </p>
     * @param allowedExtensions 允许的扩展名集合，不含点号
     */
    public void setAllowedExtensions(Set<String> allowedExtensions) {
        this.allowedExtensions = copy(allowedExtensions);
    }

    /** @return 默认覆盖策略 */
    public CocoStorageOverwritePolicy getOverwritePolicy() {
        return this.overwritePolicy;
    }

    /** @param overwritePolicy 默认覆盖策略 */
    public void setOverwritePolicy(CocoStorageOverwritePolicy overwritePolicy) {
        this.overwritePolicy = overwritePolicy == null ? CocoStorageOverwritePolicy.REJECT : overwritePolicy;
    }

    private static Set<String> copy(Set<String> values) {
        return values == null ? new LinkedHashSet<>() : new LinkedHashSet<>(values);
    }

    /**
     * 本地文件参考实现配置。
     */
    public static class LocalProperties {

        private Path root;

        private Duration orphanGracePeriod = DEFAULT_ORPHAN_GRACE_PERIOD;

        private Duration gcInterval = DEFAULT_GC_INTERVAL;

        /** @return 本地对象存储根目录 */
        public Path getRoot() {
            return this.root;
        }

        /** @param root 本地对象存储根目录 */
        public void setRoot(Path root) {
            this.root = root;
        }

        /** @return 不再被 manifest 引用的内部文件回收宽限期 */
        public Duration getOrphanGracePeriod() {
            return this.orphanGracePeriod;
        }

        /** @param orphanGracePeriod 孤儿文件回收宽限期，不能为负数 */
        public void setOrphanGracePeriod(Duration orphanGracePeriod) {
            this.orphanGracePeriod = orphanGracePeriod == null ? DEFAULT_ORPHAN_GRACE_PERIOD : orphanGracePeriod;
        }

        /** @return 后台孤儿回收间隔；{@code ZERO} 表示仅在启动和关闭时回收 */
        public Duration getGcInterval() {
            return this.gcInterval;
        }

        /** @param gcInterval 后台孤儿回收间隔，不能为负数 */
        public void setGcInterval(Duration gcInterval) {
            this.gcInterval = gcInterval == null ? DEFAULT_GC_INTERVAL : gcInterval;
        }
    }

    /**
     * 上传内容校验配置。
     * <p>
     * 校验默认开启：文件上传是常见的攻击入口，安全默认值比省事的默认值更重要。
     * </p>
     */
    public static class ValidationProperties {

        private boolean enabled = true;

        private int probeSize = DEFAULT_PROBE_SIZE;

        private boolean rejectDangerousSignatures = true;

        private boolean requireSignatureMatch = true;

        /** @return 是否启用上传内容校验装饰器 */
        public boolean isEnabled() {
            return this.enabled;
        }

        /** @param enabled 是否启用上传内容校验装饰器 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** @return 内容头部探测字节数 */
        public int getProbeSize() {
            return this.probeSize;
        }

        /** @param probeSize 内容头部探测字节数，非正数时回退到默认值 */
        public void setProbeSize(int probeSize) {
            this.probeSize = probeSize <= 0 ? DEFAULT_PROBE_SIZE : probeSize;
        }

        /** @return 是否拒绝命中危险签名的内容 */
        public boolean isRejectDangerousSignatures() {
            return this.rejectDangerousSignatures;
        }

        /** @param rejectDangerousSignatures 是否拒绝命中危险签名的内容 */
        public void setRejectDangerousSignatures(boolean rejectDangerousSignatures) {
            this.rejectDangerousSignatures = rejectDangerousSignatures;
        }

        /** @return 是否要求内容魔数与扩展名一致 */
        public boolean isRequireSignatureMatch() {
            return this.requireSignatureMatch;
        }

        /** @param requireSignatureMatch 是否要求内容魔数与扩展名一致 */
        public void setRequireSignatureMatch(boolean requireSignatureMatch) {
            this.requireSignatureMatch = requireSignatureMatch;
        }
    }
}
