package io.github.coco.spring.boot;

import io.github.coco.common.i18n.api.CocoMessageBundleRegistrar;
import io.github.coco.spring.boot.banner.CocoBannerProperties;
import io.github.coco.spring.boot.banner.CocoStartupBanner;
import io.github.coco.spring.boot.banner.CocoStartupBannerLogger;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Coco 自动配置入口。
 * <p>
 * Spring Boot 自动配置根入口，骨架阶段不注册具体业务能力。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-spring-boot-starter}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(CocoBannerProperties.class)
public class CocoAutoConfiguration {

    /**
     * <p>
     * 注册 Starter 模块内置的国际化消息资源。
     * </p>
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoSpringBootStarterMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoSpringBootStarterMessageBundleRegistrar() {
        return registry -> registry.add("coco-spring-boot-starter-messages");
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

    /**
     * <p>
     * 创建 Coco 启动 banner 日志监听器。
     * </p>
     * @param banner Coco 启动 banner 渲染器
     * @return Coco 启动 banner 日志监听器
     */
    @Bean
    @ConditionalOnProperty(prefix = "coco.banner", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public CocoStartupBannerLogger cocoStartupBannerLogger(CocoStartupBanner banner) {
        return new CocoStartupBannerLogger(banner);
    }
}
