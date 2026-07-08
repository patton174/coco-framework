package io.github.coco.feature.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.common.i18n.api.CocoMessageService;
import io.github.coco.feature.codegen.core.CocoCodeGenerator;
import io.github.coco.feature.codegen.core.CocoCodegenRequest;
import io.github.coco.feature.codegen.core.CocoCodegenResult;
import io.github.coco.feature.codegen.core.CocoGeneratedFile;
import io.github.coco.feature.codegen.core.NoOpCocoCodeGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Coco 代码生成功能自动配置测试。
 * <p>
 * 验证代码生成功能模块可以注册消息资源、绑定配置属性，并提供可替换的生成器 SPI。
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
    void createsDefaultCodeGenerator() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CocoCodegenProperties.class);
            assertThat(context).hasSingleBean(CocoCodeGenerator.class);
            assertThat(context.getBean(CocoCodeGenerator.class)).isInstanceOf(NoOpCocoCodeGenerator.class);

            CocoCodegenResult result = context.getBean(CocoCodeGenerator.class)
                    .generate(CocoCodegenRequest.builder("crud").targetPackage("io.github.sample").build());
            assertThat(result.hasFiles()).isFalse();
            assertThat(result.files()).isEmpty();
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
    void backsOffWhenCustomCodeGeneratorExists() {
        this.contextRunner
                .withUserConfiguration(CustomCodegenConfiguration.class)
                .run(context -> {
                    CocoCodeGenerator generator = context.getBean(CocoCodeGenerator.class);
                    CocoCodegenResult result = generator.generate(CocoCodegenRequest.builder("crud").build());

                    assertThat(generator).isNotInstanceOf(NoOpCocoCodeGenerator.class);
                    assertThat(result.files())
                            .containsExactly(new CocoGeneratedFile("src/main/java/Sample.java", "class Sample {}"));
                });
    }

    @Test
    void disablesCodeGenerator() {
        this.contextRunner
                .withPropertyValues("coco.codegen.enabled=false")
                .run(context -> {
                    assertTrue(context.containsBean("cocoCodegenMessageBundleRegistrar"));
                    assertThat(context).doesNotHaveBean(CocoCodeGenerator.class);
                });
    }

    @Test
    void backsOffWhenCodegenFeatureIsDisabled() {
        this.contextRunner
                .withPropertyValues("coco.features.disabled[0]=codegen")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("cocoCodegenMessageBundleRegistrar");
                    assertThat(context).doesNotHaveBean(CocoCodeGenerator.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomCodegenConfiguration {

        @Bean
        CocoCodeGenerator customCocoCodeGenerator() {
            return request -> CocoCodegenResult.of(
                    java.util.List.of(new CocoGeneratedFile("src/main/java/Sample.java", "class Sample {}")));
        }
    }
}
