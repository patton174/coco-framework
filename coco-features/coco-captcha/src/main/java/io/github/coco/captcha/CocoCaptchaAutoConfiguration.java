package io.github.coco.captcha;

import java.util.ArrayList;
import java.util.List;

import io.github.coco.i18n.CocoMessageBundleRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Coco 验证码自动配置。
 * <p>
 * 仅在 {@code coco.captcha.enabled=true} 时装配。提供进程内答案存储与验证码服务(均可被业务同名 Bean 覆盖),
 * 并按 {@code coco.captcha.*-enabled} 开关补齐图形/滑块/短信参考生成器;业务提供同类型生成器时以业务为准。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-captcha}</li>
 * </ul>
 * @author patton174
 * @since 2.1.0
 */
@AutoConfiguration
@EnableConfigurationProperties(CocoCaptchaProperties.class)
@ConditionalOnProperty(prefix = "coco.captcha", name = "enabled", havingValue = "true")
public class CocoCaptchaAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(CocoCaptchaAutoConfiguration.class);

    /**
     * 注册验证码模块的 i18n 消息包。
     * @return 消息包注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoCaptchaMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoCaptchaMessageBundleRegistrar() {
        return registry -> registry.add("coco-captcha-messages");
    }

    /**
     * 进程内答案存储(业务可覆盖)。
     * @return 答案存储
     */
    @Bean
    @ConditionalOnMissingBean(CocoCaptchaStore.class)
    public CocoCaptchaStore cocoCaptchaStore() {
        return new InMemoryCocoCaptchaStore();
    }

    /**
     * 组装验证码服务:业务生成器 + 按开关补齐的参考生成器。
     * @param properties 验证码配置
     * @param store 答案存储
     * @param businessGenerators 业务提供的生成器
     * @return 验证码服务
     */
    @Bean
    @ConditionalOnMissingBean(CocoCaptchaService.class)
    public CocoCaptchaService cocoCaptchaService(CocoCaptchaProperties properties, CocoCaptchaStore store,
            ObjectProvider<CocoCaptchaGenerator> businessGenerators) {
        List<CocoCaptchaGenerator> generators = new ArrayList<>();
        businessGenerators.orderedStream().forEach(generators::add);
        boolean hasImage = generators.stream().anyMatch(g -> g.supportedType() == CocoCaptchaType.IMAGE);
        boolean hasSlider = generators.stream().anyMatch(g -> g.supportedType() == CocoCaptchaType.SLIDER);
        boolean hasSmsCode = generators.stream().anyMatch(g -> g.supportedType() == CocoCaptchaType.SMS_CODE);
        // Reference generators only fill gaps — a business generator for a type always wins.
        if (properties.isImageEnabled() && !hasImage) {
            generators.add(new ImageCocoCaptchaGenerator(properties.getImageLength()));
        }
        if (properties.isSliderEnabled() && !hasSlider) {
            generators.add(new SliderCocoCaptchaGenerator(properties.getSliderTolerance()));
        }
        if (properties.isSmsCodeEnabled() && !hasSmsCode) {
            generators.add(new SmsCodeCocoCaptchaGenerator(properties.getSmsCodeLength()));
        }
        if (generators.isEmpty()) {
            LOGGER.warn("Coco captcha is enabled but no generators are configured; "
                    + "enable a reference type or register a CocoCaptchaGenerator.");
        }
        return new CocoCaptchaService(generators, store, properties.getTtl());
    }
}
