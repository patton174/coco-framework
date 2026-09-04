package io.github.coco.storage;

import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.storage.local.LocalCocoObjectStorage;
import org.springframework.beans.factory.ObjectProvider;
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

    /**
     * 创建默认魔数签名内容校验器。
     * @param properties 存储配置
     * @return 内容校验器
     */
    @Bean
    @ConditionalOnMissingBean(CocoContentValidator.class)
    public CocoContentValidator cocoContentValidator(CocoStorageProperties properties) {
        return new CocoSignatureContentValidator(properties.getValidation());
    }

    /**
     * 创建默认空实现内容扫描器。
     * <p>
     * 框架不实现恶意软件检测；需要真实扫描时业务方声明自己的 {@link CocoFileScanner} Bean 对接外部引擎。
     * </p>
     * @return 内容扫描器
     */
    @Bean
    @ConditionalOnMissingBean(CocoFileScanner.class)
    public CocoFileScanner cocoFileScanner() {
        return new NoOpCocoFileScanner();
    }

    /**
     * 创建对象存储内容校验织入器。
     * <p>
     * 该方法必须是 {@code static}：非静态的 BeanPostProcessor 工厂方法会导致所在配置类被提前实例化。
     * </p>
     * @param validatorProvider 内容校验器提供者
     * @param scannerProvider 内容扫描器提供者
     * @param properties 存储配置
     * @return 内容校验织入器
     */
    @Bean
    @ConditionalOnProperty(prefix = "coco.storage.validation", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public static CocoStorageValidationBeanPostProcessor cocoStorageValidationBeanPostProcessor(
            ObjectProvider<CocoContentValidator> validatorProvider, ObjectProvider<CocoFileScanner> scannerProvider,
            CocoStorageProperties properties) {
        return new CocoStorageValidationBeanPostProcessor(validatorProvider, scannerProvider, properties);
    }
}
