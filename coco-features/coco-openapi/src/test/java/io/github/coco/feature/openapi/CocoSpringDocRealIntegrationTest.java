package io.github.coco.feature.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.feature.openapi.core.CocoOpenApiMetadata;
import io.github.coco.feature.openapi.core.CocoOpenApiMetadataProvider;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Coco 与真实 SpringDoc OpenAPI API 的集成契约测试。
 * <p>
 * 测试 classpath 使用项目支持的真实 SpringDoc 版本，不在 SpringDoc 或 Swagger 包下声明测试桩。
 * </p>
 */
class CocoSpringDocRealIntegrationTest {

    private static final String AUTO_CONFIGURATION_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoOpenApiAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    @Test
    void integratesWithRealSupportedSpringDocApi() {
        this.contextRunner.run(context -> {
            assertThat(OpenApiCustomizer.class.getProtectionDomain().getCodeSource().getLocation().toString())
                    .contains("springdoc-openapi-starter-common")
                    .doesNotContain("test-classes");
            assertThat(context.getBeansOfType(OpenApiCustomizer.class))
                    .containsOnlyKeys("cocoSpringDocOpenApiCustomizer");

            OpenApiCustomizer customizer = context.getBean(
                    "cocoSpringDocOpenApiCustomizer", OpenApiCustomizer.class);
            OpenAPI openApi = new OpenAPI();

            customizer.customise(openApi);

            assertThat(openApi.getInfo()).isNotNull();
            assertThat(openApi.getInfo().getTitle()).isEqualTo("Coco API");
            assertThat(openApi.getInfo().getVersion()).isEqualTo("1.0.0");
            assertThat(openApi.getInfo().getDescription()).isEqualTo("Coco Framework API");
            assertThat(openApi.getComponents()).isNotNull();
            assertThat(openApi.getComponents().getSchemas())
                    .containsKeys("CocoApiResponse", "CocoApiErrorResponse");
        });
    }

    @Test
    void keepsMetadataProviderWhenSpringDocIsAbsentWithoutRegisteringCustomizer() {
        this.contextRunner
                .withClassLoader(new FilteredClassLoader(OpenApiCustomizer.class, OpenAPI.class, Info.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoOpenApiMetadataProvider.class);
                    assertThat(context).doesNotHaveBean("cocoSpringDocOpenApiCustomizer");
                });
    }

    @Test
    void doesNotRegisterAdapterWhenRequiredSpringDocModelIsMissing() {
        this.contextRunner
                .withClassLoader(new FilteredClassLoader(ObjectSchema.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoOpenApiMetadataProvider.class);
                    assertThat(context).doesNotHaveBean("cocoSpringDocOpenApiCustomizer");
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesBoundedResponseSchemasWithoutRequestOrSecurityContext() {
        this.contextRunner.run(context -> {
            OpenApiCustomizer customizer = context.getBean(
                    "cocoSpringDocOpenApiCustomizer", OpenApiCustomizer.class);
            OpenAPI openApi = new OpenAPI();

            customizer.customise(openApi);

            assertThat(openApi.getComponents().getSchemas().get("CocoApiResponse").getProperties().keySet())
                    .containsExactlyInAnyOrder("success", "code", "message", "data")
                    .allSatisfy(property -> assertThat(String.valueOf(property))
                            .doesNotMatch("(?i).*(tenant|user|key|nonce|path|trace|token|secret).*"));
            assertThat(openApi.getComponents().getSchemas().get("CocoApiErrorResponse").getProperties().keySet())
                    .containsExactlyInAnyOrder("success", "code", "message", "data")
                    .allSatisfy(property -> assertThat(String.valueOf(property))
                            .doesNotMatch("(?i).*(tenant|user|key|nonce|path|trace|token|secret).*"));
            assertThat(openApi.getComponents().getSchemas().get("CocoApiErrorResponse").getRequired())
                    .containsExactlyInAnyOrder("success", "code", "message");
        });
    }

    @Test
    void canDisableResponseSchemaPublicationWithoutDisablingMetadataCustomization() {
        this.contextRunner
                .withPropertyValues("coco.openapi.springdoc.response-schemas-enabled=false")
                .run(context -> {
                    OpenApiCustomizer customizer = context.getBean(
                            "cocoSpringDocOpenApiCustomizer", OpenApiCustomizer.class);
                    OpenAPI openApi = new OpenAPI();

                    customizer.customise(openApi);

                    assertThat(openApi.getInfo().getTitle()).isEqualTo("Coco API");
                    assertThat(openApi.getComponents()).isNull();
                });
    }

    @Test
    void customizesMetadataWithoutSchemaClassesWhenResponseSchemasAreDisabled() {
        this.contextRunner
                .withPropertyValues("coco.openapi.springdoc.response-schemas-enabled=false")
                .withClassLoader(new FilteredClassLoader(ObjectSchema.class))
                .run(context -> {
                    OpenApiCustomizer customizer = context.getBean(
                            "cocoSpringDocOpenApiCustomizer", OpenApiCustomizer.class);
                    OpenAPI openApi = new OpenAPI();

                    customizer.customise(openApi);

                    assertThat(openApi.getInfo().getTitle()).isEqualTo("Coco API");
                    assertThat(openApi.getComponents()).isNull();
                });
    }

    @Test
    void backsOffWhenApplicationDefinesNamedSpringDocCustomizer() {
        this.contextRunner
                .withUserConfiguration(CustomSpringDocCustomizerConfiguration.class)
                .run(context -> {
                    assertThat(context.getBeansOfType(OpenApiCustomizer.class))
                            .containsOnlyKeys("cocoSpringDocOpenApiCustomizer");

                    OpenAPI openApi = new OpenAPI();
                    context.getBean(OpenApiCustomizer.class).customise(openApi);

                    assertThat(openApi.getInfo().getTitle()).isEqualTo("Application API");
                });
    }

    @Test
    void customMetadataProviderDrivesRealSpringDocCustomizer() {
        this.contextRunner
                .withUserConfiguration(CustomMetadataProviderConfiguration.class)
                .run(context -> {
                    OpenApiCustomizer customizer = context.getBean(
                            "cocoSpringDocOpenApiCustomizer", OpenApiCustomizer.class);
                    OpenAPI openApi = new OpenAPI();

                    customizer.customise(openApi);

                    assertThat(context.getBean(CocoOpenApiMetadataProvider.class).metadata().title())
                            .isEqualTo("Custom API");
                    assertThat(openApi.getInfo().getTitle()).isEqualTo("Custom API");
                    assertThat(openApi.getInfo().getVersion()).isEqualTo("9.9.9");
                    assertThat(openApi.getInfo().getDescription()).isEqualTo("Custom description");
                });
    }

    @Test
    void customizesExistingInfoWithoutDiscardingOtherSpringDocMetadata() {
        this.contextRunner
                .withPropertyValues(
                        "coco.openapi.info.title=Configured API",
                        "coco.openapi.info.version=2.4.0",
                        "coco.openapi.info.description=Configured description")
                .run(context -> {
                    OpenApiCustomizer customizer = context.getBean(
                            "cocoSpringDocOpenApiCustomizer", OpenApiCustomizer.class);
                    Contact contact = new Contact().name("API team");
                    Info existingInfo = new Info()
                            .title("Existing title")
                            .version("0.1.0")
                            .description("Existing description")
                            .termsOfService("https://example.test/terms")
                            .contact(contact);
                    OpenAPI openApi = new OpenAPI().info(existingInfo);

                    customizer.customise(openApi);

                    assertThat(openApi.getInfo()).isSameAs(existingInfo);
                    assertThat(existingInfo.getTitle()).isEqualTo("Configured API");
                    assertThat(existingInfo.getVersion()).isEqualTo("2.4.0");
                    assertThat(existingInfo.getDescription()).isEqualTo("Configured description");
                    assertThat(existingInfo.getTermsOfService()).isEqualTo("https://example.test/terms");
                    assertThat(existingInfo.getContact()).isSameAs(contact);
                });
    }

    @Test
    void doesNotRegisterOpenApiBeansWhenFeatureIsDisabled() {
        this.contextRunner
                .withPropertyValues("coco.features.disabled[0]=openapi")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CocoOpenApiMetadataProvider.class);
                    assertThat(context).doesNotHaveBean("cocoSpringDocOpenApiCustomizer");
                    assertThat(context.getBeansOfType(OpenApiCustomizer.class)).isEmpty();
                });
    }

    @Test
    void autoConfigurationImportsDeclaresOpenApiExactlyOnce() throws IOException {
        String autoConfigurationClassName = CocoOpenApiAutoConfiguration.class.getName();
        Enumeration<URL> resources = getClass().getClassLoader().getResources(AUTO_CONFIGURATION_IMPORTS);
        long matchingImports = 0;

        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    resource.openStream(), StandardCharsets.UTF_8))) {
                matchingImports += reader.lines()
                        .map(String::trim)
                        .filter(autoConfigurationClassName::equals)
                        .count();
            }
        }

        assertThat(matchingImports).isOne();
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomMetadataProviderConfiguration {

        @Bean
        CocoOpenApiMetadataProvider customCocoOpenApiMetadataProvider() {
            return () -> new CocoOpenApiMetadata("Custom API", "9.9.9", "Custom description");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomSpringDocCustomizerConfiguration {

        @Bean(name = "cocoSpringDocOpenApiCustomizer")
        OpenApiCustomizer applicationOpenApiCustomizer() {
            return openApi -> openApi.info(new Info().title("Application API"));
        }
    }
}
