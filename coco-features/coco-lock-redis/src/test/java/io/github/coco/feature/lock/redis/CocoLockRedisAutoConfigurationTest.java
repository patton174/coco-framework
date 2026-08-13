package io.github.coco.feature.lock.redis;

import io.github.coco.feature.lock.CocoLockManager;
import io.github.coco.feature.lock.LocalCocoLockManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CocoLockRedisAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CocoLockRedisAutoConfiguration.class));

    @Test
    void registersOnlyWhenBothLockAndRedisAdapterAreEnabled() {
        this.runner.withUserConfiguration(RedisFactory.class).withPropertyValues("coco.lock.redis.enabled=true", "spring.application.name=orders").run(context -> assertThat(context).hasSingleBean(CocoLockManager.class).getBean(CocoLockManager.class).isInstanceOf(RedisCocoLockManager.class));
        this.runner.withUserConfiguration(RedisFactory.class).run(context -> assertThat(context).doesNotHaveBean(CocoLockManager.class));
        this.runner.withUserConfiguration(RedisFactory.class).withPropertyValues("coco.lock.enabled=false", "coco.lock.redis.enabled=true", "spring.application.name=orders").run(context -> assertThat(context).doesNotHaveBean(CocoLockManager.class));
    }

    @Test
    void backsOffForMissingFactoryMissingClassesAndBusinessManager() {
        this.runner.withPropertyValues("coco.lock.redis.enabled=true", "spring.application.name=orders").run(context -> assertThat(context).doesNotHaveBean(CocoLockManager.class));
        this.runner.withClassLoader(new FilteredClassLoader(RedisConnectionFactory.class)).withPropertyValues("coco.lock.redis.enabled=true", "spring.application.name=orders").run(context -> assertThat(context).doesNotHaveBean(CocoLockManager.class));
        this.runner.withUserConfiguration(RedisFactory.class, BusinessManagers.class).withPropertyValues("coco.lock.redis.enabled=true", "spring.application.name=orders").run(context -> assertThat(context).hasBean("firstBusinessManager").hasBean("secondBusinessManager").doesNotHaveBean("redisCocoLockManager"));
    }

    @Test
    void requiresSafeExplicitPrefixOrApplicationName() {
        CocoLockRedisProperties properties = new CocoLockRedisProperties();
        assertThatThrownBy(() -> properties.resolveKeyPrefix(null)).isInstanceOf(IllegalArgumentException.class);
        for (String unsafe : new String[] { "evil{slot}", "evil*", "evil key", "evil\nkey" }) {
            properties.setKeyPrefix(unsafe);
            assertThatThrownBy(() -> properties.resolveKeyPrefix("orders")).isInstanceOf(IllegalArgumentException.class);
        }
        properties.setKeyPrefix("tenant-orders");
        assertThat(properties.resolveKeyPrefix("ignored")).isEqualTo("coco:lock:tenant-orders:");
    }

    @Configuration(proxyBeanMethods = false) static class RedisFactory { @Bean RedisConnectionFactory redisConnectionFactory() { return new StubFactory(); } }
    @Configuration(proxyBeanMethods = false) static class BusinessManagers { @Bean CocoLockManager firstBusinessManager() { return new LocalCocoLockManager(); } @Bean CocoLockManager secondBusinessManager() { return new LocalCocoLockManager(); } }
    static class StubFactory implements RedisConnectionFactory { @Override public boolean getConvertPipelineAndTxResults() { return false; } @Override public RedisConnection getConnection() { throw new AssertionError("not used at startup"); } @Override public RedisClusterConnection getClusterConnection() { return null; } @Override public RedisSentinelConnection getSentinelConnection() { return null; } @Override public DataAccessException translateExceptionIfPossible(RuntimeException e) { return null; } }
}
