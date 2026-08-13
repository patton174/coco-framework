package io.github.coco.feature.lock;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class CocoLockAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CocoLockAutoConfiguration.class);

    @Test
    void suppliesLocalManagerByDefaultAndHonorsEnabledFlagAndUserOverride() {
        this.contextRunner.run(context -> assertThat(context).hasSingleBean(CocoLockManager.class)
                .getBean(CocoLockManager.class).isInstanceOf(LocalCocoLockManager.class));
        this.contextRunner.withPropertyValues("coco.lock.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(CocoLockManager.class));
        this.contextRunner.withBean(CocoLockManager.class, LocalCocoLockManager::new)
                .run(context -> assertThat(context).hasSingleBean(CocoLockManager.class));
    }

    @Test
    void redisWithoutConnectionFactoryFailsFast() {
        this.contextRunner.withPropertyValues("coco.lock.type=redis")
                .run(context -> assertThat(context.getStartupFailure()).isNotNull()
                        .hasMessageContaining("requires a RedisConnectionFactory"));
        this.contextRunner.withPropertyValues("coco.lock.type=redis")
                .withBean(CocoLockManager.class, LocalCocoLockManager::new)
                .run(context -> assertThat(context.getStartupFailure()).isNotNull()
                        .hasMessageContaining("requires a RedisConnectionFactory"));
    }

    @Test
    void invalidConfigurationFailsDuringStartupEvenWithBusinessManager() {
        this.contextRunner.withPropertyValues("coco.lock.default-lease=PT0S")
                .withBean(CocoLockManager.class, LocalCocoLockManager::new)
                .run(context -> assertThat(context.getStartupFailure()).isNotNull()
                        .hasMessageContaining("default-lease must be positive"));
    }

    @Test
    void appliesLockToSpringBeanAndAllowsBusinessManagerOverride() {
        AtomicReference<String> key = new AtomicReference<>();
        this.contextRunner.withUserConfiguration(LockedServiceConfiguration.class)
                .withBean(CocoLockManager.class, () -> recordingManager(key))
                .run(context -> {
                    assertThat(context.getBean(LockedService.class).run("7")).isEqualTo("7");
                    assertThat(key.get()).isEqualTo("service:7");
                });
    }

    private static CocoLockManager recordingManager(AtomicReference<String> key) {
        return new CocoLockManager() {
            @Override
            public Optional<CocoLock> tryLock(String lockKey, Duration waitTime, Duration leaseTime) {
                key.set(lockKey);
                return Optional.of(new CocoLock() {
                    @Override public String key() { return lockKey; }
                    @Override public java.time.Instant acquiredAt() { return java.time.Instant.EPOCH; }
                    @Override public java.time.Instant expiresAt() { return java.time.Instant.EPOCH; }
                    @Override public void close() { }
                });
            }

            @Override public void close() { }
        };
    }

    @Configuration(proxyBeanMethods = false)
    static class LockedServiceConfiguration {
        @Bean LockedService lockedService() { return new LockedService(); }
    }

    static class LockedService {
        @CocoLocked("service:#p0")
        public String run(String id) { return id; }
    }
}
