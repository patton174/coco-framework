package io.github.coco.spring.boot;

import io.github.coco.common.i18n.api.CocoMessageBundleRegistrar;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
}
