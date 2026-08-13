package io.github.coco.feature.storage;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Coco 对象存储自动配置。仅提供本地实现，不注册 Web 端点。 */
@AutoConfiguration
@EnableConfigurationProperties(CocoStorageProperties.class)
@ConditionalOnProperty(prefix = "coco.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CocoStorageAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(CocoObjectStorage.class)
    public CocoObjectStorage cocoObjectStorage(CocoStorageProperties properties) {
        properties.validate();
        return new LocalCocoObjectStorage(properties.getLocal());
    }
}
