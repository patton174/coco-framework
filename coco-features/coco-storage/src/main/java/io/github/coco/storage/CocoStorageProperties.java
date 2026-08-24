package io.github.coco.storage;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

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

    private boolean enabled;

    private LocalProperties local = new LocalProperties();

    private long maxSizeBytes = DEFAULT_MAX_SIZE_BYTES;

    private Set<String> allowedContentTypes = new LinkedHashSet<>();

    private Set<String> allowedExtensions = new LinkedHashSet<>();

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

    /** @param allowedExtensions 允许的扩展名集合，不含点号 */
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

        /** @return 本地对象存储根目录 */
        public Path getRoot() {
            return this.root;
        }

        /** @param root 本地对象存储根目录 */
        public void setRoot(Path root) {
            this.root = root;
        }
    }
}
