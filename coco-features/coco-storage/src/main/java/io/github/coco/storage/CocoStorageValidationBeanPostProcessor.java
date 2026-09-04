package io.github.coco.storage;

import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Coco 对象存储内容校验织入器。
 * <p>
 * 把容器中的每个 {@link CocoObjectStorage} 包装成 {@link ValidatingCocoObjectStorage}，
 * 使业务方自行提供的 S3、OSS 等实现同样经过上传内容校验，而不需要各自重复接入。
 * 已经是校验装饰器的 Bean 不再重复包装。
 * </p>
 * <p>
 * 校验器和扫描器通过 {@link ObjectProvider} 延迟获取：BeanPostProcessor 必须在普通 Bean 之前实例化，
 * 直接注入依赖会把这些 Bean 提前拉入创建流程并引发循环依赖。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-storage}</li>
 * </ul>
 * @author patton174
 * @since 1.1.0
 */
public class CocoStorageValidationBeanPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<CocoContentValidator> validatorProvider;

    private final ObjectProvider<CocoFileScanner> scannerProvider;

    private final CocoStorageProperties properties;

    /**
     * <p>
     * 创建内容校验织入器。
     * </p>
     * @param validatorProvider 内容校验器提供者
     * @param scannerProvider 内容扫描器提供者
     * @param properties 存储配置
     */
    public CocoStorageValidationBeanPostProcessor(ObjectProvider<CocoContentValidator> validatorProvider,
            ObjectProvider<CocoFileScanner> scannerProvider, CocoStorageProperties properties) {
        this.validatorProvider = Objects.requireNonNull(validatorProvider, "validatorProvider must not be null");
        this.scannerProvider = Objects.requireNonNull(scannerProvider, "scannerProvider must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * <p>
     * 为对象存储 Bean 织入上传内容校验。
     * </p>
     * @param bean 已初始化的 Bean
     * @param beanName Bean 名称
     * @return 需要校验时返回装饰后的对象存储，否则返回原 Bean
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof CocoObjectStorage storage) || bean instanceof ValidatingCocoObjectStorage) {
            return bean;
        }
        CocoContentValidator validator = this.validatorProvider
                .getIfAvailable(() -> new CocoSignatureContentValidator(this.properties.getValidation()));
        CocoFileScanner scanner = this.scannerProvider.getIfAvailable(NoOpCocoFileScanner::new);
        return new ValidatingCocoObjectStorage(storage, validator, scanner,
                this.properties.getValidation().getProbeSize());
    }
}
