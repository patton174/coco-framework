package io.github.coco.feature.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.coco.feature.cache.CocoCacheAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.SimpleCacheResolver;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/** Coco Redis 缓存自动配置测试。 */
class CocoCacheRedisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoCacheRedisAutoConfiguration.class));

    @Test
    void doesNotRegisterWhenDisabledOrConnectionFactoryIsMissing() {
        this.contextRunner.withUserConfiguration(ConnectionFactoryConfiguration.class).run(context ->
                assertThat(context).doesNotHaveBean(CacheManager.class));
        this.contextRunner.withPropertyValues("coco.cache.redis.enabled=true").run(context ->
                assertThat(context).doesNotHaveBean(CacheManager.class));
        this.contextRunner.withClassLoader(new FilteredClassLoader(RedisConnectionFactory.class))
                .withPropertyValues("coco.cache.redis.enabled=true")
                .withUserConfiguration(ConnectionFactoryConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(CacheManager.class));
    }

    @Test
    void redisManagerOverridesTheBaseLocalManagerAndEnablesDynamicCaches() {
        this.contextRunner
                .withConfiguration(AutoConfigurations.of(CocoCacheRedisAutoConfiguration.class,
                        CocoCacheAutoConfiguration.class))
                .withUserConfiguration(ConnectionFactoryConfiguration.class)
                .withPropertyValues("coco.cache.redis.enabled=true")
                .run(context -> {
                    RedisCacheManager manager = context.getBean(RedisCacheManager.class);
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(manager.isTransactionAware()).isFalse();
                    assertThat(manager.isAllowRuntimeCacheCreation()).isTrue();
                    assertThat(manager.getCache("dynamic")).isInstanceOf(RedisCache.class);
                });
    }

    @Test
    void backsOffForApplicationCacheManagerOrCacheResolver() {
        this.contextRunner.withUserConfiguration(ConnectionFactoryConfiguration.class, ApplicationCacheManagerConfiguration.class)
                .withPropertyValues("coco.cache.redis.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(CacheManager.class)
                        .doesNotHaveBean(RedisCacheManager.class));
        this.contextRunner.withUserConfiguration(ConnectionFactoryConfiguration.class, CacheResolverConfiguration.class)
                .withPropertyValues("coco.cache.redis.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(CacheManager.class)
                        .doesNotHaveBean(RedisCacheManager.class));
    }

    @Test
    void appliesDefaultsAndExplicitCacheNames() {
        this.contextRunner.withUserConfiguration(ConnectionFactoryConfiguration.class)
                .withPropertyValues("coco.cache.redis.enabled=true", "coco.cache.redis.cache-names=orders,products")
                .run(context -> {
                    RedisCacheManager manager = context.getBean(RedisCacheManager.class);
                    RedisCacheConfiguration configuration = ((RedisCache) manager.getCache("orders")).getCacheConfiguration();
                    assertThat(manager.isAllowRuntimeCacheCreation()).isFalse();
                    assertThat(configuration.getTtlFunction().getTimeToLive("orders", null)).isEqualTo(Duration.ofMinutes(30));
                    assertThat(configuration.getKeyPrefixFor("orders")).isEqualTo("coco:orders::");
                    assertThat(configuration.getAllowCacheNullValues()).isFalse();
                    assertThat(configuration.getKeySerializationPair().read(ByteBuffer.wrap("key".getBytes(StandardCharsets.UTF_8))))
                            .isEqualTo("key");
                    Object value = configuration.getValueSerializationPair().read(
                            ByteBuffer.wrap(new GenericJackson2JsonRedisSerializer().serialize("value")));
                    assertThat(value).isEqualTo("value");
                    assertThat(manager.getCache("missing")).isNull();
                    assertThatThrownBy(() -> manager.getCache("orders").put("null", null))
                            .isInstanceOf(IllegalArgumentException.class);
                });
    }

    @Test
    void appliesRedisPropertiesAndCustomConfiguration() {
        this.contextRunner.withUserConfiguration(ConnectionFactoryConfiguration.class)
                .withPropertyValues("coco.cache.redis.enabled=true", "coco.cache.redis.time-to-live=15s",
                        "coco.cache.redis.key-prefix=business:", "coco.cache.redis.allow-null-values=true")
                .run(context -> {
                    RedisCacheConfiguration configuration = context.getBean(RedisCacheManager.class)
                            .getCache("orders") instanceof RedisCache cache ? cache.getCacheConfiguration() : null;
                    assertThat(configuration.getTtlFunction().getTimeToLive("orders", null)).isEqualTo(Duration.ofSeconds(15));
                    assertThat(configuration.getKeyPrefixFor("orders")).isEqualTo("business:orders::");
                    assertThat(configuration.getAllowCacheNullValues()).isTrue();
                });
        this.contextRunner.withUserConfiguration(ConnectionFactoryConfiguration.class, CustomConfiguration.class)
                .withPropertyValues("coco.cache.redis.enabled=true", "coco.cache.redis.cache-names=orders")
                .run(context -> {
                    RedisCacheConfiguration configuration = context.getBean(RedisCacheManager.class)
                            .getCache("orders") instanceof RedisCache cache ? cache.getCacheConfiguration() : null;
                    assertThat(configuration.getKeyPrefixFor("orders")).isEqualTo("custom:orders::");
                    assertThat(configuration.getTtlFunction().getTimeToLive("orders", null)).isEqualTo(Duration.ofSeconds(7));
                });
    }

    @Test
    void validatesUnsafeRedisConfiguration() {
        assertInvalid("coco.cache.redis.time-to-live=0s", "time-to-live");
        assertInvalid("coco.cache.redis.key-prefix= ", "key-prefix");
        assertInvalid("coco.cache.redis.cache-names=orders,orders", "cache-names");
        assertInvalid("coco.cache.redis.cache-names=orders,\u0001", "cache-names");
        this.contextRunner.withUserConfiguration(ConnectionFactoryConfiguration.class)
                .withPropertyValues("coco.cache.redis.enabled=true", "coco.cache.redis.use-key-prefix=false")
                .run(context -> {
                    RedisCache cache = (RedisCache) context.getBean(CacheManager.class).getCache("orders");
                    assertThat(cache.getCacheConfiguration().usePrefix()).isFalse();
                });
        assertInvalid("coco.cache.redis.use-key-prefix=false", "coco.cache.redis.key-prefix=coco:",
                "key-prefix cannot be set");
    }

    @Test
    void propagatesRedisAccessFailureWithoutLocalFallback() {
        this.contextRunner.withUserConfiguration(FailingConnectionFactoryConfiguration.class)
                .withPropertyValues("coco.cache.redis.enabled=true")
                .run(context -> {
                    Cache cache = context.getBean(CacheManager.class).getCache("orders");
                    FailingRedisConnectionFactory connectionFactory = context.getBean(FailingRedisConnectionFactory.class);
                    assertThatThrownBy(() -> cache.get("id"))
                            .isInstanceOf(RedisConnectionFailureException.class);
                    connectionFactory.reset();
                    assertThatThrownBy(() -> cache.put("null", null))
                            .isInstanceOf(IllegalArgumentException.class);
                    assertThat(connectionFactory.accessed()).isFalse();
                });
        this.contextRunner.withUserConfiguration(FailingConnectionFactoryConfiguration.class)
                .withPropertyValues("coco.cache.redis.enabled=true", "coco.cache.redis.allow-null-values=true")
                .run(context -> {
                    FailingRedisConnectionFactory connectionFactory = context.getBean(FailingRedisConnectionFactory.class);
                    assertThatThrownBy(() -> context.getBean(CacheManager.class).getCache("orders").put("null", null))
                            .isInstanceOf(RedisConnectionFailureException.class);
                    assertThat(connectionFactory.accessed()).isTrue();
                });
    }

    @Test
    void roundTripsValuesThroughRedisCacheSerialization() {
        this.contextRunner.withConfiguration(AutoConfigurations.of(CocoCacheRedisAutoConfiguration.class,
                CocoCacheAutoConfiguration.class))
                .withUserConfiguration(InMemoryConnectionFactoryConfiguration.class, CacheableServiceConfiguration.class)
                .withPropertyValues("coco.cache.redis.enabled=true")
                .run(context -> {
                    CacheableService service = context.getBean(CacheableService.class);
                    assertThat(service.load("id")).isEqualTo("value-1");
                    assertThat(service.load("id")).isEqualTo("value-1");
                    assertThat(service.invocations()).isEqualTo(1);
                });
    }

    private void assertInvalid(String property, String message) {
        assertInvalid(property, null, message);
    }

    private void assertInvalid(String property, String additionalProperty, String message) {
        this.contextRunner.withUserConfiguration(ConnectionFactoryConfiguration.class)
                .withPropertyValues(additionalProperty == null
                        ? new String[] { "coco.cache.redis.enabled=true", property }
                        : new String[] { "coco.cache.redis.enabled=true", property, additionalProperty })
                .run(context -> assertThat(context).hasFailed().getFailure().hasStackTraceContaining(message));
    }

    @Configuration(proxyBeanMethods = false)
    static class ConnectionFactoryConfiguration {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return new StubRedisConnectionFactory();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FailingConnectionFactoryConfiguration {

        @Bean
        FailingRedisConnectionFactory redisConnectionFactory() {
            return new FailingRedisConnectionFactory();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InMemoryConnectionFactoryConfiguration {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return new InMemoryRedisConnectionFactory();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ApplicationCacheManagerConfiguration {

        @Bean
        CacheManager applicationCacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CacheResolverConfiguration {

        @Bean("cacheResolver")
        SimpleCacheResolver cacheResolver() {
            return new SimpleCacheResolver(new ConcurrentMapCacheManager());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomConfiguration {

        @Bean
        RedisCacheConfiguration redisCacheConfiguration() {
            return RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(7))
                    .computePrefixWith(cacheName -> "custom:" + cacheName + "::")
                    .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CacheableServiceConfiguration {

        @Bean
        CacheableService cacheableService() {
            return new CacheableService();
        }
    }

    static class CacheableService {

        private int invocations;

        @Cacheable("orders")
        String load(String id) {
            return "value-" + ++this.invocations;
        }

        int invocations() {
            return this.invocations;
        }
    }

    static class StubRedisConnectionFactory implements RedisConnectionFactory {

        @Override
        public RedisConnection getConnection() {
            throw new UnsupportedOperationException("not used by configuration tests");
        }

        @Override
        public org.springframework.data.redis.connection.RedisClusterConnection getClusterConnection() {
            return null;
        }

        @Override
        public boolean getConvertPipelineAndTxResults() {
            return false;
        }

        @Override
        public org.springframework.data.redis.connection.RedisSentinelConnection getSentinelConnection() {
            return null;
        }

        @Override
        public org.springframework.dao.DataAccessException translateExceptionIfPossible(RuntimeException exception) {
            return null;
        }
    }

    static class FailingRedisConnectionFactory extends StubRedisConnectionFactory {

        private final AtomicBoolean accessed = new AtomicBoolean();

        @Override
        public RedisConnection getConnection() {
            this.accessed.set(true);
            throw new RedisConnectionFailureException("connection unavailable");
        }

        boolean accessed() {
            return this.accessed.get();
        }

        void reset() {
            this.accessed.set(false);
        }
    }

    static class InMemoryRedisConnectionFactory extends StubRedisConnectionFactory {

        private final Map<ByteArrayKey, byte[]> values = new ConcurrentHashMap<>();

        @Override
        public RedisConnection getConnection() {
            return (RedisConnection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { RedisConnection.class }, (proxy, method, arguments) -> {
                        String name = method.getName();
                        if (name.equals("close")) {
                            return null;
                        }
                        if (name.equals("isClosed") || name.equals("isQueueing") || name.equals("isPipelined")) {
                            return false;
                        }
                        if (name.equals("get") && arguments.length == 1) {
                            byte[] value = this.values.get(new ByteArrayKey((byte[]) arguments[0]));
                            return value == null ? null : value.clone();
                        }
                        if (name.equals("set") && arguments.length >= 2) {
                            this.values.put(new ByteArrayKey((byte[]) arguments[0]), ((byte[]) arguments[1]).clone());
                            return true;
                        }
                        if (name.equals("del")) {
                            long count = 0;
                            for (byte[] key : (byte[][]) arguments[0]) {
                                if (this.values.remove(new ByteArrayKey(key)) != null) {
                                    count++;
                                }
                            }
                            return count;
                        }
                        if (name.endsWith("Commands")) {
                            return proxy;
                        }
                        if (name.equals("getNativeConnection")) {
                            return this;
                        }
                        throw new UnsupportedOperationException(name);
                    });
        }
    }

    private record ByteArrayKey(byte[] value) {

        private ByteArrayKey {
            value = value.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ByteArrayKey key && Arrays.equals(this.value, key.value);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(this.value);
        }
    }
}
