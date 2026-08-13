package io.github.coco.feature.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Coco 本地缓存配置属性。
 * <p>
 * {@code allow-null-values} 决定 Spring Cache 是否缓存 {@code null} 返回值。{@code maximum-size} 和
 * {@code expire-after-write} 仅在 Caffeine 位于运行时类路径时生效，未引入 Caffeine 时显式设置二者会导致启动失败。
 * </p>
 */
@Validated
@ConfigurationProperties("coco.cache")
public class CocoCacheProperties {

    /** 是否启用 Coco 本地缓存默认配置。 */
    private boolean enabled = true;

    /** 显式缓存名称；为空时允许按 Spring Cache 请求动态创建。 */
    private List<String> cacheNames = new ArrayList<>();

    /** Caffeine 单个缓存的最大条目数；达到上限后 Caffeine 可驱逐较少使用的条目。 */
    @Positive
    private Long maximumSize;

    /** Caffeine 条目写入后的存活时间；到期后读取不会返回该条目。 */
    private Duration expireAfterWrite;

    /** 是否缓存 {@code null} 值，默认允许。 */
    private boolean allowNullValues = true;

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getCacheNames() {
        return this.cacheNames;
    }

    public void setCacheNames(List<String> cacheNames) {
        this.cacheNames = cacheNames == null ? new ArrayList<>() : new ArrayList<>(cacheNames);
    }

    public Long getMaximumSize() {
        return this.maximumSize;
    }

    public void setMaximumSize(Long maximumSize) {
        this.maximumSize = maximumSize;
    }

    public Duration getExpireAfterWrite() {
        return this.expireAfterWrite;
    }

    public void setExpireAfterWrite(Duration expireAfterWrite) {
        this.expireAfterWrite = expireAfterWrite;
    }

    /**
     * 验证写入后过期时间为正数。
     * @return 未配置或配置为正时长时返回 {@code true}
     */
    @AssertTrue(message = "coco.cache.expire-after-write must be positive")
    public boolean isExpireAfterWritePositive() {
        return this.expireAfterWrite == null || !this.expireAfterWrite.isZero() && !this.expireAfterWrite.isNegative();
    }

    public boolean isAllowNullValues() {
        return this.allowNullValues;
    }

    public void setAllowNullValues(boolean allowNullValues) {
        this.allowNullValues = allowNullValues;
    }
}
