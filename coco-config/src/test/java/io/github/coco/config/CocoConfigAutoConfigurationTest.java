package io.github.coco.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.coco.api.CocoConfigurer;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.api.feature.CocoFeatureRegistry;
import io.github.coco.api.feature.CocoFeatures;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.common.i18n.api.CocoMessageService;
import io.github.coco.feature.registry.CocoFeaturePlan;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Coco 配置自动装配测试。
 * <p>
 * 验证 Spring Boot 配置文件和 {@code CocoConfigurer} Bean 会合并生成最终功能状态。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-config}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoConfigAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoConfigAutoConfiguration.class));

    @Test
    void createsFeatureManagerWithDefaultFeaturesEnabled() {
        this.contextRunner.run(context -> {
            CocoFeatureManager manager = context.getBean(CocoFeatureManager.class);

            assertTrue(manager.isEnabled(CocoFeature.WEB));
            assertTrue(manager.isEnabled(CocoFeature.TENANT));
        });
    }

    @Test
    void appliesDisabledFeaturesFromApplicationProperties() {
        this.contextRunner
                .withPropertyValues(
                        "coco.features.disabled[0]=tenant",
                        "coco.features.disabled[1]=data-permission")
                .run(context -> {
                    CocoFeatureManager manager = context.getBean(CocoFeatureManager.class);

                    assertFalse(manager.isEnabled(CocoFeature.TENANT));
                    assertFalse(manager.isEnabled(CocoFeature.DATA_PERMISSION));
                    assertTrue(manager.isEnabled(CocoFeature.WEB));
                });
    }

    @Test
    void appliesSingleDisabledFeatureFromApplicationProperties() {
        this.contextRunner
                .withPropertyValues("coco.features.disabled[0]=openapi")
                .run(context -> {
                    CocoFeatureManager manager = context.getBean(CocoFeatureManager.class);

                    assertFalse(manager.isEnabled(CocoFeature.OPENAPI));
                    assertTrue(manager.isEnabled(CocoFeature.WEB));
                });
    }

    @Test
    void codeConfigurationCanOverrideApplicationProperties() {
        this.contextRunner
                .withUserConfiguration(AnnotatedCocoConfiguration.class)
                .withPropertyValues("coco.features.disabled[0]=tenant")
                .run(context -> {
                    CocoFeatureManager manager = context.getBean(CocoFeatureManager.class);
                    CocoFeaturePlan plan = context.getBean(CocoFeaturePlan.class);

                    assertTrue(manager.isEnabled(CocoFeature.TENANT));
                    assertTrue(plan.enabledFeatures().contains(CocoFeature.TENANT));
                });
    }

    @Test
    void mergesDisabledFeaturesFromCocoConfigurerBeans() {
        this.contextRunner
                .withUserConfiguration(UserCocoConfiguration.class)
                .withPropertyValues("coco.features.disabled[0]=tenant")
                .run(context -> {
                    CocoFeatureManager manager = context.getBean(CocoFeatureManager.class);

                    assertFalse(manager.isEnabled(CocoFeature.TENANT));
                    assertFalse(manager.isEnabled(CocoFeature.DATA_PERMISSION));
                    assertTrue(manager.isEnabled(CocoFeature.WEB));
                });
    }

    @Test
    void registersConfigMessageBundle() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        CocoCommonAutoConfiguration.class,
                        CocoConfigAutoConfiguration.class))
                .withPropertyValues("coco.common.i18n.basename=coco-messages")
                .run(context -> {
                    CocoMessageService messageService = context.getBean(CocoMessageService.class);

                    assertTrue(context.containsBean("cocoConfigMessageBundleRegistrar"));
                    assertEquals("无效的 Coco 功能禁用配置。",
                            messageService.getMessage("coco.config.features.disabled.invalid"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class UserCocoConfiguration {

        @Bean
        CocoConfigurer dataPermissionConfigurer() {
            return new CocoConfigurer() {

                @Override
                public void configureFeatures(CocoFeatureRegistry features) {
                    features.disable(CocoFeature.DATA_PERMISSION);
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    @CocoFeatures(enabled = CocoFeature.TENANT)
    static class AnnotatedCocoConfiguration {
    }
}
