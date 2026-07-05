package io.github.coco.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import io.github.coco.feature.registry.CocoFeatureManifest;
import io.github.coco.feature.registry.CocoFeatureManifestLoader;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Coco 功能装配 Mojo 测试。
 * <p>
 * 验证 Maven 插件会生成构建清单，并把启用的功能模块写入 Maven 项目模型。
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
class CocoFeaturesMojoTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesManifestAndAppliesEnabledFeatureDependencies() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("project"));
        Path resources = Files.createDirectories(baseDir.resolve("src/main/resources"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        Files.writeString(resources.resolve("application.yml"), """
                coco:
                  features:
                    exclude:
                      - tenant
                      - data-permission
                """, StandardCharsets.UTF_8);

        MavenProject project = project(baseDir, output);
        CocoFeaturesMojo mojo = new CocoFeaturesMojo();
        set(mojo, "project", project);
        set(mojo, "outputDirectory", output.toFile());
        set(mojo, "classesDirectory", output.toFile());
        set(mojo, "featureGroupId", "io.github.patton174");
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");

        mojo.execute();

        CocoFeatureManifest manifest = CocoFeatureManifestLoader.read(
                Files.newInputStream(output.resolve(CocoFeatureManifestLoader.MANIFEST_LOCATION)));
        assertThat(manifest.enabledFeatureIds()).contains("web", "mybatis-plus", "audit", "security", "openapi", "codegen");
        assertThat(manifest.enabledFeatureIds()).doesNotContain("tenant", "data-permission");
        assertThat(project.getModel().getDependencies())
                .extracting(dependency -> dependency.getGroupId() + ":" + dependency.getArtifactId())
                .contains("io.github.patton174:coco-feature-web")
                .doesNotContain("io.github.patton174:coco-feature-tenant");
    }

    @Test
    void skipsPomPackagingProjects() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("parent"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        project.setPackaging("pom");
        CocoFeaturesMojo mojo = new CocoFeaturesMojo();
        set(mojo, "project", project);
        set(mojo, "outputDirectory", output.toFile());
        set(mojo, "classesDirectory", output.toFile());

        mojo.execute();

        assertThat(output.resolve(CocoFeatureManifestLoader.MANIFEST_LOCATION)).doesNotExist();
        assertThat(project.getModel().getDependencies()).isEmpty();
    }

    @Test
    void prunesDisabledFeatureArtifactsFromResolvedClasspath() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("classpath"));
        Path resources = Files.createDirectories(baseDir.resolve("src/main/resources"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        Files.writeString(resources.resolve("application.yml"), """
                coco:
                  features:
                    disabled:
                      - tenant
                      - data-permission
                """, StandardCharsets.UTF_8);

        MavenProject project = project(baseDir, output);
        Set<Artifact> artifacts = new LinkedHashSet<>(Set.of(
                artifact("coco-feature-web"),
                artifact("coco-feature-tenant"),
                artifact("coco-feature-data-permission")));
        project.setArtifacts(artifacts);
        project.setDependencyArtifacts(new LinkedHashSet<>(artifacts));
        CocoFeaturesMojo mojo = new CocoFeaturesMojo();
        set(mojo, "project", project);
        set(mojo, "outputDirectory", output.toFile());
        set(mojo, "classesDirectory", output.toFile());
        set(mojo, "featureGroupId", "io.github.patton174");
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");

        mojo.execute();

        assertThat(project.getArtifacts())
                .extracting(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId())
                .contains("io.github.patton174:coco-feature-web")
                .doesNotContain(
                        "io.github.patton174:coco-feature-tenant",
                        "io.github.patton174:coco-feature-data-permission");
        assertThat(project.getDependencyArtifacts())
                .extracting(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId())
                .contains("io.github.patton174:coco-feature-web")
                .doesNotContain(
                        "io.github.patton174:coco-feature-tenant",
                        "io.github.patton174:coco-feature-data-permission");
    }

    private MavenProject project(Path baseDir, Path output) throws Exception {
        Model model = new Model();
        model.setGroupId("com.example");
        model.setArtifactId("demo");
        model.setVersion("1.0.0");
        Build build = new Build();
        build.setOutputDirectory(output.toString());
        model.setBuild(build);
        MavenProject project = new MavenProject(model);
        project.setFile(baseDir.resolve("pom.xml").toFile());
        Files.writeString(project.getFile().toPath(), "<project />", StandardCharsets.UTF_8);
        return project;
    }

    private Artifact artifact(String artifactId) {
        return new DefaultArtifact("io.github.patton174", artifactId, "1.0.0-SNAPSHOT",
                Artifact.SCOPE_RUNTIME, "jar", null, new DefaultArtifactHandler("jar"));
    }

    private void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
