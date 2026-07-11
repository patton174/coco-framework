package io.github.coco.feature.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.i18n.CocoMessageService;
import io.github.coco.feature.codegen.core.CocoCodeGenerator;
import io.github.coco.feature.codegen.core.CocoCodegenRequest;
import io.github.coco.feature.codegen.core.CocoCodegenResult;
import io.github.coco.feature.codegen.core.CocoGeneratedFile;
import io.github.coco.feature.codegen.core.CocoGeneratedFileWriter;
import io.github.coco.feature.codegen.crud.CocoCrudIdStrategy;
import io.github.coco.feature.codegen.crud.CocoCrudSpec;
import io.github.coco.feature.codegen.template.FreeMarkerCocoCodeGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Coco 代码生成功能自动配置测试。
 * <p>
 * 验证代码生成功能模块可以注册消息资源、绑定配置属性，并提供可替换的真实模板生成器。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-codegen}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoCodegenAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoCodegenAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    @Test
    void registersCodegenMessageBundle() {
        this.contextRunner.run(context -> {
            CocoMessageService messageService = context.getBean(CocoMessageService.class);

            assertTrue(context.containsBean("cocoCodegenMessageBundleRegistrar"));
            assertEquals("Coco 代码生成功能消息资源已就绪。",
                    messageService.getMessage("coco.feature.codegen.ready"));
        });
    }

    @Test
    void createsDefaultTemplateGeneratorAndWriter() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CocoCodegenProperties.class);
            assertThat(context).hasSingleBean(CocoCodeGenerator.class);
            assertThat(context).hasSingleBean(CocoGeneratedFileWriter.class);
            assertThat(context.getBean(CocoCodeGenerator.class)).isInstanceOf(FreeMarkerCocoCodeGenerator.class);

            CocoCodegenResult result = context.getBean(CocoCodeGenerator.class).generate(
                    CocoCrudSpec.builder("io.github.sample", "Order", "sample_order")
                            .id("id", "id", "Long", CocoCrudIdStrategy.AUTO)
                            .field("name", "name", "String", true)
                            .build()
                            .toRequest());
            assertThat(result.files()).hasSize(10);
        });
    }

    @Test
    void bindsCodegenTemplateProperties() {
        this.contextRunner
                .withPropertyValues(
                        "coco.codegen.templates.location=file:/workspace/templates",
                        "coco.codegen.templates.encoding=GBK")
                .run(context -> {
                    CocoCodegenProperties properties = context.getBean(CocoCodegenProperties.class);

                    assertThat(properties.getTemplates().getLocation()).isEqualTo("file:/workspace/templates");
                    assertThat(properties.getTemplates().getEncoding()).isEqualTo("GBK");
                });
    }

    @Test
    void configuredTemplateLocationReplacesBuiltInTemplates(@TempDir Path templates) throws IOException {
        Path group = templates.resolve("custom");
        Files.createDirectories(group);
        Files.writeString(group.resolve("manifest.properties"), """
                group=custom
                template.count=1
                template.0.source=sample.ftl
                template.0.output=generated/${name}.java
                """);
        Files.writeString(group.resolve("sample.ftl"), "class ${name} {}");

        this.contextRunner
                .withPropertyValues("coco.codegen.templates.location=" + templates.toUri())
                .run(context -> {
                    CocoCodegenResult result = context.getBean(CocoCodeGenerator.class)
                            .generate(CocoCodegenRequest.builder("custom")
                                    .attribute("name", "Sample")
                                    .build());

                    assertThat(result.files()).containsExactly(
                            new CocoGeneratedFile("generated/Sample.java", "class Sample {}"));
                });
    }

    @Test
    void backsOffWhenCustomCodeGeneratorExists() {
        this.contextRunner
                .withUserConfiguration(CustomCodegenConfiguration.class)
                .run(context -> {
                    CocoCodeGenerator generator = context.getBean(CocoCodeGenerator.class);
                    CocoCodegenResult result = generator.generate(CocoCodegenRequest.builder("crud").build());

                    assertThat(generator).isNotInstanceOf(FreeMarkerCocoCodeGenerator.class);
                    assertThat(result.files())
                            .containsExactly(new CocoGeneratedFile("src/main/java/Sample.java", "class Sample {}"));
                });
    }

    @Test
    void customCodeGeneratorUsesBoundTemplatePropertiesAndNormalizedRequest() {
        this.contextRunner
                .withUserConfiguration(TemplateAwareCodegenConfiguration.class)
                .withPropertyValues(
                        "coco.codegen.templates.location=file:/workspace/templates",
                        "coco.codegen.templates.encoding=UTF-16")
                .run(context -> {
                    CocoCodeGenerator generator = context.getBean(CocoCodeGenerator.class);
                    CocoCodegenResult result = generator.generate(CocoCodegenRequest.builder(" crud ")
                            .targetPackage(" io.github.sample ")
                            .attribute(" entity ", "Order")
                            .attribute(" ", "ignored")
                            .attribute("ignored", null)
                            .build());

                    assertThat(generator).isNotInstanceOf(FreeMarkerCocoCodeGenerator.class);
                    assertThat(result.files()).containsExactly(new CocoGeneratedFile("generated/Order.java",
                            "group=crud;package=io.github.sample;templates=file:/workspace/templates;encoding=UTF-16"));
                });
    }

    @Test
    void codegenRequestAndResultModelsNormalizeAndValidateInput() {
        CocoCodegenRequest request = CocoCodegenRequest.builder(" crud ")
                .targetPackage(" io.github.sample ")
                .attribute(" entity ", "Order")
                .attribute(" ", "ignored")
                .attribute("ignored", null)
                .build();
        CocoGeneratedFile generatedFile = new CocoGeneratedFile(" generated/Order.java ", null);
        CocoCodegenResult result = CocoCodegenResult.of(Arrays.asList(null, generatedFile));

        assertThat(request.templateGroup()).isEqualTo("crud");
        assertThat(request.targetPackage()).isEqualTo("io.github.sample");
        assertThat(request.attributes()).containsOnlyKeys("entity");
        assertThat(request.attributes()).containsEntry("entity", "Order");
        assertThat(generatedFile.path()).isEqualTo("generated/Order.java");
        assertThat(generatedFile.content()).isEmpty();
        assertThat(result.files()).containsExactly(generatedFile);
        assertThrows(UnsupportedOperationException.class,
                () -> request.attributes().put("another", "value"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.files().add(new CocoGeneratedFile("generated/Another.java", "")));
        assertThrows(IllegalArgumentException.class, () -> CocoCodegenRequest.builder(" ").build());
        assertThrows(IllegalArgumentException.class, () -> new CocoGeneratedFile(" ", ""));
    }

    @Test
    void disablesCodegenInfrastructure() {
        this.contextRunner
                .withPropertyValues("coco.codegen.enabled=false")
                .run(context -> {
                    assertTrue(context.containsBean("cocoCodegenMessageBundleRegistrar"));
                    assertThat(context).doesNotHaveBean(CocoCodeGenerator.class);
                    assertThat(context).doesNotHaveBean(CocoGeneratedFileWriter.class);
                });
    }

    @Test
    void backsOffWhenCodegenFeatureIsDisabled() {
        this.contextRunner
                .withPropertyValues("coco.features.disabled[0]=codegen")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("cocoCodegenMessageBundleRegistrar");
                    assertThat(context).doesNotHaveBean(CocoCodeGenerator.class);
                    assertThat(context).doesNotHaveBean(CocoGeneratedFileWriter.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomCodegenConfiguration {

        @Bean
        CocoCodeGenerator customCocoCodeGenerator() {
            return request -> CocoCodegenResult.of(
                    List.of(new CocoGeneratedFile("src/main/java/Sample.java", "class Sample {}")));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TemplateAwareCodegenConfiguration {

        @Bean
        CocoCodeGenerator templateAwareCocoCodeGenerator(CocoCodegenProperties properties) {
            return request -> CocoCodegenResult.of(List.of(new CocoGeneratedFile(
                    "generated/" + request.attributes().get("entity") + ".java",
                    "group=" + request.templateGroup()
                            + ";package=" + request.targetPackage()
                            + ";templates=" + properties.getTemplates().getLocation()
                            + ";encoding=" + properties.getTemplates().getEncoding())));
        }
    }
}
