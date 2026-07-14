package io.github.coco.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.coco.api.CocoConfigurer;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.api.feature.CocoFeatureRegistry;
import io.github.coco.api.feature.CocoFeatures;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.feature.model.CocoFeatureManifestLoader;
import io.github.coco.feature.model.CocoFeaturePlan;
import io.github.coco.feature.model.CocoFeatureSelection;
import io.github.coco.feature.model.StandardCocoFeatures;
import io.github.coco.i18n.CocoMessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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

    @TempDir
    Path tempDir;

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
    @ExtendWith(OutputCaptureExtension.class)
    void runtimeConfigurationLogsOnlyDependencyPropagatedFeatures(CapturedOutput output) {
        this.contextRunner
                .withPropertyValues(
                        "coco.features.disabled[0]=tenant",
                        "coco.features.disabled[1]=mybatis-plus")
                .run(context -> {
                    CocoFeaturePlan plan = context.getBean(CocoFeaturePlan.class);

                    assertEquals(java.util.Set.of(CocoFeature.TENANT, CocoFeature.DATA_PERMISSION, CocoFeature.CODEGEN),
                            plan.disabledByDependencyFeatures());
                });

        assertTrue(output.getAll().contains("disabledByDependency=[codegen, data-permission]"));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void manifestWithoutProvenanceLogsDependencyImpactWithoutAttributingCause(CapturedOutput output) throws Exception {
        Path manifestPath = this.tempDir.resolve(CocoFeatureManifestLoader.MANIFEST_LOCATION);
        Files.createDirectories(manifestPath.getParent());
        CocoFeaturePlan buildPlan = StandardCocoFeatures.resolve(
                CocoFeatureSelection.ofDisabled(java.util.Set.of(CocoFeature.TENANT, CocoFeature.MYBATIS_PLUS)));
        Files.writeString(manifestPath, CocoFeatureManifestLoader.write(
                StandardCocoFeatures.toManifest(buildPlan, "test")), StandardCharsets.UTF_8);

        try (URLClassLoader classLoader = new URLClassLoader(
                new java.net.URL[] { this.tempDir.toUri().toURL() }, getClass().getClassLoader())) {
            this.contextRunner.withClassLoader(classLoader).run(context -> {
                CocoFeaturePlan plan = context.getBean(CocoFeaturePlan.class);

                assertFalse(plan.isEnabled(CocoFeature.TENANT));
                assertFalse(plan.isEnabled(CocoFeature.MYBATIS_PLUS));
            });
        }

        assertTrue(output.getAll().contains("dependencyAffected=[codegen, data-permission, tenant]"));
        assertTrue(output.getAll().contains("dependencyProvenance=unknown"));
        assertFalse(output.getAll().contains("disabledByDependency="));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void codeConfigurationLogsOnlyDependencyPropagatedFeatures(CapturedOutput output) {
        this.contextRunner
                .withUserConfiguration(DependencyDisabledCocoConfiguration.class)
                .run(context -> {
                    CocoFeaturePlan plan = context.getBean(CocoFeaturePlan.class);

                    assertEquals(java.util.Set.of(CocoFeature.TENANT, CocoFeature.DATA_PERMISSION, CocoFeature.CODEGEN),
                            plan.disabledByDependencyFeatures());
                });

        assertTrue(output.getAll().contains("disabledByDependency=[codegen, data-permission]"));
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

    @Configuration(proxyBeanMethods = false)
    @CocoFeatures(disabled = { CocoFeature.TENANT, CocoFeature.MYBATIS_PLUS })
    static class DependencyDisabledCocoConfiguration {
    }
}
