package io.github.coco.messaging;

import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.messaging.internal.CocoMessageListenerRegistrar;
import io.github.coco.messaging.internal.DefaultCocoMessagePublisher;
import io.github.coco.messaging.internal.LocalCocoMessageTransport;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Coco 消息模块自动配置。
 * <p>
 * 默认只提供进程内消息投递；通过声明 {@link CocoMessageTransport} Bean 可以替换传输实现。
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(CocoMessagingProperties.class)
@ConditionalOnProperty(prefix = "coco.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CocoMessagingAutoConfiguration {

    /**
     * 注册消息模块的国际化资源。
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoMessagingMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoMessagingMessageBundleRegistrar() {
        return registry -> registry.add("coco-messaging-messages");
    }

    /**
     * 创建默认进程内传输实现。
     * @param properties 消息配置
     * @return 本地消息传输
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(CocoMessageTransport.class)
    public CocoMessageTransport cocoMessageTransport(CocoMessagingProperties properties) {
        return new LocalCocoMessageTransport(properties);
    }

    /**
     * 创建默认消息发布器。
     * @param transport 当前消息传输实现
     * @return 消息发布器
     */
    @Bean
    @ConditionalOnMissingBean(CocoMessagePublisher.class)
    public CocoMessagePublisher cocoMessagePublisher(CocoMessageTransport transport) {
        return new DefaultCocoMessagePublisher(transport);
    }

    /**
     * 注册注解监听方法和 {@link CocoMessageHandler} Bean。
     * @param beanFactory Bean 工厂
     * @param transport 当前消息传输实现
     * @return 监听器注册器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoMessageListenerRegistrar cocoMessageListenerRegistrar(ConfigurableListableBeanFactory beanFactory,
            CocoMessageTransport transport) {
        return new CocoMessageListenerRegistrar(beanFactory, transport);
    }
}
