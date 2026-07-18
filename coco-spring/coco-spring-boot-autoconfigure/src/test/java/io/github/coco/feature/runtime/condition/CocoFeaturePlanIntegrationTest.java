package io.github.coco.feature.runtime.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import io.github.coco.api.CocoConfigurer;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.api.feature.CocoFeatureRegistry;
import io.github.coco.api.feature.CocoFeatures;
import io.github.coco.config.CocoFeatureManager;
import io.github.coco.config.DefaultCocoFeatureManager;
import io.github.coco.feature.model.CocoFeatureManifestLoader;
import io.github.coco.feature.model.CocoFeaturePlan;
import io.github.coco.feature.model.CocoFeatureSelection;
import io.github.coco.feature.model.StandardCocoFeatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.Banner;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Coco 启动早期功能计划集成测试。
 * <p>
 * 使用真实 {@link SpringApplication} 验证构建清单只限制可用能力，运行期有效配置和可早期读取的代码选择
 * 会共同驱动条件判断与功能管理器。
 * </p>
 * @author patton174
 * @since 2.0.0
 */
@SuppressWarnings("deprecation")
class CocoFeaturePlanIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void activeProfileCanDisableFeatureAvailableInManifest() throws Exception {
        try (URLClassLoader classLoader = manifestClassLoader(StandardCocoFeatures.resolve(CocoFeatureSelection.empty()),
                Map.of("application-feature-plan.properties", "coco.features.disabled=web\n"));
                ConfigurableApplicationContext context = run(CodeEnabledApplication.class, classLoader,
                        "--spring.profiles.active=feature-plan")) {
            assertWebDisabledByTheSinglePlan(context);
        }
    }

    @Test
    void externalConfigurationCanDisableFeatureAvailableInManifest() throws Exception {
        Path external = this.tempDir.resolve("external-feature-plan.properties");
        Files.writeString(external, "coco.features.disabled=web\n", StandardCharsets.UTF_8);
        try (URLClassLoader classLoader = manifestClassLoader(StandardCocoFeatures.resolve(CocoFeatureSelection.empty()),
                Map.of());
                ConfigurableApplicationContext context = run(CodeEnabledApplication.class, classLoader,
                        "--spring.config.additional-location=" + external.toUri())) {
            assertWebDisabledByTheSinglePlan(context);
        }
    }

    @Test
    void commandLineCanDisableFeatureAvailableInManifest() throws Exception {
        try (URLClassLoader classLoader = manifestClassLoader(StandardCocoFeatures.resolve(CocoFeatureSelection.empty()),
                Map.of());
                ConfigurableApplicationContext context = run(CodeEnabledApplication.class, classLoader,
                        "--coco.features.disabled=web")) {
            assertWebDisabledByTheSinglePlan(context);
        }
    }

    @Test
    void laterEnvironmentPostProcessorParticipatesBeforePlanIsFrozen() throws Exception {
        String processorClassName = LateFeatureEnvironmentPostProcessor.class.getName();
        try (URLClassLoader classLoader = manifestClassLoader(StandardCocoFeatures.resolve(CocoFeatureSelection.empty()),
                Map.of("META-INF/spring.factories",
                        "org.springframework.boot.EnvironmentPostProcessor=" + processorClassName + "\n"));
                ConfigurableApplicationContext context = run(RuntimeConfigurationApplication.class, classLoader)) {
            assertWebDisabledByTheSinglePlan(context);
        }
    }

    @Test
    void primarySourceAnnotationDrivesConditionAndManager() {
        try (ConfigurableApplicationContext context = run(CodeSelectedApplication.class,
                getClass().getClassLoader())) {
            assertWebDisabledByTheSinglePlan(context);
        }
    }

    @Test
    void runtimeCannotEnableFeatureRemovedByBuildManifest() throws Exception {
        CocoFeaturePlan availability = StandardCocoFeatures.resolve(
                CocoFeatureSelection.ofDisabled(java.util.Set.of(CocoFeature.WEB)));
        try (URLClassLoader classLoader = manifestClassLoader(availability, Map.of())) {
            assertThatThrownBy(() -> {
                try (ConfigurableApplicationContext ignored = run(RuntimeConfigurationApplication.class, classLoader,
                        "--coco.features.enabled=web")) {
                    // Startup must fail before a usable context is returned.
                }
            }).satisfies(error -> assertThat(rootCause(error).getMessage())
                    .contains("web", "not available", "feature manifest"));
        }
    }

    @Test
    void lateCocoConfigurerSelectionFailsInsteadOfDivergingFromConditions() {
        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext ignored = run(LateConfigurerApplication.class,
                    getClass().getClassLoader())) {
                // Startup must fail instead of keeping a conditionally created Web bean.
            }
        }).satisfies(error -> assertThat(rootCause(error).getMessage())
                .contains("CocoConfigurer", "startup", "feature plan"));
    }

    @Test
    void lateAnnotationSelectionFailsInsteadOfDivergingFromConditions() {
        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext ignored = run(LateAnnotationApplication.class,
                    getClass().getClassLoader())) {
                // Startup must fail instead of silently ignoring a late annotation.
            }
        }).satisfies(error -> assertThat(rootCause(error).getMessage())
                .contains("@CocoFeatures", "primary", "feature plan"));
    }

    @Test
    void customFeaturePlanBeanFailsInsteadOfDivergingFromConditions() {
        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext ignored = run(CustomPlanApplication.class,
                    getClass().getClassLoader())) {
                // Startup must fail because conditions cannot consume a regular plan Bean.
            }
        }).satisfies(error -> assertThat(rootCause(error).getMessage())
                .contains("custom CocoFeaturePlan", "startup feature plan", "conditions"));
    }

    @Test
    void customFeatureManagerFailsWhenItDisagreesWithConditions() {
        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext ignored = run(CustomManagerApplication.class,
                    getClass().getClassLoader())) {
                // Startup must fail because the custom manager reports a different plan.
            }
        }).satisfies(error -> assertThat(rootCause(error).getMessage())
                .contains("custom CocoFeatureManager", "startup feature plan", "conditions"));
    }

    @Test
    void customFeatureManagerFailsWhenItsCollectionViewsDisagreeWithPlan() {
        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext ignored = run(InconsistentManagerViewsApplication.class,
                    getClass().getClassLoader())) {
                // All public manager views must describe the startup feature plan.
            }
        }).satisfies(error -> assertThat(rootCause(error).getMessage())
                .contains("custom CocoFeatureManager", "startup feature plan", "conditions"));
    }

    private void assertWebDisabledByTheSinglePlan(ConfigurableApplicationContext context) {
        assertThat(context.containsBean("webFeatureBean")).isFalse();
        CocoFeaturePlan plan = context.getBean(CocoFeaturePlan.class);
        CocoFeatureManager manager = context.getBean(CocoFeatureManager.class);
        assertThat(manager.isEnabled(CocoFeature.WEB)).isFalse();
        assertThat(((DefaultCocoFeatureManager) manager).featurePlan()).isSameAs(plan);
    }

    private URLClassLoader manifestClassLoader(CocoFeaturePlan plan, Map<String, String> resources) throws Exception {
        Path root = Files.createTempDirectory(this.tempDir, "feature-plan-classpath-");
        Path manifest = root.resolve(CocoFeatureManifestLoader.MANIFEST_LOCATION);
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest,
                CocoFeatureManifestLoader.write(StandardCocoFeatures.toManifest(plan, "integration-test")),
                StandardCharsets.UTF_8);
        for (Map.Entry<String, String> resource : resources.entrySet()) {
            Path target = root.resolve(resource.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, resource.getValue(), StandardCharsets.UTF_8);
        }
        return new URLClassLoader(new java.net.URL[] { root.toUri().toURL() }, getClass().getClassLoader());
    }

    private ConfigurableApplicationContext run(Class<?> source, ClassLoader classLoader, String... arguments) {
        SpringApplication application = new SpringApplication(new DefaultResourceLoader(classLoader), source);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setWebApplicationType(WebApplicationType.NONE);
        return application.run(arguments);
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class RuntimeConfigurationApplication {

        @Bean
        @ConditionalOnCocoFeature(CocoFeature.WEB)
        String webFeatureBean() {
            return "web";
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @CocoFeatures(disabled = CocoFeature.WEB)
    static class CodeSelectedApplication {

        @Bean
        @ConditionalOnCocoFeature(CocoFeature.WEB)
        String webFeatureBean() {
            return "web";
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @CocoFeatures(enabled = CocoFeature.WEB)
    static class CodeEnabledApplication {

        @Bean
        @ConditionalOnCocoFeature(CocoFeature.WEB)
        String webFeatureBean() {
            return "web";
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class LateConfigurerApplication {

        @Bean
        CocoConfigurer webDisablingConfigurer() {
            return new CocoConfigurer() {

                @Override
                public void configureFeatures(CocoFeatureRegistry features) {
                    features.disable(CocoFeature.WEB);
                }
            };
        }

        @Bean
        @ConditionalOnCocoFeature(CocoFeature.WEB)
        String webFeatureBean() {
            return "web";
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(LateFeatureConfiguration.class)
    static class LateAnnotationApplication {

        @Bean
        @ConditionalOnCocoFeature(CocoFeature.WEB)
        String webFeatureBean() {
            return "web";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @CocoFeatures(disabled = CocoFeature.WEB)
    static class LateFeatureConfiguration {
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class CustomPlanApplication {

        @Bean
        CocoFeaturePlan customFeaturePlan() {
            return StandardCocoFeatures.resolve(
                    CocoFeatureSelection.ofDisabled(java.util.Set.of(CocoFeature.WEB)));
        }

        @Bean
        @ConditionalOnCocoFeature(CocoFeature.WEB)
        String webFeatureBean() {
            return "web";
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class CustomManagerApplication {

        @Bean
        CocoFeatureManager customFeatureManager() {
            return new DefaultCocoFeatureManager(java.util.Set.of(CocoFeature.WEB));
        }

        @Bean
        @ConditionalOnCocoFeature(CocoFeature.WEB)
        String webFeatureBean() {
            return "web";
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class InconsistentManagerViewsApplication {

        @Bean
        CocoFeatureManager inconsistentFeatureManager() {
            CocoFeaturePlan plan = StandardCocoFeatures.resolve(CocoFeatureSelection.empty());
            return new CocoFeatureManager() {

                @Override
                public boolean isEnabled(CocoFeature feature) {
                    return plan.isEnabled(feature);
                }

                @Override
                public java.util.Set<CocoFeature> enabledFeatures() {
                    return java.util.Set.of();
                }

                @Override
                public java.util.Set<CocoFeature> disabledFeatures() {
                    return java.util.Set.of();
                }
            };
        }
    }

    public static final class LateFeatureEnvironmentPostProcessor
            implements EnvironmentPostProcessor, Ordered {

        @Override
        public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
            environment.getPropertySources().addFirst(new MapPropertySource(
                    "lateFeatureSelection", Map.of("coco.features.disabled", "web")));
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }
}
