package io.github.coco.storage;

import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.storage.local.LocalCocoObjectStorage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Coco 对象存储自动配置。
 * <p>
 * 不创建业务 Controller 或附件元数据表；业务方可声明自己的 {@link CocoObjectStorage} Bean 取代本地参考实现。
 * </p>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "coco.storage", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CocoStorageProperties.class)
public class CocoStorageAutoConfiguration {

    /**
     * 注册存储模块国际化消息资源。
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoStorageMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoStorageMessageBundleRegistrar() {
        return registry -> registry.add("coco-storage-messages");
    }

    /**
     * 创建安全流式本地文件参考实现。
     * @param properties 存储配置
     * @return 本地对象存储
     */
    @Bean
    @ConditionalOnMissingBean(CocoObjectStorage.class)
    public CocoObjectStorage cocoObjectStorage(CocoStorageProperties properties) {
        return new LocalCocoObjectStorage(properties);
    }
}
