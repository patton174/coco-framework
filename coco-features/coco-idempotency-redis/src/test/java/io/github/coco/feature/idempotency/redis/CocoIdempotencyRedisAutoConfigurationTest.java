package io.github.coco.feature.idempotency.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import io.github.coco.feature.idempotency.store.CocoIdempotencyAcquireResult;
import io.github.coco.feature.idempotency.store.CocoIdempotencyLease;
import io.github.coco.feature.idempotency.store.CocoIdempotencyRequest;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStore;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStoredResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class CocoIdempotencyRedisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoIdempotencyRedisAutoConfiguration.class));

    @Test
    void enabledAdapterRegistersStore() {
        this.contextRunner.withPropertyValues(enabled()).withBean(RedisConnectionFactory.class,
                CocoIdempotencyRedisAutoConfigurationTest::connectionFactory).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CocoIdempotencyStore.class);
                    assertThat(context).hasSingleBean(RedisCocoIdempotencyStore.class);
                });
    }

    @Test
    void enabledAdapterFailsFastWithoutConnectionFactory() {
        this.contextRunner.withPropertyValues(enabled()).run(context -> {
            assertThat(context.getStartupFailure()).isNotNull();
            assertThat(context.getStartupFailure()).hasMessageContaining("RedisConnectionFactory");
        });
    }

    @Test
    void userStoreTakesPrecedenceEvenWithoutConnectionFactory() {
        this.contextRunner.withUserConfiguration(UserStoreConfiguration.class).withPropertyValues(enabled())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CocoIdempotencyStore.class);
                    assertThat(context.getBean(CocoIdempotencyStore.class)).isInstanceOf(UserStore.class);
                    assertThat(context).doesNotHaveBean(RedisCocoIdempotencyStore.class);
                });
    }

    @Test
    void coreIdempotencyGateSuppressesRedisAdapter() {
        this.contextRunner.withPropertyValues("coco.idempotency.redis.enabled=true")
                .withBean(RedisConnectionFactory.class, CocoIdempotencyRedisAutoConfigurationTest::connectionFactory)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(RedisCocoIdempotencyStore.class);
                });
    }

    @Test
    void missingRedisClassesDoNotLinkAdapter() {
        this.contextRunner.withClassLoader(new FilteredClassLoader(RedisConnectionFactory.class))
                .withPropertyValues(enabled()).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(RedisCocoIdempotencyStore.class);
                });
    }

    private static String[] enabled() {
        return new String[] { "coco.idempotency.enabled=true", "coco.idempotency.redis.enabled=true" };
    }

    private static RedisConnectionFactory connectionFactory() {
        return new RedisConnectionFactory() {
            @Override public boolean getConvertPipelineAndTxResults() { return false; }
            @Override public RedisConnection getConnection() { throw new AssertionError("not used at startup"); }
            @Override public org.springframework.data.redis.connection.RedisClusterConnection getClusterConnection() { return null; }
            @Override public org.springframework.data.redis.connection.RedisSentinelConnection getSentinelConnection() { return null; }
            @Override public org.springframework.dao.DataAccessException translateExceptionIfPossible(RuntimeException exception) { return null; }
        };
    }

    @Configuration(proxyBeanMethods = false)
    static class UserStoreConfiguration {
        @Bean CocoIdempotencyStore userStore() { return new UserStore(); }
    }

    private static final class UserStore implements CocoIdempotencyStore {
        @Override public CocoIdempotencyAcquireResult acquire(CocoIdempotencyRequest request, Instant now, Instant expiresAt) { return CocoIdempotencyAcquireResult.inProgress(); }
        @Override public boolean complete(CocoIdempotencyLease lease, CocoIdempotencyStoredResponse response, Instant now) { return false; }
        @Override public boolean fail(CocoIdempotencyLease lease, Instant now) { return false; }
    }
}
