package io.github.coco.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import io.github.coco.api.CocoConfigurer;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.api.feature.CocoFeatureRegistry;
import io.github.coco.api.feature.CocoFeatures;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.i18n.CocoMessageService;
import io.github.coco.feature.model.CocoFeaturePlan;
import io.github.coco.feature.model.CocoFeatureSelection;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
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
 *   <li>模块：{@code coco-spring-boot-autoconfigure}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@SuppressWarnings("deprecation")
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
    void propertyDisableWinsOverCodeEnable() {
        this.contextRunner
                .withUserConfiguration(AnnotatedCocoConfiguration.class)
                .withPropertyValues("coco.features.disabled[0]=tenant")
                .run(context -> {
                    CocoFeatureManager manager = context.getBean(CocoFeatureManager.class);
                    CocoFeaturePlan plan = context.getBean(CocoFeaturePlan.class);

                    assertFalse(manager.isEnabled(CocoFeature.TENANT));
                    assertFalse(plan.enabledFeatures().contains(CocoFeature.TENANT));
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

    @Test
    void featurePropertiesAdaptToSelectionModel() {
        CocoFeatureProperties properties = new CocoFeatureProperties();
        properties.setEnabled(java.util.Set.of(CocoFeature.WEB));
        properties.setDisabled(java.util.Set.of(CocoFeature.TENANT));

        CocoFeatureSelection selection = properties.toSelection();

        assertEquals(java.util.Set.of(CocoFeature.WEB), selection.enabled());
        assertEquals(java.util.Set.of(CocoFeature.TENANT), selection.disabled());
    }

    @Test
    void featurePropertiesExposeImmutableCollectionSnapshots() {
        Set<CocoFeature> enabled = new LinkedHashSet<>(Set.of(CocoFeature.WEB));
        Set<CocoFeature> disabled = new LinkedHashSet<>(Set.of(CocoFeature.TENANT));
        CocoFeatureProperties properties = new CocoFeatureProperties();
        properties.setEnabled(enabled);
        properties.setDisabled(disabled);
        enabled.add(CocoFeature.OPENAPI);
        disabled.add(CocoFeature.DATA_PERMISSION);

        assertEquals(Set.of(CocoFeature.WEB), properties.getEnabled());
        assertEquals(Set.of(CocoFeature.TENANT), properties.getDisabled());
        assertThrows(UnsupportedOperationException.class, () -> properties.getEnabled().add(CocoFeature.OPENAPI));
        assertThrows(UnsupportedOperationException.class,
                () -> properties.getDisabled().add(CocoFeature.DATA_PERMISSION));
    }

    @Test
    void rootPropertiesIsolateNestedFeaturePropertiesAndBindThroughJavaBeans() {
        CocoFeatureProperties features = new CocoFeatureProperties();
        features.setEnabled(Set.of(CocoFeature.WEB));
        CocoProperties properties = new CocoProperties();
        properties.setFeatures(features);
        features.setEnabled(Set.of(CocoFeature.OPENAPI));

        assertEquals(Set.of(CocoFeature.WEB), properties.getFeatures().getEnabled());
        CocoFeatureProperties snapshot = properties.getFeatures();
        snapshot.setDisabled(Set.of(CocoFeature.TENANT));
        assertTrue(properties.getFeatures().getDisabled().isEmpty());

        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "coco.features.enabled[0]", "web",
                "coco.features.disabled[0]", "tenant")));
        assertTrue(binder.bind("coco", Bindable.ofInstance(properties)).isBound());
        assertEquals(Set.of(CocoFeature.WEB), properties.getFeatures().getEnabled());
        assertEquals(Set.of(CocoFeature.TENANT), properties.getFeatures().getDisabled());
    }

    @Test
    void preservesPublishedConfigurationPropertiesApi() throws ReflectiveOperationException {
        assertEquals(CocoProperties.class, Class.forName("io.github.coco.config.CocoProperties"));
        assertNotNull(CocoProperties.class.getConstructor());
        assertEquals(CocoFeatureProperties.class, CocoProperties.class.getMethod("getFeatures").getReturnType());
        assertNotNull(CocoProperties.class.getMethod("setFeatures", CocoFeatureProperties.class));
        assertEquals(Set.class, CocoFeatureProperties.class.getMethod("getEnabled").getReturnType());
        assertEquals(Set.class, CocoFeatureProperties.class.getMethod("getDisabled").getReturnType());
        assertNotNull(CocoFeatureProperties.class.getMethod("setEnabled", Set.class));
        assertNotNull(CocoFeatureProperties.class.getMethod("setDisabled", Set.class));
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
