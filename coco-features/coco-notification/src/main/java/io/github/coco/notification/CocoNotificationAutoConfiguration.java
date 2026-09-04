package io.github.coco.notification;

import java.util.ArrayList;
import java.util.List;

import io.github.coco.i18n.CocoMessageBundleRegistrar;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Coco 通知自动配置。
 * <p>
 * {@code coco.notification.enabled=true} 时装配 {@link CocoNotificationService}。参考渠道
 * (日志 SMS/EMAIL、进程内站内信)只在业务未提供该类型渠道时补齐——业务注册的
 * {@link CocoNotificationChannel} 优先,参考实现不会覆盖它们。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-notification}</li>
 * </ul>
 * @author patton174
 * @since 2.1.0
 */
@AutoConfiguration
@EnableConfigurationProperties(CocoNotificationProperties.class)
@ConditionalOnProperty(prefix = "coco.notification", name = "enabled", havingValue = "true")
public class CocoNotificationAutoConfiguration {

    /**
     * 注册通知模块的 i18n 消息包。
     * @return 消息包注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoNotificationMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoNotificationMessageBundleRegistrar() {
        return registry -> registry.add("coco-notification-messages");
    }

    /**
     * 组装通知服务:业务渠道 + 按需补齐的参考渠道。
     * @param properties 通知配置
     * @param businessChannels 业务提供的渠道
     * @return 通知服务
     */
    @Bean
    @ConditionalOnMissingBean(CocoNotificationService.class)
    public CocoNotificationService cocoNotificationService(CocoNotificationProperties properties,
            ObjectProvider<CocoNotificationChannel> businessChannels) {
        List<CocoNotificationChannel> channels = new ArrayList<>();
        businessChannels.orderedStream().forEach(channels::add);
        boolean hasSms = channels.stream().anyMatch(c -> c.supportedType() == CocoNotificationChannelType.SMS);
        boolean hasEmail = channels.stream().anyMatch(c -> c.supportedType() == CocoNotificationChannelType.EMAIL);
        boolean hasInApp = channels.stream().anyMatch(c -> c.supportedType() == CocoNotificationChannelType.IN_APP);
        // Reference channels only fill gaps — a business channel for a type always wins.
        if (properties.isLoggingFallback() && !hasSms) {
            channels.add(new LoggingCocoNotificationChannel(CocoNotificationChannelType.SMS));
        }
        if (properties.isLoggingFallback() && !hasEmail) {
            channels.add(new LoggingCocoNotificationChannel(CocoNotificationChannelType.EMAIL));
        }
        if (properties.isInMemoryInApp() && !hasInApp) {
            channels.add(new InMemoryInAppCocoNotificationChannel());
        }
        return new CocoNotificationService(channels);
    }
}
