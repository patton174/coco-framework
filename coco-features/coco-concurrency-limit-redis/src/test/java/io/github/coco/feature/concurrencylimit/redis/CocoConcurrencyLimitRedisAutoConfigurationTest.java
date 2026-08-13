package io.github.coco.feature.concurrencylimit.redis;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.coco.feature.concurrencylimit.CocoConcurrencyLimitAutoConfiguration;
import io.github.coco.feature.concurrencylimit.CocoConcurrencyLimitStore;
import io.github.coco.feature.concurrencylimit.InMemoryCocoConcurrencyLimitStore;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.config.CocoConfigAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;

class CocoConcurrencyLimitRedisAutoConfigurationTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoConfigAutoConfiguration.class,
                    CocoConcurrencyLimitAutoConfiguration.class,
                    CocoConcurrencyLimitRedisAutoConfiguration.class))
            .withUserConfiguration(WebPrerequisites.class);
    @Test void disabledUsesCoreLocalStore() { this.runner.withUserConfiguration(Factory.class).withPropertyValues("coco.concurrency-limit.enabled=true").run(context -> assertThat(context).hasSingleBean(CocoConcurrencyLimitStore.class).getBean(CocoConcurrencyLimitStore.class).isInstanceOf(InMemoryCocoConcurrencyLimitStore.class)); }
    @Test void enabledFactoryUsesRedisStore() { this.runner.withUserConfiguration(Factory.class).withPropertyValues("coco.concurrency-limit.enabled=true", "coco.concurrency-limit.redis.enabled=true", "coco.concurrency-limit.redis.app-namespace=test").run(context -> assertThat(context).hasSingleBean(CocoConcurrencyLimitStore.class).getBean(CocoConcurrencyLimitStore.class).isInstanceOf(RedisCocoConcurrencyLimitStore.class)); }
    @Test void enabledWithoutFactoryFailsFast() { this.runner.withPropertyValues("coco.concurrency-limit.enabled=true", "coco.concurrency-limit.redis.enabled=true", "coco.concurrency-limit.redis.app-namespace=test").run(context -> assertThat(context).hasFailed()); }
    @Test void customStoreWinsEvenWhenRedisEnabled() { this.runner.withUserConfiguration(Factory.class, CustomStore.class).withPropertyValues("coco.concurrency-limit.enabled=true", "coco.concurrency-limit.redis.enabled=true", "coco.concurrency-limit.redis.app-namespace=test").run(context -> assertThat(context).hasSingleBean(CocoConcurrencyLimitStore.class).getBean(CocoConcurrencyLimitStore.class).isSameAs(context.getBean("customStore"))); }
    @Test
    void multipleCustomStoresRequireAnExplicitPrimaryStore() {
        this.runner.withUserConfiguration(Factory.class, MultipleCustomStores.class)
                .withPropertyValues("coco.concurrency-limit.enabled=true", "coco.concurrency-limit.redis.enabled=true",
                        "coco.concurrency-limit.redis.app-namespace=test")
                .run(context -> {
                    assertThat(context).hasBean("firstCustomStore").hasBean("secondCustomStore");
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(org.assertj.core.api.Assertions
                            .catchThrowable(() -> context.getBean(CocoConcurrencyLimitStore.class)))
                            .isInstanceOf(NoUniqueBeanDefinitionException.class);
                });
    }
    @Test void coreDisabledCreatesNothing() { this.runner.withUserConfiguration(Factory.class).withPropertyValues("coco.concurrency-limit.redis.enabled=true", "coco.concurrency-limit.redis.app-namespace=test").run(context -> assertThat(context).doesNotHaveBean(CocoConcurrencyLimitStore.class)); }
    @Configuration(proxyBeanMethods = false)
    static class Factory {
        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return new StubFactory();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomStore {
        @Bean
        CocoConcurrencyLimitStore customStore() {
            return new InMemoryCocoConcurrencyLimitStore(
                    new io.github.coco.feature.concurrencylimit.CocoConcurrencyLimitProperties());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MultipleCustomStores {
        @Bean
        CocoConcurrencyLimitStore firstCustomStore() {
            return new InMemoryCocoConcurrencyLimitStore(
                    new io.github.coco.feature.concurrencylimit.CocoConcurrencyLimitProperties());
        }

        @Bean
        CocoConcurrencyLimitStore secondCustomStore() {
            return new InMemoryCocoConcurrencyLimitStore(
                    new io.github.coco.feature.concurrencylimit.CocoConcurrencyLimitProperties());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class WebPrerequisites {
        @Bean
        CocoWebRequestMatcher cocoWebRequestMatcher() {
            return (request, rules) -> false;
        }

        @Bean
        CocoWebRequestContextResolver cocoWebRequestContextResolver() {
            return (traceId, request) -> new CocoWebRequestSnapshot(traceId, request.getMethod(),
                    request.getRequestURI(), null, request.getRemoteAddr(), null, null, null, null, null, null,
                    java.util.Map.of(), java.util.Map.of());
        }
    }

    private static final class StubFactory implements RedisConnectionFactory {
        @Override public boolean getConvertPipelineAndTxResults() { return false; }
        @Override public RedisConnection getConnection() { throw new UnsupportedOperationException(); }
        @Override public RedisClusterConnection getClusterConnection() { return null; }
        @Override public RedisSentinelConnection getSentinelConnection() { return null; }
        @Override public DataAccessException translateExceptionIfPossible(RuntimeException exception) { return null; }
    }
}
