package io.github.coco.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.coco.spring.boot.autoconfigure.i18n.CocoI18nAutoConfiguration;
import io.github.coco.i18n.CocoMessageService;
import io.github.coco.openapi.core.CocoOpenApiMetadata;
import io.github.coco.openapi.core.CocoOpenApiMetadataProvider;
import io.github.coco.openapi.core.DefaultCocoOpenApiMetadataProvider;
import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Coco OpenAPI 功能自动配置测试。
 * <p>
 * 验证 OpenAPI 功能模块可以注册消息资源、绑定配置属性，并提供可替换的文档元数据 SPI。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-openapi}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoOpenApiAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoI18nAutoConfiguration.class,
                    CocoOpenApiAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    @Test
    void registersOpenApiMessageBundle() {
        this.contextRunner.run(context -> {
            CocoMessageService messageService = context.getBean(CocoMessageService.class);

            assertTrue(context.containsBean("cocoOpenApiMessageBundleRegistrar"));
            assertEquals("Coco OpenAPI 功能消息资源已就绪。",
                    messageService.getMessage("coco.feature.openapi.ready"));
        });
    }

    @Test
    void createsDefaultOpenApiMetadataProvider() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CocoOpenApiProperties.class);
            assertThat(context).hasSingleBean(CocoOpenApiMetadataProvider.class);
            assertThat(context.getBean(CocoOpenApiMetadataProvider.class))
                    .isInstanceOf(DefaultCocoOpenApiMetadataProvider.class);

            CocoOpenApiMetadata metadata = context.getBean(CocoOpenApiMetadataProvider.class).metadata();
            assertThat(metadata.title()).isEqualTo("Coco API");
            assertThat(metadata.version()).isEqualTo("1.0.0");
            assertThat(metadata.descriptionOptional()).contains("Coco Framework API");
        });
    }

    @Test
    void bindsOpenApiMetadataProperties() {
        this.contextRunner
                .withPropertyValues(
                        "coco.openapi.info.title=Sample API",
                        "coco.openapi.info.version=2.1.0",
                        "coco.openapi.info.description=Sample description")
                .run(context -> {
                    CocoOpenApiMetadata metadata = context.getBean(CocoOpenApiMetadataProvider.class).metadata();

                    assertThat(metadata.title()).isEqualTo("Sample API");
                    assertThat(metadata.version()).isEqualTo("2.1.0");
                    assertThat(metadata.descriptionOptional()).contains("Sample description");
                });
    }

    @Test
    void adaptsMetadataToSpringDocWhenSpringDocIsPresent() {
        this.contextRunner
                .withPropertyValues(
                        "coco.openapi.info.title=SpringDoc API",
                        "coco.openapi.info.version=3.0.0",
                        "coco.openapi.info.description=SpringDoc description")
                .run(context -> {
                    assertThat(context).hasBean("cocoSpringDocOpenApiCustomizer");
                    OpenApiCustomizer customizer =
                            context.getBean("cocoSpringDocOpenApiCustomizer", OpenApiCustomizer.class);
                    OpenAPI openApi = new OpenAPI();

                    customizer.customise(openApi);

                    assertThat(openApi.getInfo()).isNotNull();
                    assertThat(openApi.getInfo().getTitle()).isEqualTo("SpringDoc API");
                    assertThat(openApi.getInfo().getVersion()).isEqualTo("3.0.0");
                    assertThat(openApi.getInfo().getDescription()).isEqualTo("SpringDoc description");
                });
    }

    @Test
    void disablesSpringDocMetadataAdapter() {
        this.contextRunner
                .withPropertyValues("coco.openapi.springdoc.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean("cocoSpringDocOpenApiCustomizer"));
    }

    @Test
    void normalizesBlankOpenApiMetadataProperties() {
        this.contextRunner
                .withPropertyValues(
                        "coco.openapi.info.title=   ",
                        "coco.openapi.info.version=   ",
                        "coco.openapi.info.description=   ")
                .run(context -> {
                    CocoOpenApiMetadata metadata = context.getBean(CocoOpenApiMetadataProvider.class).metadata();

                    assertThat(metadata.title()).isEqualTo("Coco API");
                    assertThat(metadata.version()).isEqualTo("1.0.0");
                    assertThat(metadata.descriptionOptional()).isEmpty();
                });
    }

    @Test
    void backsOffWhenCustomOpenApiMetadataProviderExists() {
        this.contextRunner
                .withUserConfiguration(CustomOpenApiConfiguration.class)
                .run(context -> {
                    CocoOpenApiMetadataProvider provider = context.getBean(CocoOpenApiMetadataProvider.class);

                    assertThat(provider.metadata().title()).isEqualTo("Custom API");
                    assertThat(provider).isNotInstanceOf(DefaultCocoOpenApiMetadataProvider.class);

                    OpenApiCustomizer customizer =
                            context.getBean("cocoSpringDocOpenApiCustomizer", OpenApiCustomizer.class);
                    OpenAPI openApi = new OpenAPI();
                    customizer.customise(openApi);
                    assertThat(openApi.getInfo().getTitle()).isEqualTo("Custom API");
                });
    }

    @Test
    void disablesOpenApiMetadataProvider() {
        this.contextRunner
                .withPropertyValues("coco.openapi.enabled=false")
                .run(context -> {
                    assertTrue(context.containsBean("cocoOpenApiMessageBundleRegistrar"));
                    assertThat(context).doesNotHaveBean(CocoOpenApiMetadataProvider.class);
                    assertThat(context).doesNotHaveBean("cocoSpringDocOpenApiCustomizer");
                });
    }

    @Test
    void backsOffWhenOpenApiFeatureIsDisabled() {
        this.contextRunner
                .withPropertyValues("coco.features.disabled[0]=openapi")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("cocoOpenApiMessageBundleRegistrar");
                    assertThat(context).doesNotHaveBean(CocoOpenApiMetadataProvider.class);
                    assertThat(context).doesNotHaveBean("cocoSpringDocOpenApiCustomizer");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomOpenApiConfiguration {

        @Bean
        CocoOpenApiMetadataProvider customCocoOpenApiMetadataProvider() {
            return () -> new CocoOpenApiMetadata("Custom API", "9.9.9", null);
        }
    }
}
