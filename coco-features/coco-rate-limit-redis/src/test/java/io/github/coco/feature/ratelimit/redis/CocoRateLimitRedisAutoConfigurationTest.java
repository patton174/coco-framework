package io.github.coco.feature.ratelimit.redis;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.coco.feature.ratelimit.CocoRateLimitAutoConfiguration;
import io.github.coco.feature.ratelimit.CocoRateLimitDecision;
import io.github.coco.feature.ratelimit.CocoRateLimitStore;
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

class CocoRateLimitRedisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoRateLimitRedisAutoConfiguration.class));

    @Test
    void createsRedisStoreWhenRedisAdapterAndConnectionFactoryAreAvailable() {
        this.contextRunner
                .withUserConfiguration(RedisFactoryConfiguration.class)
                .withPropertyValues("coco.rate-limit.redis.enabled=true",
                        "coco.rate-limit.redis.key-prefix=company:edge")
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoRateLimitStore.class)
                            .hasSingleBean(CocoRateLimitRedisProperties.class);
                    assertThat(context.getBean(CocoRateLimitStore.class))
                            .isInstanceOf(RedisCocoRateLimitStore.class);
                    assertThat(context.getBean(CocoRateLimitRedisProperties.class).getKeyPrefix())
                            .isEqualTo("company:edge");
                });
    }

    @Test
    void failsFastWithoutConnectionFactoryAndBacksOffWhenRedisAdapterIsDisabled() {
        this.contextRunner
                .withPropertyValues("coco.rate-limit.redis.enabled=true")
                .run(context -> assertThat(context).hasFailed());

        this.contextRunner
                .withUserConfiguration(RedisFactoryConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(CocoRateLimitStore.class));
    }

    @Test
    void doesNotCreateRedisStoreWhenWebFeatureIsDisabled() {
        this.contextRunner
                .withUserConfiguration(RedisFactoryConfiguration.class)
                .withPropertyValues("coco.rate-limit.redis.enabled=true", "coco.features.disabled[0]=web")
                .run(context -> assertThat(context).doesNotHaveBean(CocoRateLimitStore.class));
    }

    @Test
    void doesNotLoadWhenSpringDataRedisIsAbsent() {
        this.contextRunner
                .withClassLoader(new FilteredClassLoader(RedisConnectionFactory.class))
                .withPropertyValues("coco.rate-limit.redis.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(CocoRateLimitStore.class));
    }

    @Test
    void winsBeforeMainAutoConfigurationAndPreventsInMemoryFallback() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CocoRateLimitAutoConfiguration.class,
                        CocoRateLimitRedisAutoConfiguration.class))
                .withUserConfiguration(RedisFactoryConfiguration.class)
                .withPropertyValues("coco.rate-limit.enabled=true", "coco.rate-limit.redis.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoRateLimitStore.class);
                    assertThat(context.getBean(CocoRateLimitStore.class))
                            .isInstanceOf(RedisCocoRateLimitStore.class);
                });
    }

    @Test
    void customStoreBacksOffBothRedisAndInMemoryDefaults() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CocoRateLimitAutoConfiguration.class,
                        CocoRateLimitRedisAutoConfiguration.class))
                .withUserConfiguration(RedisFactoryConfiguration.class, CustomStoreConfiguration.class)
                .withPropertyValues("coco.rate-limit.enabled=true", "coco.rate-limit.redis.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoRateLimitStore.class);
                    assertThat(context.getBean(CocoRateLimitStore.class))
                            .isSameAs(context.getBean("customRateLimitStore"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisFactoryConfiguration {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return new StubRedisConnectionFactory();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomStoreConfiguration {

        @Bean
        CocoRateLimitStore customRateLimitStore() {
            return permit -> new CocoRateLimitDecision(true, permit.limit(), permit.limit() - 1,
                    permit.resetAt(), false);
        }
    }

    private static final class StubRedisConnectionFactory implements RedisConnectionFactory {

        @Override
        public boolean getConvertPipelineAndTxResults() {
            return false;
        }

        @Override
        public RedisConnection getConnection() {
            throw new UnsupportedOperationException("Connection access is not expected during auto-configuration");
        }

        @Override
        public RedisClusterConnection getClusterConnection() {
            return null;
        }

        @Override
        public RedisSentinelConnection getSentinelConnection() {
            return null;
        }

        @Override
        public DataAccessException translateExceptionIfPossible(RuntimeException exception) {
            return null;
        }
    }
}
