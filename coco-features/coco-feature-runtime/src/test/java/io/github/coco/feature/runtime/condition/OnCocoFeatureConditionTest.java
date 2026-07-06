package io.github.coco.feature.runtime.condition;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.coco.api.feature.CocoFeature;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Coco 功能条件判断器测试。
 * <p>
 * 验证自动配置可以根据功能启用状态生效或跳过。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-runtime}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class OnCocoFeatureConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ConditionalFeatureConfiguration.class);

    @Test
    void matchesEnabledFeatureByDefault() {
        this.contextRunner.run(context -> assertThat(context).hasBean("webFeatureBean"));
    }

    @Test
    void skipsDisabledFeatureFromEnvironment() {
        this.contextRunner
                .withPropertyValues("coco.features.exclude[0]=web")
                .run(context -> assertThat(context).doesNotHaveBean("webFeatureBean"));
    }

    @Test
    void skipsFeatureWhenDependencyIsDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(DependentFeatureConfiguration.class)
                .withPropertyValues("coco.features.exclude[0]=mybatis-plus")
                .run(context -> assertThat(context).doesNotHaveBean("auditFeatureBean"));
    }

    @Configuration(proxyBeanMethods = false)
    static class ConditionalFeatureConfiguration {

        @Bean
        @ConditionalOnCocoFeature(CocoFeature.WEB)
        String webFeatureBean() {
            return "web";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DependentFeatureConfiguration {

        @Bean
        @ConditionalOnCocoFeature(CocoFeature.AUDIT)
        String auditFeatureBean() {
            return "audit";
        }
    }
}
