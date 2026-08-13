package io.github.coco.feature.lock.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.coco.feature.lock.CocoLockAutoConfiguration;
import io.github.coco.feature.lock.CocoLock;
import io.github.coco.feature.lock.CocoLocked;
import io.github.coco.feature.lock.CocoLockManager;
import io.github.coco.feature.lock.LocalCocoLockManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CocoLockRedisAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoLockRedisAutoConfiguration.class,
                    CocoLockAutoConfiguration.class, CocoLockRedisInfrastructureAutoConfiguration.class));

    @Test
    void redisDisabledUsesLocalManager() {
        this.runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(CocoLockManager.class).hasBean("cocoLockAdvisor");
            assertThat(context.getBean(CocoLockManager.class)).isInstanceOf(LocalCocoLockManager.class);
        });
    }

    @Test
    void redisEnabledWithFactoryUsesRedisManager() {
        this.runner.withUserConfiguration(RedisFactoryConfiguration.class)
                .withPropertyValues("coco.lock.redis.enabled=true", "spring.application.name=orders")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(CocoLockManager.class)
                            .hasBean("cocoLockAdvisor");
                    assertThat(context.getBean(CocoLockManager.class)).isInstanceOf(RedisCocoLockManager.class);
                });
    }

    @Test
    void redisEnabledWithoutFactoryFailsFastWithoutLocalFallback() {
        this.runner.withPropertyValues("coco.lock.redis.enabled=true", "spring.application.name=orders")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("RedisConnectionFactory"));
    }

    @Test
    void redisEnabledWithoutSpringDataRedisFailsFastWithoutLoadingMissingClass() {
        this.runner.withClassLoader(new FilteredClassLoader(RedisConnectionFactory.class))
                .withPropertyValues("coco.lock.redis.enabled=true", "spring.application.name=orders")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("Spring Data Redis"));
    }

    @Test
    void businessManagerOverridesRedisAndDoesNotRequireInfrastructure() {
        this.runner.withClassLoader(new FilteredClassLoader(RedisConnectionFactory.class))
                .withUserConfiguration(SingleBusinessManagerConfiguration.class)
                .withPropertyValues("coco.lock.redis.enabled=true", "spring.application.name=orders")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(CocoLockManager.class)
                            .hasBean("cocoLockAdvisor").doesNotHaveBean("redisCocoLockManager")
                            .doesNotHaveBean("cocoLockRedisInfrastructureValidation");
                    assertThat(context.getBean(CocoLockManager.class)).isInstanceOf(LocalCocoLockManager.class);
                });
    }

    @Test
    void twoBusinessManagersWithoutPrimaryFailDeterministicallyInFullContext() {
        this.runner.withUserConfiguration(BusinessManagersConfiguration.class)
                .withPropertyValues("coco.lock.redis.enabled=true", "spring.application.name=orders")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasMessageContaining("Coco lock manager selection is ambiguous")
                        .hasMessageContaining("firstBusinessManager(")
                        .hasMessageContaining("secondBusinessManager(")
                        .hasMessageContaining(CocoLockManager.class.getName())
                        .hasMessageNotContaining("manager-secret"));
    }

    @Test
    void uniquePrimaryBusinessManagerIsUsedByAdvisorInFullContext() {
        this.runner.withUserConfiguration(PrimaryBusinessManagerConfiguration.class)
                .withPropertyValues("coco.lock.redis.enabled=true", "spring.application.name=orders")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasBean("cocoLockAdvisor")
                            .doesNotHaveBean("redisCocoLockManager");
                    assertThat(context.getBean(LockedService.class).run()).isEqualTo("done");
                    assertThat(context.getBean("primaryBusinessManager", RecordingCocoLockManager.class).acquisitions())
                            .isOne();
                    assertThat(context.getBean("secondaryBusinessManager", RecordingCocoLockManager.class).acquisitions())
                            .isZero();
                });
    }

    @Test
    void multiplePrimaryBusinessManagersFailDeterministicallyInFullContext() {
        this.runner.withUserConfiguration(MultiplePrimaryBusinessManagersConfiguration.class)
                .withPropertyValues("coco.lock.redis.enabled=true", "spring.application.name=orders")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasMessageContaining("Coco lock manager selection is ambiguous")
                        .hasMessageContaining("firstPrimaryBusinessManager(")
                        .hasMessageContaining("secondPrimaryBusinessManager(")
                        .hasMessageNotContaining("manager-secret"));
    }

    @Test
    void requiresSafeExplicitPrefixOrApplicationName() {
        CocoLockRedisProperties properties = new CocoLockRedisProperties();
        assertThatThrownBy(() -> properties.resolveKeyPrefix(null)).isInstanceOf(IllegalArgumentException.class);
        for (String unsafe : new String[] { "evil{slot}", "evil*", "evil key", "evil\nkey" }) {
            properties.setKeyPrefix(unsafe);
            assertThatThrownBy(() -> properties.resolveKeyPrefix("orders"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        properties.setKeyPrefix("tenant-orders");
        assertThat(properties.resolveKeyPrefix("ignored")).isEqualTo("coco:lock:tenant-orders:");
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisFactoryConfiguration {
        @Bean RedisConnectionFactory redisConnectionFactory() { return new StubFactory(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class SingleBusinessManagerConfiguration {
        @Bean CocoLockManager businessManager() { return new LocalCocoLockManager(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class BusinessManagersConfiguration {
        @Bean CocoLockManager firstBusinessManager() { return new RecordingCocoLockManager(); }
        @Bean CocoLockManager secondBusinessManager() { return new RecordingCocoLockManager(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class PrimaryBusinessManagerConfiguration {
        @Bean LockedService lockedService() { return new LockedService(); }
        @Bean CocoLockManager secondaryBusinessManager() { return new RecordingCocoLockManager(); }
        @Bean @Primary CocoLockManager primaryBusinessManager() { return new RecordingCocoLockManager(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class MultiplePrimaryBusinessManagersConfiguration {
        @Bean @Primary CocoLockManager firstPrimaryBusinessManager() { return new RecordingCocoLockManager(); }
        @Bean @Primary CocoLockManager secondPrimaryBusinessManager() { return new RecordingCocoLockManager(); }
    }

    static class StubFactory implements RedisConnectionFactory {
        @Override public boolean getConvertPipelineAndTxResults() { return false; }
        @Override public RedisConnection getConnection() { throw new AssertionError("not used at startup"); }
        @Override public RedisClusterConnection getClusterConnection() { return null; }
        @Override public RedisSentinelConnection getSentinelConnection() { return null; }
        @Override public DataAccessException translateExceptionIfPossible(RuntimeException exception) { return null; }
    }

    static class LockedService {
        @CocoLocked("primary-selection")
        String run() { return "done"; }
    }

    static class RecordingCocoLockManager implements CocoLockManager {
        private final AtomicInteger acquisitions = new AtomicInteger();

        @Override
        public Optional<CocoLock> tryLock(String key, Duration waitTime, Duration leaseTime) {
            this.acquisitions.incrementAndGet();
            return Optional.of(new CocoLock() {
                @Override public String key() { return key; }
                @Override public Instant acquiredAt() { return Instant.EPOCH; }
                @Override public Instant expiresAt() { return Instant.EPOCH.plus(leaseTime); }
                @Override public void close() { }
            });
        }

        int acquisitions() { return this.acquisitions.get(); }
        @Override public void close() { }
        @Override public String toString() { return "manager-secret"; }
    }
}
