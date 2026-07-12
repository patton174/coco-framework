package io.github.coco.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.model.CocoFeaturePlan;
import io.github.coco.feature.model.CocoFeatureManifest;
import io.github.coco.feature.model.CocoFeatureManifestLoader;
import io.github.coco.feature.model.StandardCocoFeatures;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
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
                    disabled:
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
        assertThat(project.getArtifacts()).isEmpty();
    }

    @Test
    void keepsAuditEnabledAndAddsDependencyWhenWebIsDisabled() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("without-web"));
        Path resources = Files.createDirectories(baseDir.resolve("src/main/resources"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        Files.writeString(resources.resolve("application.yml"), """
                coco:
                  features:
                    disabled:
                      - web
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
        assertThat(manifest.enabledFeatureIds()).contains("audit").doesNotContain("web", "openapi");
        assertThat(manifest.features())
                .filteredOn(entry -> "audit".equals(entry.id()))
                .singleElement()
                .satisfies(entry -> assertThat(entry.dependencies()).isEmpty());
        assertThat(project.getModel().getDependencies())
                .extracting(dependency -> dependency.getGroupId() + ":" + dependency.getArtifactId())
                .contains("io.github.patton174:coco-feature-audit")
                .doesNotContain("io.github.patton174:coco-feature-web");
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
    void prunesDisabledMybatisArtifactsAndKeepsAudit() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("classpath"));
        Path resources = Files.createDirectories(baseDir.resolve("src/main/resources"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        Files.writeString(resources.resolve("application.yml"), """
                coco:
                  features:
                    disabled:
                      - mybatis-plus
                """, StandardCharsets.UTF_8);

        MavenProject project = project(baseDir, output);
        Set<Artifact> artifacts = new LinkedHashSet<>(Set.of(
                artifact("com.baomidou", "mybatis-plus-core"),
                artifact("com.baomidou", "mybatis-plus-jsqlparser-common"),
                artifact("com.baomidou", "mybatis-plus-spring-boot-native-image"),
                artifact("com.baomidou", "mybatis-plus-spring-boot4-starter"),
                artifact("com.example", "mybatis"),
                artifact("coco-feature-audit"),
                artifact("coco-feature-web"),
                artifact("coco-feature-mybatis-plus"),
                artifact("org.mybatis", "mybatis"),
                artifact("org.mybatis", "mybatis-extra"),
                artifact("org.mybatis", "mybatis-spring"),
                artifact("org.springframework", "spring-jdbc")));
        project.setArtifacts(artifacts);
        project.setDependencyArtifacts(new LinkedHashSet<>(artifacts));
        CocoFeaturesMojo mojo = new CocoFeaturesMojo();
        set(mojo, "project", project);
        set(mojo, "outputDirectory", output.toFile());
        set(mojo, "classesDirectory", output.toFile());
        set(mojo, "featureGroupId", "io.github.patton174");
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");

        mojo.execute();

        CocoFeatureManifest manifest = CocoFeatureManifestLoader.read(
                Files.newInputStream(output.resolve(CocoFeatureManifestLoader.MANIFEST_LOCATION)));
        assertThat(manifest.enabledFeatureIds()).contains("audit").doesNotContain("mybatis-plus");
        assertThat(project.getModel().getDependencies())
                .extracting(dependency -> dependency.getGroupId() + ":" + dependency.getArtifactId())
                .contains("io.github.patton174:coco-feature-audit")
                .doesNotContain("io.github.patton174:coco-feature-mybatis-plus");
        assertThat(project.getArtifacts())
                .extracting(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId())
                .contains(
                        "io.github.patton174:coco-feature-audit",
                        "io.github.patton174:coco-feature-web",
                        "com.example:mybatis",
                        "org.mybatis:mybatis-extra",
                        "org.springframework:spring-jdbc")
                .doesNotContain(
                        "com.baomidou:mybatis-plus-core",
                        "com.baomidou:mybatis-plus-jsqlparser-common",
                        "com.baomidou:mybatis-plus-spring-boot-native-image",
                        "com.baomidou:mybatis-plus-spring-boot4-starter",
                        "io.github.patton174:coco-feature-mybatis-plus",
                        "org.mybatis:mybatis",
                        "org.mybatis:mybatis-spring");
        assertThat(project.getDependencyArtifacts())
                .extracting(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId())
                .contains(
                        "io.github.patton174:coco-feature-audit",
                        "io.github.patton174:coco-feature-web",
                        "com.example:mybatis",
                        "org.mybatis:mybatis-extra",
                        "org.springframework:spring-jdbc")
                .doesNotContain(
                        "com.baomidou:mybatis-plus-core",
                        "com.baomidou:mybatis-plus-jsqlparser-common",
                        "com.baomidou:mybatis-plus-spring-boot-native-image",
                        "com.baomidou:mybatis-plus-spring-boot4-starter",
                        "io.github.patton174:coco-feature-mybatis-plus",
                        "org.mybatis:mybatis",
                        "org.mybatis:mybatis-spring");
    }

    @Test
    void failsWhenMavenParameterContainsUnknownFeature() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("invalid-parameter"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        CocoFeaturesMojo mojo = new CocoFeaturesMojo();
        set(mojo, "project", project);
        set(mojo, "outputDirectory", output.toFile());
        set(mojo, "classesDirectory", output.toFile());
        set(mojo, "enabled", "web,wrong-feature");

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Failed to resolve Coco feature selection")
                .hasRootCauseMessage("Unknown Coco feature id 'wrong-feature' in Maven parameter "
                        + "coco.features.enabled. Valid feature ids: web, mybatis-plus, audit, security, tenant, "
                        + "data-permission, openapi, codegen.");
    }

    @Test
    void keepsModelDependencyWhenRuntimeArtifactResolverIsUnavailable() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("resolver-unavailable"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        CocoFeaturesMojo mojo = new CocoFeaturesMojo();
        set(mojo, "project", project);
        set(mojo, "featureGroupId", "io.github.patton174");
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");

        mojo.applyFeatureDependencies(planWithOnly(CocoFeature.WEB));

        assertThat(project.getModel().getDependencies())
                .singleElement()
                .satisfies(dependency -> {
                    assertThat(dependency.getGroupId()).isEqualTo("io.github.patton174");
                    assertThat(dependency.getArtifactId()).isEqualTo("coco-feature-web");
                    assertThat(dependency.getVersion()).isEqualTo("1.0.0-SNAPSHOT");
                    assertThat(dependency.getScope()).isEqualTo(Artifact.SCOPE_RUNTIME);
                });
        assertThat(project.getArtifacts()).isEmpty();
    }

    @Test
    void keepsModelDependencyWhenRuntimeArtifactResolutionFails() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("resolver-fails"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        CocoFeaturesMojo mojo = new CocoFeaturesMojo();
        set(mojo, "project", project);
        set(mojo, "featureGroupId", "io.github.patton174");
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");
        set(mojo, "repositorySystem", failingRepositorySystem());
        set(mojo, "repositorySystemSession", repositorySystemSession());
        set(mojo, "remoteRepositories", List.of());
        mojo.setLog(noOpLog());

        mojo.applyFeatureDependencies(planWithOnly(CocoFeature.WEB));

        assertThat(project.getModel().getDependencies())
                .singleElement()
                .satisfies(dependency -> {
                    assertThat(dependency.getGroupId()).isEqualTo("io.github.patton174");
                    assertThat(dependency.getArtifactId()).isEqualTo("coco-feature-web");
                    assertThat(dependency.getVersion()).isEqualTo("1.0.0-SNAPSHOT");
                    assertThat(dependency.getScope()).isEqualTo(Artifact.SCOPE_RUNTIME);
                });
        assertThat(project.getArtifacts()).isEmpty();
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
        return artifact("io.github.patton174", artifactId);
    }

    private Artifact artifact(String groupId, String artifactId) {
        return new DefaultArtifact(groupId, artifactId, "1.0.0-SNAPSHOT",
                Artifact.SCOPE_RUNTIME, "jar", null, new DefaultArtifactHandler("jar"));
    }

    private CocoFeaturePlan planWithOnly(CocoFeature feature) {
        EnumSet<CocoFeature> disabled = EnumSet.allOf(CocoFeature.class);
        disabled.remove(feature);
        return new CocoFeaturePlan(Set.of(feature), disabled, StandardCocoFeatures.all());
    }

    private RepositorySystem failingRepositorySystem() {
        return (RepositorySystem) Proxy.newProxyInstance(
                RepositorySystem.class.getClassLoader(),
                new Class<?>[] { RepositorySystem.class },
                (proxy, method, arguments) -> {
                    if ("resolveArtifact".equals(method.getName())) {
                        ArtifactRequest request = (ArtifactRequest) arguments[1];
                        ArtifactResult result = new ArtifactResult(request);
                        result.setArtifact(request.getArtifact());
                        result.addException(new IllegalStateException("artifact unavailable"));
                        throw new ArtifactResolutionException(List.of(result), "artifact unavailable");
                    }
                    return proxyObjectMethod(proxy, method.getName(), arguments);
                });
    }

    private RepositorySystemSession repositorySystemSession() {
        return (RepositorySystemSession) Proxy.newProxyInstance(
                RepositorySystemSession.class.getClassLoader(),
                new Class<?>[] { RepositorySystemSession.class },
                (proxy, method, arguments) -> proxyObjectMethod(proxy, method.getName(), arguments));
    }

    private Log noOpLog() {
        return (Log) Proxy.newProxyInstance(
                Log.class.getClassLoader(),
                new Class<?>[] { Log.class },
                (proxy, method, arguments) -> {
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == void.class) {
                        return null;
                    }
                    return proxyObjectMethod(proxy, method.getName(), arguments);
                });
    }

    private Object proxyObjectMethod(Object proxy, String methodName, Object[] arguments) {
        if ("toString".equals(methodName)) {
            return proxy.getClass().getInterfaces()[0].getSimpleName() + "Proxy";
        }
        if ("hashCode".equals(methodName)) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(methodName)) {
            return proxy == arguments[0];
        }
        throw new UnsupportedOperationException(methodName);
    }

    private void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
