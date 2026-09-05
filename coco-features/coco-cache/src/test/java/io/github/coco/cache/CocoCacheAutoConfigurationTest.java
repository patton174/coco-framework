package io.github.coco.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

class CocoCacheAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoCacheRedisAutoConfiguration.class,
                    CocoCacheRedisMissingDependencyAutoConfiguration.class, CocoCacheAutoConfiguration.class));

    @Test
    void disabledByDefaultRegistersNothing() {
        this.runner.run(context -> assertThat(context).doesNotHaveBean(CocoCacheManager.class));
    }

    @Test
    void localTopologyWiresManagerWithoutL2OrPublisher() {
        this.runner.withPropertyValues("coco.cache.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(CocoCacheManager.class);
            assertThat(context).doesNotHaveBean(CocoCacheL2Store.class);
            assertThat(context).doesNotHaveBean(CocoCacheInvalidationPublisher.class);
        });
    }

    @Test
    void twoLevelTopologyWiresL2AndPublisher() {
        // The real pub/sub container is a SmartLifecycle that opens a live subscription on
        // startup. Supplying our own container backs off the auto-configured one (it is
        // @ConditionalOnMissingBean), so this test verifies L2/publisher/manager wiring
        // without needing a running Redis. The listener logic itself is covered separately.
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        this.runner.withPropertyValues("coco.cache.enabled=true", "coco.cache.store-type=two-level")
                .withBean(RedisConnectionFactory.class, () -> connectionFactory)
                .withBean(StringRedisTemplate.class, () -> new StringRedisTemplate(connectionFactory))
                .withBean(RedisMessageListenerContainer.class, () -> {
                    RedisMessageListenerContainer container = new RedisMessageListenerContainer() {
                        @Override
                        public void start() {
                            // no-op: skip opening a real subscription in the wiring test
                        }
                    };
                    container.setConnectionFactory(connectionFactory);
                    return container;
                })
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoCacheL2Store.class);
                    assertThat(context).hasSingleBean(CocoCacheInvalidationPublisher.class);
                    assertThat(context).hasSingleBean(CocoCacheManager.class);
                });
    }

    @Test
    void twoLevelFailsClosedWhenRedisIsNotOnTheClasspath() {
        this.runner.withPropertyValues("coco.cache.enabled=true", "coco.cache.store-type=two-level")
                .withClassLoader(new FilteredClassLoader(StringRedisTemplate.class))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void customCacheManagerBacksOffTheDefault() {
        this.runner.withPropertyValues("coco.cache.enabled=true")
                .withBean(CacheManager.class, () -> new org.springframework.cache.concurrent.ConcurrentMapCacheManager())
                .run(context -> assertThat(context).doesNotHaveBean(CocoCacheManager.class));
    }
}
