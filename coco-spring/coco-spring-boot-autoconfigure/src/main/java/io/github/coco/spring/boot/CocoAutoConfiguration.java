package io.github.coco.spring.boot;

import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.spring.boot.autoconfigure.logging.CocoLoggingAutoConfiguration;
import io.github.coco.spring.boot.banner.CocoBannerProperties;
import io.github.coco.spring.boot.banner.CocoStartupBanner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Coco Spring Boot 自动配置入口。
 * <p>
 * 负责注册 Spring Boot 接入层自己的消息资源和启动 banner 组件，日志基础设施由 {@link CocoLoggingAutoConfiguration} 承接。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-spring-boot-autoconfigure}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration(after = CocoLoggingAutoConfiguration.class)
@EnableConfigurationProperties(CocoBannerProperties.class)
public class CocoAutoConfiguration {

    /**
     * <p>
     * 注册 Spring Boot 接入层内置的国际化消息资源。
     * </p>
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoSpringBootMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoSpringBootMessageBundleRegistrar() {
        return registry -> registry.add("coco-spring-boot-messages");
    }

    /**
     * <p>
     * 创建 Coco 启动 banner 渲染器。
     * </p>
     * @param properties Coco 启动 banner 配置属性
     * @return Coco 启动 banner 渲染器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoStartupBanner cocoStartupBanner(CocoBannerProperties properties) {
        return new CocoStartupBanner(properties);
    }
}
