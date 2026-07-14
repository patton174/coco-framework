package io.github.coco.feature.replay.redis;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.feature.web.CocoWebAutoConfiguration;
import io.github.coco.feature.web.replay.CocoReplayStore;
import io.github.coco.feature.web.replay.InMemoryCocoReplayStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;

class CocoReplayRedisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoReplayRedisAutoConfiguration.class,
                    CocoWebAutoConfiguration.class));

    @Test
    void enabledRedisAdapterRegistersBeforeDefaultMemoryStore() {
        this.contextRunner
                .withPropertyValues("coco.web.replay.redis.enabled=true")
                .withBean(RedisConnectionFactory.class, CocoReplayRedisAutoConfigurationTest::connectionFactory)
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoReplayStore.class);
                    assertThat(context).hasSingleBean(RedisCocoReplayStore.class);
                    assertThat(context).doesNotHaveBean(InMemoryCocoReplayStore.class);
                });
    }

    @Test
    void backsOffForUserProvidedReplayStore() {
        CocoReplayStore customStore = (key, expiresAt) -> true;
        this.contextRunner
                .withPropertyValues("coco.web.replay.redis.enabled=true")
                .withBean(RedisConnectionFactory.class, CocoReplayRedisAutoConfigurationTest::connectionFactory)
                .withBean(CocoReplayStore.class, () -> customStore)
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoReplayStore.class);
                    assertThat(context.getBean(CocoReplayStore.class)).isSameAs(customStore);
                    assertThat(context).doesNotHaveBean(RedisCocoReplayStore.class);
                    assertThat(context).doesNotHaveBean(InMemoryCocoReplayStore.class);
        });
    }

    @Test
    void missingConnectionFactoryLeavesDefaultMemoryStoreUntouched() {
        this.contextRunner
                .withPropertyValues("coco.web.replay.redis.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(RedisCocoReplayStore.class);
                    assertThat(context).hasSingleBean(InMemoryCocoReplayStore.class);
                });
    }

    @Test
    void disabledAdapterLeavesDefaultStoreSelectionUntouched() {
        this.contextRunner
                .withBean(RedisConnectionFactory.class, CocoReplayRedisAutoConfigurationTest::connectionFactory)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RedisCocoReplayStore.class);
                    assertThat(context).hasSingleBean(InMemoryCocoReplayStore.class);
                });
    }

    @Test
    void disabledReplayDoesNotCreateRedisStore() {
        this.contextRunner
                .withPropertyValues(
                        "coco.web.replay.enabled=false",
                        "coco.web.replay.redis.enabled=true")
                .withBean(RedisConnectionFactory.class, CocoReplayRedisAutoConfigurationTest::connectionFactory)
                .run(context -> assertThat(context).doesNotHaveBean(RedisCocoReplayStore.class));
    }

    @Test
    void missingSpringDataRedisDoesNotAttemptRedisAutoConfiguration() {
        this.contextRunner
                .withClassLoader(new FilteredClassLoader(RedisConnectionFactory.class))
                .withPropertyValues("coco.web.replay.redis.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(RedisCocoReplayStore.class);
                    assertThat(context).hasSingleBean(InMemoryCocoReplayStore.class);
                });
    }

    @Test
    void customStoreStillStartsWhenSpringDataRedisIsAbsent() {
        CocoReplayStore customStore = (key, expiresAt) -> true;
        this.contextRunner
                .withClassLoader(new FilteredClassLoader(RedisConnectionFactory.class))
                .withPropertyValues("coco.web.replay.redis.enabled=true")
                .withBean(CocoReplayStore.class, () -> customStore)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CocoReplayStore.class);
                    assertThat(context.getBean(CocoReplayStore.class)).isSameAs(customStore);
                    assertThat(context).doesNotHaveBean(RedisCocoReplayStore.class);
                });
    }

    private static RedisConnectionFactory connectionFactory() {
        return new RedisConnectionFactory() {

            @Override
            public RedisConnection getConnection() {
                throw new AssertionError("Auto-configuration must not reserve a key during startup");
            }

            @Override
            public boolean getConvertPipelineAndTxResults() {
                return false;
            }

            @Override
            public RedisClusterConnection getClusterConnection() {
                throw new AssertionError("Cluster connection must not be requested during startup");
            }

            @Override
            public RedisSentinelConnection getSentinelConnection() {
                throw new AssertionError("Sentinel connection must not be requested during startup");
            }

            @Override
            public DataAccessException translateExceptionIfPossible(RuntimeException exception) {
                return null;
            }
        };
    }
}
