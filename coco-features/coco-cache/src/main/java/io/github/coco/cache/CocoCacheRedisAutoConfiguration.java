package io.github.coco.cache;

import java.util.Arrays;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Coco 缓存 TWO_LEVEL 拓扑的 Redis 装配。
 * <p>
 * {@code store-type=two-level} 且 classpath 有 Spring Data Redis 时,提供 L2 存储、失效广播
 * 发布器,并注册 pub/sub 监听容器把远端失效分发给 {@link CocoCacheManager}。在
 * {@link CocoCacheAutoConfiguration} 之前运行,使其 {@code ObjectProvider} 能拿到这两个 Bean。
 * </p>
 */
@AutoConfiguration(before = CocoCacheAutoConfiguration.class)
@EnableConfigurationProperties(CocoCacheProperties.class)
@ConditionalOnProperty(prefix = "coco.cache", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "coco.cache", name = "store-type", havingValue = "two-level")
@ConditionalOnClass(StringRedisTemplate.class)
public class CocoCacheRedisAutoConfiguration {

    /**
     * 创建 L2 缓存值模板。
     * <p>
     * 键用字符串序列化,值用 JDK 序列化(RedisTemplate 默认)。之所以不用 JSON,是因为框架已迁移到
     * Jackson 3.x,而 Spring Data Redis 的 {@code GenericJackson2JsonRedisSerializer} 仍绑定
     * Jackson 2.x,类路径上不存在。JDK 序列化要求被缓存的值实现 {@link java.io.Serializable};
     * 需要跨语言可读时,业务可提供自己的同名 Bean 覆盖此模板。
     * </p>
     * @param connectionFactory Redis 连接工厂
     * @return 值可序列化任意 {@code Serializable} 对象的 RedisTemplate
     */
    @Bean("cocoCacheL2RedisTemplate")
    @ConditionalOnMissingBean(name = "cocoCacheL2RedisTemplate")
    public RedisTemplate<String, Object> cocoCacheL2RedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 创建 L2 存储。
     * @param properties 缓存配置
     * @param redisTemplate L2 值模板
     * @return L2 存储
     */
    @Bean
    @ConditionalOnMissingBean(CocoCacheL2Store.class)
    public CocoCacheL2Store cocoCacheL2Store(CocoCacheProperties properties,
            RedisTemplate<String, Object> redisTemplate) {
        return new RedisCocoCacheL2Store(redisTemplate, properties.getRedis().getKeyPrefix());
    }

    /**
     * 创建失效广播发布器。
     * @param properties 缓存配置
     * @param beanFactory 用于解析 StringRedisTemplate
     * @return 失效广播发布器
     */
    @Bean
    @ConditionalOnMissingBean(CocoCacheInvalidationPublisher.class)
    public CocoCacheInvalidationPublisher cocoCacheInvalidationPublisher(CocoCacheProperties properties,
            ConfigurableListableBeanFactory beanFactory) {
        return new RedisCocoCacheInvalidationPublisher(resolve(beanFactory,
                properties.getRedis().getTemplateBeanName(), "coco.cache.redis.template-bean-name"),
                properties.getRedis().getInvalidationChannel());
    }

    /**
     * 注册 pub/sub 监听容器,把远端失效分发给缓存管理器。
     * @param connectionFactory Redis 连接工厂
     * @param properties 缓存配置
     * @param cacheManager 缓存管理器(延迟获取,避免与 before 装配顺序冲突)
     * @return 监听容器
     */
    @Bean
    @ConditionalOnMissingBean(RedisMessageListenerContainer.class)
    public RedisMessageListenerContainer cocoCacheInvalidationContainer(RedisConnectionFactory connectionFactory,
            CocoCacheProperties properties, ObjectProvider<CocoCacheManager> cacheManager) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            CocoCacheManager manager = cacheManager.getIfAvailable();
            if (manager != null) {
                new CocoCacheInvalidationListener(manager).onMessage(message, pattern);
            }
        }, new ChannelTopic(properties.getRedis().getInvalidationChannel()));
        return container;
    }

    private static StringRedisTemplate resolve(ConfigurableListableBeanFactory factory, String configuredName,
            String key) {
        if (configuredName != null) {
            if (!factory.containsBean(configuredName)
                    || !factory.isTypeMatch(configuredName, StringRedisTemplate.class)) {
                throw new BeanCreationException(key + " references StringRedisTemplate bean '" + configuredName
                        + "' that does not exist or has the wrong type");
            }
            return factory.getBean(configuredName, StringRedisTemplate.class);
        }
        String[] names = factory.getBeanNamesForType(StringRedisTemplate.class, false, false);
        if (names.length == 1) {
            return factory.getBean(names[0], StringRedisTemplate.class);
        }
        String[] primary = Arrays.stream(names)
                .filter(name -> factory.containsBeanDefinition(name) && factory.getBeanDefinition(name).isPrimary())
                .toArray(String[]::new);
        if (primary.length == 1) {
            return factory.getBean(primary[0], StringRedisTemplate.class);
        }
        throw new BeanCreationException(key + " requires one StringRedisTemplate or one @Primary candidate; candidates="
                + Arrays.toString(names) + "; configure " + key);
    }
}
