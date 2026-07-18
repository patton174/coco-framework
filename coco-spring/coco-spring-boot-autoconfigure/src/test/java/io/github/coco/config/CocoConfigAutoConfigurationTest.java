package io.github.coco.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLClassLoader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

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
    void publishedThreeArgumentFeaturePlanUsesPassedPropertiesAndConfigurersWithoutManifest() throws Exception {
        CocoProperties properties = new CocoProperties();
        CocoFeatureProperties features = new CocoFeatureProperties();
        features.setDisabled(Set.of(CocoFeature.TENANT));
        properties.setFeatures(features);
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("publishedApiConfigurer", new CocoConfigurer() {
            @Override
            public void configureFeatures(CocoFeatureRegistry features) {
                features.disable(CocoFeature.DATA_PERMISSION);
            }
        });
        ClassLoader previous = Thread.currentThread().getContextClassLoader();

        try (URLClassLoader noManifest = new URLClassLoader(new URL[0], null)) {
            Thread.currentThread().setContextClassLoader(noManifest);
            CocoFeaturePlan plan = new CocoConfigAutoConfiguration().cocoFeaturePlan(
                    properties, beanFactory.getBeanProvider(CocoConfigurer.class), beanFactory);

            assertFalse(plan.isEnabled(CocoFeature.TENANT));
            assertFalse(plan.isEnabled(CocoFeature.DATA_PERMISSION));
            assertTrue(plan.isEnabled(CocoFeature.WEB));
        }
        finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Test
    void publishedThreeArgumentFeaturePlanShortCircuitsNullArgumentsWhenManifestExists() throws Exception {
        Path manifestPath = this.tempDir.resolve(CocoFeatureManifestLoader.MANIFEST_LOCATION);
        Files.createDirectories(manifestPath.getParent());
        CocoFeaturePlan expected = StandardCocoFeatures.resolve(
                CocoFeatureSelection.ofDisabled(Set.of(CocoFeature.TENANT)));
        Files.writeString(manifestPath, CocoFeatureManifestLoader.write(
                StandardCocoFeatures.toManifest(expected, "published-api-test")), StandardCharsets.UTF_8);
        ClassLoader previous = Thread.currentThread().getContextClassLoader();

        try (URLClassLoader manifestClassLoader = new URLClassLoader(
                new URL[] { this.tempDir.toUri().toURL() }, null)) {
            Thread.currentThread().setContextClassLoader(manifestClassLoader);

            CocoFeaturePlan actual = new CocoConfigAutoConfiguration().cocoFeaturePlan(null, null, null);

            assertEquals(expected.enabledFeatures(), actual.enabledFeatures());
            assertEquals(expected.disabledFeatures(), actual.disabledFeatures());
        }
        finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Test
    void publishedThreeArgumentFeaturePlanKeepsNullBoundariesWithoutManifest() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        CocoProperties properties = new CocoProperties();
        ClassLoader previous = Thread.currentThread().getContextClassLoader();

        try (URLClassLoader noManifest = new URLClassLoader(new URL[0], null)) {
            Thread.currentThread().setContextClassLoader(noManifest);
            CocoConfigAutoConfiguration configuration = new CocoConfigAutoConfiguration();

            assertThrows(NullPointerException.class, () -> configuration.cocoFeaturePlan(
                    null, beanFactory.getBeanProvider(CocoConfigurer.class), beanFactory));
            assertThrows(NullPointerException.class,
                    () -> configuration.cocoFeaturePlan(properties, null, beanFactory));
            assertNotNull(configuration.cocoFeaturePlan(
                    properties, beanFactory.getBeanProvider(CocoConfigurer.class), null));
        }
        finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Test
    void publishedThreeArgumentFeaturePlanLetsCodeSelectionOverridePropertyConflict() throws Exception {
        CocoProperties properties = new CocoProperties();
        CocoFeatureProperties features = new CocoFeatureProperties();
        features.setDisabled(Set.of(CocoFeature.WEB));
        properties.setFeatures(features);
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("publishedApiOverride", new CocoConfigurer() {
            @Override
            public void configureFeatures(CocoFeatureRegistry features) {
                features.enable(CocoFeature.WEB);
            }
        });
        ClassLoader previous = Thread.currentThread().getContextClassLoader();

        try (URLClassLoader noManifest = new URLClassLoader(new URL[0], null)) {
            Thread.currentThread().setContextClassLoader(noManifest);

            CocoFeaturePlan plan = new CocoConfigAutoConfiguration().cocoFeaturePlan(
                    properties, beanFactory.getBeanProvider(CocoConfigurer.class), beanFactory);

            assertTrue(plan.isEnabled(CocoFeature.WEB));
        }
        finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
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
    void rootPropertiesPreservePublishedNestedFeatureIdentityAndBindThroughJavaBeans() {
        CocoFeatureProperties features = new CocoFeatureProperties();
        features.setEnabled(Set.of(CocoFeature.WEB));
        CocoProperties properties = new CocoProperties();
        properties.setFeatures(features);
        assertSame(features, properties.getFeatures());
        features.setEnabled(Set.of(CocoFeature.OPENAPI));

        assertEquals(Set.of(CocoFeature.OPENAPI), properties.getFeatures().getEnabled());
        CocoFeatureProperties liveFeatures = properties.getFeatures();
        liveFeatures.setDisabled(Set.of(CocoFeature.TENANT));
        assertSame(liveFeatures, properties.getFeatures());
        assertEquals(Set.of(CocoFeature.TENANT), properties.getFeatures().getDisabled());

        properties.setFeatures(null);
        assertNotNull(properties.getFeatures());
        assertTrue(properties.getFeatures().getEnabled().isEmpty());
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

    @Configuration(proxyBeanMethods = false)
    @CocoFeatures(disabled = { CocoFeature.TENANT, CocoFeature.MYBATIS_PLUS })
    static class DependencyDisabledCocoConfiguration {
    }
}
