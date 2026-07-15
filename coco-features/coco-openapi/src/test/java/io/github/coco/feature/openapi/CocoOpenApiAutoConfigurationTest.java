package io.github.coco.feature.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.i18n.CocoMessageService;
import io.github.coco.feature.openapi.core.CocoOpenApiMetadata;
import io.github.coco.feature.openapi.core.CocoOpenApiMetadataProvider;
import io.github.coco.feature.openapi.core.DefaultCocoOpenApiMetadataProvider;
import io.github.coco.feature.openapi.springdoc.CocoSpringDocOpenApiCustomizerFactoryBean;
import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

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
                    CocoCommonAutoConfiguration.class,
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
                });
    }

    @Test
    void disablesOpenApiMetadataProvider() {
        this.contextRunner
                .withPropertyValues("coco.openapi.enabled=false")
                .run(context -> {
                    assertTrue(context.containsBean("cocoOpenApiMessageBundleRegistrar"));
                    assertThat(context).doesNotHaveBean(CocoOpenApiMetadataProvider.class);
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

    @Test
    void preservesLegacyOneArgumentSpringDocCustomizerDescriptor() throws NoSuchMethodException {
        assertThat(CocoOpenApiAutoConfiguration.class.getMethod("cocoSpringDocOpenApiCustomizer",
                CocoOpenApiMetadataProvider.class).getReturnType())
                .isEqualTo(CocoSpringDocOpenApiCustomizerFactoryBean.class);
    }

    @Test
    void executesOldBytecodeLinkedToTheOneArgumentSpringDocCustomizer(@TempDir Path temporaryDirectory)
            throws Exception {
        Path classesDirectory = temporaryDirectory.resolve("classes");
        compile(classesDirectory, classPathFor(CocoOpenApiMetadataProvider.class), List.of(
                source("io.github.coco.feature.openapi.CocoOpenApiAutoConfiguration", """
                        package io.github.coco.feature.openapi;
                        import io.github.coco.feature.openapi.core.CocoOpenApiMetadataProvider;
                        import io.github.coco.feature.openapi.springdoc.CocoSpringDocOpenApiCustomizerFactoryBean;
                        public class CocoOpenApiAutoConfiguration {
                            public CocoSpringDocOpenApiCustomizerFactoryBean cocoSpringDocOpenApiCustomizer(
                                    CocoOpenApiMetadataProvider metadataProvider) {
                                return null;
                            }
                        }
                        """),
                source("legacy.OpenApiClient", """
                        package legacy;
                        import io.github.coco.feature.openapi.CocoOpenApiAutoConfiguration;
                        import io.github.coco.feature.openapi.core.CocoOpenApiMetadataProvider;
                        public final class OpenApiClient {
                            public static Object create(CocoOpenApiMetadataProvider metadataProvider) {
                                return new CocoOpenApiAutoConfiguration()
                                        .cocoSpringDocOpenApiCustomizer(metadataProvider);
                            }
                        }
                        """)));

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] { classesDirectory.toUri().toURL() },
                getClass().getClassLoader())) {
            Class<?> client = classLoader.loadClass("legacy.OpenApiClient");
            Object factoryBean = client.getMethod("create", CocoOpenApiMetadataProvider.class)
                    .invoke(null, (CocoOpenApiMetadataProvider) () -> new CocoOpenApiMetadata("Legacy", "1", null));

            assertThat(factoryBean).isInstanceOf(CocoSpringDocOpenApiCustomizerFactoryBean.class);
        }
    }

    private static void compile(Path classesDirectory, String classPath, List<JavaFileObject> sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK compiler").isNotNull();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            boolean compiled = compiler.getTask(null, fileManager, null,
                    List.of("-classpath", classPath, "-d", classesDirectory.toString()), null, sources).call();
            assertThat(compiled).isTrue();
        }
        catch (Exception ex) {
            throw new IllegalStateException("Unable to compile compatibility fixture", ex);
        }
    }

    private static JavaFileObject source(String className, String source) {
        return new SimpleJavaFileObject(URI.create("string:///" + className.replace('.', '/') + ".java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
    }

    private static String classPathFor(Class<?> type) throws Exception {
        return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomOpenApiConfiguration {

        @Bean
        CocoOpenApiMetadataProvider customCocoOpenApiMetadataProvider() {
            return () -> new CocoOpenApiMetadata("Custom API", "9.9.9", null);
        }
    }
}
