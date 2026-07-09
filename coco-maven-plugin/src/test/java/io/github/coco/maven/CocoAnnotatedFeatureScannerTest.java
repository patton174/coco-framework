package io.github.coco.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.registry.CocoFeatureSelection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Coco 注解功能扫描器测试。
 * <p>
 * 验证构建期扫描器可以从业务项目编译输出中读取 {@code @CocoFeatures}，并忽略不应影响构建的 class 文件。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-maven-plugin}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoAnnotatedFeatureScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void scansTopLevelCocoFeaturesAnnotations() throws Exception {
        Path classesDirectory = compileSources("""
                package com.example;

                import io.github.coco.api.feature.CocoFeature;
                import io.github.coco.api.feature.CocoFeatures;

                @CocoFeatures(
                        enabled = { CocoFeature.TENANT },
                        disabled = { CocoFeature.DATA_PERMISSION })
                public class DemoFeatureConfiguration {

                    @CocoFeatures(enabled = { CocoFeature.CODEGEN })
                    public static class NestedFeatureConfiguration {
                    }
                }
                """);

        CocoFeatureSelection selection = new CocoAnnotatedFeatureScanner()
                .scan(classesDirectory, classpath(classesDirectory));

        assertThat(selection.enabled()).containsExactly(CocoFeature.TENANT);
        assertThat(selection.enabled()).doesNotContain(CocoFeature.CODEGEN);
        assertThat(selection.disabled()).containsExactly(CocoFeature.DATA_PERMISSION);
    }

    @Test
    void ignoresUnreadableApplicationClasses() throws Exception {
        Path classesDirectory = compileSources("""
                package com.example;

                import io.github.coco.api.feature.CocoFeature;
                import io.github.coco.api.feature.CocoFeatures;

                @CocoFeatures(enabled = { CocoFeature.OPENAPI })
                public class DemoFeatureConfiguration {
                }
                """);
        Path brokenClass = classesDirectory.resolve("com/example/BrokenApplication.class");
        Files.writeString(brokenClass, "not a valid class", StandardCharsets.UTF_8);

        CocoFeatureSelection selection = new CocoAnnotatedFeatureScanner()
                .scan(classesDirectory, classpath(classesDirectory));

        assertThat(selection.enabled()).containsExactly(CocoFeature.OPENAPI);
        assertThat(selection.disabled()).isEmpty();
    }

    @Test
    void returnsEmptySelectionForMissingClassesDirectory() {
        CocoFeatureSelection selection = new CocoAnnotatedFeatureScanner()
                .scan(this.tempDir.resolve("missing"), List.of());

        assertThat(selection.enabled()).isEmpty();
        assertThat(selection.disabled()).isEmpty();
    }

    private Path compileSources(String... sources) throws Exception {
        Path sourceDirectory = Files.createDirectories(this.tempDir.resolve("src"));
        Path classesDirectory = Files.createDirectories(this.tempDir.resolve("classes"));
        List<Path> sourceFiles = new ArrayList<>();
        for (int index = 0; index < sources.length; index++) {
            Path sourceFile = sourceDirectory.resolve(index == 0
                    ? "DemoFeatureConfiguration.java"
                    : "DemoFeatureConfiguration" + index + ".java");
            Files.writeString(sourceFile, sources[index], StandardCharsets.UTF_8);
            sourceFiles.add(sourceFile);
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK compiler").isNotNull();
        List<String> arguments = new ArrayList<>();
        arguments.add("-classpath");
        arguments.add(System.getProperty("java.class.path"));
        arguments.add("-d");
        arguments.add(classesDirectory.toString());
        sourceFiles.stream().map(Path::toString).forEach(arguments::add);

        int result = compiler.run(null, null, null, arguments.toArray(String[]::new));
        assertThat(result).isZero();
        return classesDirectory;
    }

    private Collection<URL> classpath(Path classesDirectory) throws Exception {
        return List.of(classesDirectory.toUri().toURL());
    }
}
