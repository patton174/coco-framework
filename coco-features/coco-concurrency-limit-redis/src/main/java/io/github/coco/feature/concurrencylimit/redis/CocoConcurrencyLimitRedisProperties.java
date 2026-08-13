package io.github.coco.feature.concurrencylimit.redis;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Redis 分布式并发许可存储配置。 */
@ConfigurationProperties(CocoConcurrencyLimitRedisProperties.PROPERTY_PREFIX)
public class CocoConcurrencyLimitRedisProperties {
    /** 配置前缀。 */
    public static final String PROPERTY_PREFIX = "coco.concurrency-limit.redis";
    private static final Pattern SAFE_NAMESPACE = Pattern.compile("[A-Za-z0-9._:-]{1,96}");
    private boolean enabled;
    private String keyPrefix = "coco:concurrency-limit:";
    private String appNamespace;
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration renewInterval = Duration.ofSeconds(10);
    public boolean isEnabled() { return this.enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getKeyPrefix() { return this.keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    public String getAppNamespace() { return this.appNamespace; }
    public void setAppNamespace(String appNamespace) { this.appNamespace = appNamespace; }
    public Duration getLeaseDuration() { return this.leaseDuration; }
    public void setLeaseDuration(Duration leaseDuration) { this.leaseDuration = leaseDuration; }
    public Duration getRenewInterval() { return this.renewInterval; }
    public void setRenewInterval(Duration renewInterval) { this.renewInterval = renewInterval; }
    /** 严格校验不会进入 Redis key 的命名空间与时间参数。 */
    public void validate(String springApplicationName) {
        String prefix = Objects.requireNonNull(this.keyPrefix, "coco.concurrency-limit.redis.key-prefix must not be null");
        if (!prefix.matches("[A-Za-z0-9:_-]{1,96}") || !prefix.endsWith(":")) throw new IllegalArgumentException("coco.concurrency-limit.redis.key-prefix must be safe and end in ':'");
        String namespace = this.appNamespace == null || this.appNamespace.isBlank() ? springApplicationName : this.appNamespace;
        if (namespace == null || !SAFE_NAMESPACE.matcher(namespace).matches()) throw new IllegalArgumentException("coco.concurrency-limit.redis.app-namespace or spring.application.name must be safe and nonblank");
        if (this.leaseDuration == null || this.leaseDuration.isNegative() || this.leaseDuration.isZero() || this.leaseDuration.toMillis() > 3_600_000L) throw new IllegalArgumentException("coco.concurrency-limit.redis.lease-duration must be between 1ms and 1h");
        if (this.renewInterval == null || this.renewInterval.isNegative() || this.renewInterval.isZero() || this.renewInterval.compareTo(this.leaseDuration.dividedBy(2)) >= 0) throw new IllegalArgumentException("coco.concurrency-limit.redis.renew-interval must be positive and less than half the lease-duration");
    }
}
