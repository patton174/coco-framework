package io.github.coco.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.model.CocoFeaturePlan;
import io.github.coco.feature.model.CocoFeatureManifest;
import io.github.coco.feature.model.CocoFeatureManifestLoader;
import io.github.coco.feature.model.StandardCocoFeatures;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.RepositorySystemSession;
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
        CocoFeaturesMojo mojo = newMojo(project);
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
                .contains("io.github.patton174:coco-web")
                .doesNotContain("io.github.patton174:coco-tenant");
        assertThat(project.getArtifacts())
                .extracting(Artifact::getArtifactId)
                .contains("coco-web", "coco-mybatis-plus", "coco-audit", "coco-security",
                        "coco-openapi", "coco-feature-codegen");
    }

    @Test
    void logsOnlyDependencyPropagatedFeaturesWhenExplicitDisablesAlsoMatchDependencies() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("dependency-diagnostics"));
        Path resources = Files.createDirectories(baseDir.resolve("src/main/resources"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        Files.writeString(resources.resolve("application.yml"), """
                coco:
                  features:
                    disabled:
                      - tenant
                      - mybatis-plus
                """, StandardCharsets.UTF_8);

        CocoFeaturesMojo mojo = newMojo(project(baseDir, output));
        List<String> infoMessages = new ArrayList<>();
        mojo.setLog(capturingLog(infoMessages));
        set(mojo, "outputDirectory", output.toFile());
        set(mojo, "classesDirectory", output.toFile());
        set(mojo, "featureGroupId", "io.github.patton174");
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");

        mojo.execute();

        assertThat(infoMessages).anySatisfy(message -> assertThat(message)
                .contains("disabledByDependency=[codegen, data-permission]")
                .doesNotContain("disabledByDependency=[codegen, data-permission, tenant]"));
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
        CocoFeaturesMojo mojo = newMojo(project);
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
                .contains("io.github.patton174:coco-audit")
                .doesNotContain("io.github.patton174:coco-web");
    }

    @Test
    void skipsPomPackagingProjects() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("parent"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        project.setPackaging("pom");
        CocoFeaturesMojo mojo = newMojo(project);
        set(mojo, "outputDirectory", output.toFile());
        set(mojo, "classesDirectory", output.toFile());

        mojo.execute();

        assertThat(output.resolve(CocoFeatureManifestLoader.MANIFEST_LOCATION)).doesNotExist();
        assertThat(project.getModel().getDependencies()).isEmpty();
    }

    @Test
    void leavesDisabledFeatureArtifactsAvailableUntilPackagePruning() throws Exception {
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
                artifact("coco-audit"),
                artifact("coco-web"),
                artifact("coco-mybatis-plus"),
                artifact("coco-feature-mybatis-plus"),
                artifact("org.mybatis", "mybatis"),
                artifact("org.mybatis", "mybatis-extra"),
                artifact("org.mybatis", "mybatis-spring"),
                artifact("org.springframework", "spring-jdbc")));
        project.setArtifacts(artifacts);
        project.setDependencyArtifacts(new LinkedHashSet<>(artifacts));
        project.getModel().addDependency(dependency("io.github.patton174", "coco-mybatis-plus"));
        project.getModel().addDependency(dependency("io.github.patton174", "coco-feature-mybatis-plus"));
        CocoFeaturesMojo mojo = newMojo(project);
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
                .contains(
                        "io.github.patton174:coco-mybatis-plus",
                        "io.github.patton174:coco-feature-mybatis-plus");
        assertThat(project.getArtifacts())
                .extracting(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId())
                .contains(
                        "io.github.patton174:coco-audit",
                        "io.github.patton174:coco-web",
                        "com.example:mybatis",
                        "com.baomidou:mybatis-plus-core",
                        "com.baomidou:mybatis-plus-jsqlparser-common",
                        "com.baomidou:mybatis-plus-spring-boot-native-image",
                        "com.baomidou:mybatis-plus-spring-boot4-starter",
                        "org.mybatis:mybatis",
                        "org.mybatis:mybatis-extra",
                        "org.mybatis:mybatis-spring",
                        "org.springframework:spring-jdbc",
                        "io.github.patton174:coco-mybatis-plus",
                        "io.github.patton174:coco-feature-mybatis-plus");
        assertThat(project.getDependencyArtifacts())
                .extracting(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId())
                .contains(
                        "io.github.patton174:coco-audit",
                        "io.github.patton174:coco-web",
                        "com.example:mybatis",
                        "com.baomidou:mybatis-plus-core",
                        "com.baomidou:mybatis-plus-jsqlparser-common",
                        "com.baomidou:mybatis-plus-spring-boot-native-image",
                        "com.baomidou:mybatis-plus-spring-boot4-starter",
                        "org.mybatis:mybatis",
                        "org.mybatis:mybatis-extra",
                        "org.mybatis:mybatis-spring",
                        "org.springframework:spring-jdbc",
                        "io.github.patton174:coco-mybatis-plus",
                        "io.github.patton174:coco-feature-mybatis-plus");
    }

    @Test
    void keepsBusinessDirectThirdPartyDependenciesWhenFeatureIsDisabled() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("direct-third-party-dependencies"));
        Path resources = Files.createDirectories(baseDir.resolve("src/main/resources"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        Files.writeString(resources.resolve("application.yml"), """
                coco:
                  features:
                    disabled:
                      - mybatis-plus
                      - codegen
                """, StandardCharsets.UTF_8);

        MavenProject project = project(baseDir, output);
        Dependency mybatis = dependency("org.mybatis", "mybatis");
        Dependency freemarker = dependency("org.freemarker", "freemarker");
        project.getModel().addDependency(mybatis);
        project.getModel().addDependency(freemarker);
        Set<Artifact> artifacts = new LinkedHashSet<>(Set.of(
                artifact("org.mybatis", "mybatis", "3.5.19"),
                artifact("org.freemarker", "freemarker", "2.3.34"),
                artifact("io.github.patton174", "coco-mybatis-plus", "2.0.2"),
                artifact("io.github.patton174", "coco-feature-codegen", "2.0.2")));
        project.setArtifacts(artifacts);
        project.setDependencyArtifacts(new LinkedHashSet<>(artifacts));

        CocoFeaturesMojo mojo = newMojo(project);
        set(mojo, "outputDirectory", output.toFile());
        set(mojo, "classesDirectory", output.toFile());
        set(mojo, "featureGroupId", "io.github.patton174");
        set(mojo, "featureVersion", "2.0.2");

        mojo.execute();

        assertThat(project.getModel().getDependencies())
                .extracting(dependency -> dependency.getGroupId() + ":" + dependency.getArtifactId())
                .contains("org.mybatis:mybatis", "org.freemarker:freemarker");
        assertThat(project.getArtifacts())
                .extracting(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId())
                .contains("org.mybatis:mybatis", "org.freemarker:freemarker",
                        "io.github.patton174:coco-mybatis-plus",
                        "io.github.patton174:coco-feature-codegen");
        assertThat(project.getDependencyArtifacts())
                .extracting(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId())
                .contains("org.mybatis:mybatis", "org.freemarker:freemarker",
                        "io.github.patton174:coco-mybatis-plus",
                        "io.github.patton174:coco-feature-codegen");
    }

    @Test
    void failsWhenMavenParameterContainsUnknownFeature() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("invalid-parameter"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        CocoFeaturesMojo mojo = newMojo(project);
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
    void failsClosedWhenProjectDependencyResolverIsUnavailable() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("resolver-unavailable"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        CocoFeaturesMojo mojo = new CocoFeaturesMojo();
        set(mojo, "project", project);
        set(mojo, "featureGroupId", "io.github.patton174");
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");

        assertThatThrownBy(() -> mojo.applyFeatureDependencies(planWithOnly(CocoFeature.WEB)))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Maven project dependency resolver is required");
        assertThat(project.getModel().getDependencies()).isEmpty();
        assertThat(project.getArtifacts()).isEmpty();
    }

    @Test
    void failsClosedWhenRuntimeArtifactResolutionFails() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("resolver-fails"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        Dependency businessDependency = dependency("com.example", "business-root");
        project.getModel().addDependency(businessDependency);
        List<Dependency> originalDependencies = project.getModel().getDependencies();
        Set<Artifact> existingArtifacts = new LinkedHashSet<>(List.of(
                artifact("com.example", "business-root"),
                artifact("com.example", "business-transitive")));
        Set<Artifact> existingDependencyArtifacts = new LinkedHashSet<>(List.of(
                artifact("com.example", "business-root")));
        project.setArtifacts(existingArtifacts);
        project.setDependencyArtifacts(existingDependencyArtifacts);
        CocoFeaturesMojo mojo = new CocoFeaturesMojo();
        set(mojo, "project", project);
        set(mojo, "featureGroupId", "io.github.patton174");
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");
        set(mojo, "projectDependenciesResolver", failingProjectDependenciesResolver());
        set(mojo, "repositorySystemSession", repositorySystemSession());
        mojo.setLog(noOpLog());

        assertThatThrownBy(() -> mojo.applyFeatureDependencies(planWith(CocoFeature.WEB, CocoFeature.AUDIT)))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Failed to resolve the refreshed Coco feature dependency closure");
        assertThat(project.getModel().getDependencies()).isSameAs(originalDependencies)
                .containsExactly(businessDependency);
        assertThat(originalDependencies).containsExactly(businessDependency);
        assertThat(project.getArtifacts()).isSameAs(existingArtifacts)
                .containsExactlyElementsOf(existingArtifacts);
        assertThat(project.getDependencyArtifacts()).isSameAs(existingDependencyArtifacts)
                .containsExactlyElementsOf(existingDependencyArtifacts);
    }

    @Test
    void collectionAndUnresolvedFailuresDoNotPublishAnyProjectMutation() throws Exception {
        for (boolean collectionFailure : List.of(true, false)) {
            Path baseDir = Files.createDirectories(this.tempDir.resolve(
                    collectionFailure ? "collection-error" : "unresolved-dependency"));
            Path output = Files.createDirectories(baseDir.resolve("target/classes"));
            MavenProject project = project(baseDir, output);
            Dependency businessDependency = dependency("com.example", "business-root");
            project.getModel().addDependency(businessDependency);
            List<Dependency> originalDependencies = project.getModel().getDependencies();
            Set<Artifact> existingArtifacts = new LinkedHashSet<>(List.of(
                    artifact("com.example", "business-root"),
                    artifact("com.example", "business-transitive")));
            Set<Artifact> existingDependencyArtifacts = new LinkedHashSet<>(List.of(
                    artifact("com.example", "business-root")));
            project.setArtifacts(existingArtifacts);
            project.setDependencyArtifacts(existingDependencyArtifacts);
            org.eclipse.aether.graph.Dependency unresolved = resolvedDependency(
                    "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT", Artifact.SCOPE_COMPILE);
            DependencyResolutionResult result = dependencyResolutionResult(
                    List.of(),
                    collectionFailure ? List.of() : List.of(unresolved),
                    collectionFailure ? List.of(new IllegalStateException("collection failed")) : List.of(),
                    collectionFailure ? List.of() : List.of(new IllegalStateException("resolution failed")));
            CocoFeaturesMojo mojo = newMojo(project);
            set(mojo, "featureVersion", "1.0.0-SNAPSHOT");
            set(mojo, "projectDependenciesResolver", (ProjectDependenciesResolver) request -> result);

            assertThatThrownBy(() -> mojo.applyFeatureDependencies(planWith(CocoFeature.WEB, CocoFeature.AUDIT)))
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessageContaining(collectionFailure ? "Failed to collect" : "Failed to resolve");
            assertThat(project.getModel().getDependencies()).isSameAs(originalDependencies)
                    .containsExactly(businessDependency);
            assertThat(originalDependencies).containsExactly(businessDependency);
            assertThat(project.getArtifacts()).isSameAs(existingArtifacts)
                    .containsExactlyElementsOf(existingArtifacts);
            assertThat(project.getDependencyArtifacts()).isSameAs(existingDependencyArtifacts)
                    .containsExactlyElementsOf(existingDependencyArtifacts);
        }
    }

    @Test
    void missingVersionIsStagedWithoutMutatingTheDeclaredDependencyWhenResolutionFails() throws Exception {
        for (boolean collectionFailure : List.of(true, false)) {
            Path baseDir = Files.createDirectories(this.tempDir.resolve(
                    collectionFailure ? "null-version-collection-error" : "null-version-unresolved"));
            Path output = Files.createDirectories(baseDir.resolve("target/classes"));
            MavenProject project = project(baseDir, output);
            Dependency declaredWeb = dependency("io.github.patton174", "coco-web");
            declaredWeb.setVersion(null);
            project.getModel().addDependency(declaredWeb);
            List<Dependency> originalDependencies = project.getModel().getDependencies();
            Set<Artifact> existingArtifacts = new LinkedHashSet<>(List.of(
                    artifact("com.example", "business-root"),
                    artifact("com.example", "business-transitive")));
            Set<Artifact> existingDependencyArtifacts = new LinkedHashSet<>(List.of(
                    artifact("com.example", "business-root")));
            project.setArtifacts(existingArtifacts);
            project.setDependencyArtifacts(existingDependencyArtifacts);
            org.eclipse.aether.graph.Dependency unresolved = resolvedDependency(
                    "io.github.patton174", "coco-audit", "1.0.0-SNAPSHOT", Artifact.SCOPE_COMPILE);
            DependencyResolutionResult result = dependencyResolutionResult(
                    List.of(),
                    collectionFailure ? List.of() : List.of(unresolved),
                    collectionFailure ? List.of(new IllegalStateException("collection failed")) : List.of(),
                    collectionFailure ? List.of() : List.of(new IllegalStateException("resolution failed")));
            CocoFeaturesMojo mojo = newMojo(project);
            set(mojo, "featureVersion", "1.0.0-SNAPSHOT");
            set(mojo, "projectDependenciesResolver", (ProjectDependenciesResolver) request -> {
                assertThat(request.getMavenProject().getDependencies())
                        .filteredOn(dependency -> "coco-web".equals(dependency.getArtifactId()))
                        .extracting(Dependency::getVersion)
                        .containsExactly("1.0.0-SNAPSHOT");
                return result;
            });

            assertThatThrownBy(() -> mojo.applyFeatureDependencies(planWith(CocoFeature.WEB, CocoFeature.AUDIT)))
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessageContaining(collectionFailure ? "Failed to collect" : "Failed to resolve");
            assertThat(project.getModel().getDependencies()).isSameAs(originalDependencies)
                    .containsExactly(declaredWeb);
            assertThat(originalDependencies.get(0)).isSameAs(declaredWeb);
            assertThat(declaredWeb.getVersion()).isNull();
            assertThat(project.getArtifacts()).isSameAs(existingArtifacts)
                    .containsExactlyElementsOf(existingArtifacts);
            assertThat(project.getDependencyArtifacts()).isSameAs(existingDependencyArtifacts)
                    .containsExactlyElementsOf(existingDependencyArtifacts);
        }
    }

    @Test
    void treatsLegacyAliasAsAnExistingFeatureDependency() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("legacy-alias"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        Dependency legacyWeb = dependency("io.github.patton174", "coco-feature-web");
        legacyWeb.setVersion("2.0.2");
        project.getModel().addDependency(legacyWeb);
        CocoFeaturesMojo mojo = newMojo(project);
        set(mojo, "projectDependenciesResolver", (ProjectDependenciesResolver) request -> {
            throw new AssertionError("Declared compatibility dependencies must not trigger classpath refresh.");
        });
        set(mojo, "featureGroupId", "io.github.patton174");
        set(mojo, "featureVersion", "2.0.2");

        mojo.applyFeatureDependencies(planWithOnly(CocoFeature.WEB));

        assertThat(project.getModel().getDependencies())
                .extracting(Dependency::getArtifactId)
                .containsExactly("coco-feature-web");
    }

    @Test
    void rejectsTestProvidedAndOptionalFeatureDeclarations() throws Exception {
        java.util.List<Dependency> invalidDependencies = new java.util.ArrayList<>();
        Dependency testDependency = dependency("io.github.patton174", "coco-web");
        testDependency.setScope(Artifact.SCOPE_TEST);
        invalidDependencies.add(testDependency);
        Dependency providedDependency = dependency("io.github.patton174", "coco-web");
        providedDependency.setScope(Artifact.SCOPE_PROVIDED);
        invalidDependencies.add(providedDependency);
        Dependency optionalDependency = dependency("io.github.patton174", "coco-web");
        optionalDependency.setOptional(true);
        invalidDependencies.add(optionalDependency);

        for (int index = 0; index < invalidDependencies.size(); index++) {
            Path baseDir = Files.createDirectories(this.tempDir.resolve("invalid-runtime-dependency-" + index));
            Path output = Files.createDirectories(baseDir.resolve("target/classes"));
            MavenProject project = project(baseDir, output);
            project.getModel().addDependency(invalidDependencies.get(index));
            CocoFeaturesMojo mojo = newMojo(project);
            set(mojo, "featureVersion", "1.0.0-SNAPSHOT");

            assertThatThrownBy(() -> mojo.applyFeatureDependencies(planWithOnly(CocoFeature.WEB)))
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessageContaining("non-runtime dependencies")
                    .hasMessageContaining("non-optional compile or runtime scope");
            assertThat(project.getArtifacts()).isEmpty();
        }
    }

    @Test
    void atomicallyPublishesResolvedClosureAndDirectFeatureRootsToTheirMavenProjectViews() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("transitive-closure"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        Artifact existingArtifact = artifact("com.example", "existing-artifact");
        Artifact existingDependencyArtifact = artifact("com.example", "existing-dependency-artifact");
        project.setArtifacts(new LinkedHashSet<>(Set.of(existingArtifact)));
        project.setDependencyArtifacts(new LinkedHashSet<>(Set.of(existingDependencyArtifact)));
        CocoFeaturesMojo mojo = newMojo(project);
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");
        set(mojo, "projectDependenciesResolver", projectDependenciesResolverReturning(request -> {
            assertThat(request.getMavenProject()).isNotSameAs(project);
            assertThat(request.getMavenProject().getDependencies())
                    .extracting(Dependency::getArtifactId)
                    .contains("coco-web");
            return java.util.List.of(
                    resolvedDependency("io.github.patton174", "coco-web", "1.0.0-SNAPSHOT",
                            Artifact.SCOPE_COMPILE),
                    resolvedDependency("com.example", "feature-runtime", "3.2.1", Artifact.SCOPE_RUNTIME));
        }));

        mojo.applyFeatureDependencies(planWithOnly(CocoFeature.WEB));

        assertThat(project.getModel().getDependencies())
                .singleElement()
                .satisfies(dependency -> {
                    assertThat(dependency.getArtifactId()).isEqualTo("coco-web");
                    assertThat(dependency.getScope()).isEqualTo(Artifact.SCOPE_COMPILE);
                });
        assertThat(project.getArtifacts())
                .extracting(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId())
                .containsExactly(
                        "com.example:existing-artifact",
                        "io.github.patton174:coco-web",
                        "com.example:feature-runtime");
        assertThat(project.getDependencyArtifacts())
                .extracting(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId())
                .containsExactly(
                        "com.example:existing-dependency-artifact",
                        "io.github.patton174:coco-web");
        assertThat(project.getArtifacts())
                .filteredOn(artifact -> "coco-web".equals(artifact.getArtifactId()))
                .allSatisfy(artifact -> assertThat(artifact.getScope()).isEqualTo(Artifact.SCOPE_COMPILE));
        assertThat(project.getArtifacts())
                .filteredOn(artifact -> "feature-runtime".equals(artifact.getArtifactId()))
                .allSatisfy(artifact -> assertThat(artifact.getScope()).isEqualTo(Artifact.SCOPE_RUNTIME));
        assertThat(project.getDependencyArtifacts())
                .filteredOn(artifact -> "coco-web".equals(artifact.getArtifactId()))
                .allSatisfy(artifact -> assertThat(artifact.getScope()).isEqualTo(Artifact.SCOPE_COMPILE));
        assertThat(project.getDependencyArtifacts())
                .extracting(Artifact::getArtifactId)
                .doesNotContain("feature-runtime");
    }

    @Test
    void replacesMediatedConflictsAndPreservesDeterministicMavenProjectViewOrder() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("mediated-conflict"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        Artifact unrelatedBefore = artifact("com.example", "unrelated-before", "1.0.0");
        Artifact oldShared = artifact("com.example", "shared-library", "1.0.0");
        Artifact unrelatedAfter = artifact("com.example", "unrelated-after", "1.0.0");
        project.setArtifacts(new LinkedHashSet<>(List.of(unrelatedBefore, oldShared, unrelatedAfter)));
        Artifact unrelatedDirect = artifact("com.example", "unrelated-direct", "1.0.0");
        Artifact staleDirectFeature = artifact("io.github.patton174", "coco-web", "0.9.0");
        project.setDependencyArtifacts(new LinkedHashSet<>(List.of(unrelatedDirect, staleDirectFeature)));
        CocoFeaturesMojo mojo = newMojo(project);
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");
        set(mojo, "projectDependenciesResolver", projectDependenciesResolverReturning(request -> List.of(
                resolvedDependency("io.github.patton174", "coco-web", "1.0.0-SNAPSHOT",
                        Artifact.SCOPE_COMPILE),
                resolvedDependency("com.example", "shared-library", "2.0.0", Artifact.SCOPE_RUNTIME),
                resolvedDependency("com.example", "resolved-tail", "1.0.0", Artifact.SCOPE_RUNTIME))));

        mojo.applyFeatureDependencies(planWithOnly(CocoFeature.WEB));

        assertThat(project.getArtifacts())
                .extracting(artifact -> artifact.getArtifactId() + ":" + artifact.getVersion())
                .containsExactly(
                        "unrelated-before:1.0.0",
                        "unrelated-after:1.0.0",
                        "coco-web:1.0.0-SNAPSHOT",
                        "shared-library:2.0.0",
                        "resolved-tail:1.0.0");
        assertThat(project.getDependencyArtifacts())
                .extracting(artifact -> artifact.getArtifactId() + ":" + artifact.getVersion())
                .containsExactly("unrelated-direct:1.0.0", "coco-web:1.0.0-SNAPSHOT");
    }

    @Test
    void preservesDifferentClassifierAndTypeArtifactsWhenReplacingMavenConflictIds() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("classifier-type-conflicts"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        Artifact testsClassifier = artifact(
                "com.example", "shared-library", "1.0.0", "jar", "tests");
        Artifact zipType = artifact(
                "com.example", "shared-library", "1.0.0", "zip", null);
        Artifact oldPlainJar = artifact("com.example", "shared-library", "1.0.0");
        project.setArtifacts(new LinkedHashSet<>(List.of(testsClassifier, zipType, oldPlainJar)));
        CocoFeaturesMojo mojo = newMojo(project);
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");
        set(mojo, "projectDependenciesResolver", projectDependenciesResolverReturning(request -> List.of(
                resolvedDependency("io.github.patton174", "coco-web", "1.0.0-SNAPSHOT",
                        Artifact.SCOPE_COMPILE),
                resolvedDependency("com.example", "shared-library", "2.0.0", Artifact.SCOPE_RUNTIME))));

        mojo.applyFeatureDependencies(planWithOnly(CocoFeature.WEB));

        assertThat(project.getArtifacts())
                .extracting(artifact -> artifact.getArtifactId() + ":" + artifact.getType() + ":"
                        + artifact.getClassifier() + ":" + artifact.getVersion())
                .containsExactly(
                        "shared-library:jar:tests:1.0.0",
                        "shared-library:zip:null:1.0.0",
                        "coco-web:jar:null:1.0.0-SNAPSHOT",
                        "shared-library:jar:null:2.0.0");
    }

    @Test
    void rollsBackModelAndBothProjectViewsWhenSecondSetterRejectsPublication() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("setter-rollback"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject baseProject = project(baseDir, output);
        FailingDependencyArtifactsProject project = new FailingDependencyArtifactsProject(baseProject.getModel());
        project.setFile(baseProject.getFile());
        Dependency businessDependency = dependency("com.example", "business-root");
        project.getModel().addDependency(businessDependency);
        List<Dependency> originalDependencies = project.getModel().getDependencies();
        Set<Artifact> existingArtifacts = new LinkedHashSet<>(List.of(
                artifact("com.example", "business-root"),
                artifact("com.example", "business-transitive")));
        Set<Artifact> existingDependencyArtifacts = new LinkedHashSet<>(List.of(
                artifact("com.example", "business-root")));
        project.setArtifacts(existingArtifacts);
        project.setDependencyArtifacts(existingDependencyArtifacts);
        project.failNextDependencyArtifactsPublication();
        CocoFeaturesMojo mojo = newMojo(project);
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");
        set(mojo, "projectDependenciesResolver", projectDependenciesResolverReturning(request -> List.of(
                resolvedDependency("io.github.patton174", "coco-web", "1.0.0-SNAPSHOT",
                        Artifact.SCOPE_COMPILE),
                resolvedDependency("com.example", "feature-runtime", "3.2.1", Artifact.SCOPE_RUNTIME))));

        assertThatThrownBy(() -> mojo.applyFeatureDependencies(planWithOnly(CocoFeature.WEB)))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Failed to publish resolved Coco feature dependency views")
                .hasRootCauseMessage("dependencyArtifacts publication failed");
        assertThat(project.getModel().getDependencies()).isSameAs(originalDependencies)
                .containsExactly(businessDependency);
        assertThat(originalDependencies).containsExactly(businessDependency);
        assertThat(project.getArtifacts()).isSameAs(existingArtifacts)
                .containsExactlyElementsOf(existingArtifacts);
        assertThat(project.getDependencyArtifacts()).isSameAs(existingDependencyArtifacts)
                .containsExactlyElementsOf(existingDependencyArtifacts);
    }

    @Test
    void appliesCompileRuntimeOptionalScopeFilterToTheStagedProjectResolution() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("resolution-filter"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        CocoFeaturesMojo mojo = newMojo(project);
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");
        set(mojo, "projectDependenciesResolver", projectDependenciesResolverReturning(request -> {
            assertThat(request.getResolutionFilter().accept(
                    dependencyNode("compile", Artifact.SCOPE_COMPILE, false), List.of())).isTrue();
            assertThat(request.getResolutionFilter().accept(
                    dependencyNode("runtime", Artifact.SCOPE_RUNTIME, false), List.of())).isTrue();
            assertThat(request.getResolutionFilter().accept(
                    dependencyNode("provided", Artifact.SCOPE_PROVIDED, false), List.of())).isFalse();
            assertThat(request.getResolutionFilter().accept(
                    dependencyNode("test", Artifact.SCOPE_TEST, false), List.of())).isFalse();
            assertThat(request.getResolutionFilter().accept(
                    dependencyNode("optional", Artifact.SCOPE_COMPILE, true), List.of())).isFalse();
            return List.of(resolvedDependency("io.github.patton174", "coco-web", "1.0.0-SNAPSHOT",
                    Artifact.SCOPE_COMPILE));
        }));

        mojo.applyFeatureDependencies(planWithOnly(CocoFeature.WEB));

        assertThat(project.getArtifacts()).extracting(Artifact::getArtifactId).containsExactly("coco-web");
    }

    @Test
    void preservesNullDependencyArtifactsWhilePublishingTheResolvedClosure() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("null-direct-artifacts"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        project.setDependencyArtifacts(null);
        CocoFeaturesMojo mojo = newMojo(project);
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");
        set(mojo, "projectDependenciesResolver", projectDependenciesResolverReturning(request -> java.util.List.of(
                resolvedDependency("io.github.patton174", "coco-web", "1.0.0-SNAPSHOT",
                        Artifact.SCOPE_COMPILE),
                resolvedDependency("com.example", "feature-runtime", "3.2.1", Artifact.SCOPE_RUNTIME))));

        mojo.applyFeatureDependencies(planWithOnly(CocoFeature.WEB));

        assertThat(project.getDependencyArtifacts()).isNull();
        assertThat(project.getArtifacts())
                .extracting(Artifact::getArtifactId)
                .containsExactly("coco-web", "feature-runtime");
    }

    @Test
    void resolvesAllNewFeatureRootsTogetherBeforePublishingAnyProjectMutation() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("aggregate-resolution"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        CocoFeaturesMojo mojo = newMojo(project);
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");
        AtomicInteger resolutions = new AtomicInteger();
        set(mojo, "projectDependenciesResolver", projectDependenciesResolverReturning(request -> {
            resolutions.incrementAndGet();
            assertThat(project.getModel().getDependencies()).isEmpty();
            assertThat(request.getMavenProject().getDependencyArtifacts()).isNull();
            assertThat(request.getMavenProject().getDependencies())
                    .extracting(Dependency::getArtifactId)
                    .containsExactly("coco-web", "coco-audit");
            return request.getMavenProject().getDependencies().stream()
                    .map(dependency -> resolvedDependency(
                            dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(),
                            dependency.getScope()))
                    .toList();
        }));

        mojo.applyFeatureDependencies(planWith(CocoFeature.WEB, CocoFeature.AUDIT));

        assertThat(resolutions).hasValue(1);
        assertThat(project.getModel().getDependencies())
                .extracting(Dependency::getArtifactId)
                .containsExactly("coco-web", "coco-audit");
    }

    @Test
    void doesNotRedeclareFeaturesAlreadyResolvedThroughTheStarter() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("starter-classpath"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        project.getModel().addDependency(dependency("io.github.patton174", "coco-spring-boot-starter"));
        Set<Artifact> starterClasspath = new LinkedHashSet<>(List.of(
                artifact("io.github.patton174", "coco-api"),
                artifact("io.github.patton174", "coco-web"),
                artifact("org.springframework", "spring-context"),
                artifact("com.fasterxml.jackson.core", "jackson-databind")));
        project.setArtifacts(starterClasspath);
        project.setDependencyArtifacts(new LinkedHashSet<>(starterClasspath));
        CocoFeaturesMojo mojo = newMojo(project);
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");

        mojo.applyFeatureDependencies(planWithOnly(CocoFeature.WEB));

        assertThat(project.getModel().getDependencies())
                .extracting(Dependency::getArtifactId)
                .containsExactly("coco-spring-boot-starter");
        assertThat(project.getArtifacts()).containsExactlyElementsOf(starterClasspath);
        assertThat(project.getDependencyArtifacts()).containsExactlyElementsOf(starterClasspath);
    }

    @Test
    void failsClosedWhenResolvedClosureOmitsDirectFeatureArtifact() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("missing-direct-closure"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        CocoFeaturesMojo mojo = newMojo(project);
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");
        set(mojo, "projectDependenciesResolver", projectDependenciesResolverReturning(request -> java.util.List.of(
                resolvedDependency("com.example", "feature-runtime", "3.2.1", Artifact.SCOPE_RUNTIME))));

        assertThatThrownBy(() -> mojo.applyFeatureDependencies(planWithOnly(CocoFeature.WEB)))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("missing direct feature dependency");
        assertThat(project.getModel().getDependencies()).isEmpty();
        assertThat(project.getArtifacts()).isEmpty();
    }

    @Test
    void rejectsFeatureArtifactsFromAnotherFrameworkVersion() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("misaligned-version"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        project.setArtifacts(Set.of(
                artifact("io.github.patton174", "coco-feature-web", "2.0.1"),
                artifact("io.github.patton174", "coco-web", "2.0.2")));
        CocoFeaturesMojo mojo = newMojo(project);
        set(mojo, "featureGroupId", "io.github.patton174");
        set(mojo, "featureVersion", "2.0.2");

        assertThatThrownBy(mojo::validateFeatureArtifactVersions)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessage("Coco feature artifact versions must align with '2.0.2': "
                        + "io.github.patton174:coco-feature-web:2.0.1.");
    }

    @Test
    void derivesFeatureVersionFromResolvedCocoArtifactsInsteadOfBusinessProjectVersion() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("different-business-version"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        project.setVersion("99.7.3");
        project.setArtifacts(Set.of(artifact("io.github.patton174", "coco-api", "2.0.2")));

        CocoFeaturesMojo mojo = newMojo(project);
        set(mojo, "outputDirectory", output.toFile());
        set(mojo, "classesDirectory", output.toFile());
        set(mojo, "featureGroupId", "io.github.patton174");

        mojo.execute();

        assertThat(project.getModel().getDependencies())
                .filteredOn(dependency -> "io.github.patton174".equals(dependency.getGroupId()))
                .allSatisfy(dependency -> assertThat(dependency.getVersion()).isEqualTo("2.0.2"));
    }

    @Test
    void derivesFeatureVersionFromPluginDescriptorInsteadOfBusinessProjectVersion() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("plugin-version-source"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        project.setVersion("99.7.3");
        PluginDescriptor pluginDescriptor = new PluginDescriptor();
        pluginDescriptor.setVersion("2.0.2");

        CocoFeaturesMojo mojo = newMojo(project);
        set(mojo, "featureGroupId", "io.github.patton174");
        set(mojo, "pluginDescriptor", pluginDescriptor);

        mojo.applyFeatureDependencies(planWithOnly(CocoFeature.WEB));

        assertThat(project.getModel().getDependencies())
                .singleElement()
                .satisfies(dependency -> assertThat(dependency.getVersion()).isEqualTo("2.0.2"));
    }

    @Test
    void explicitFeatureVersionRemainsAuthoritativeOverPluginVersion() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("explicit-feature-version"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        PluginDescriptor pluginDescriptor = new PluginDescriptor();
        pluginDescriptor.setVersion("9.9.9");

        CocoFeaturesMojo mojo = newMojo(project);
        set(mojo, "featureGroupId", "io.github.patton174");
        set(mojo, "featureVersion", "2.0.2");
        set(mojo, "pluginDescriptor", pluginDescriptor);

        mojo.applyFeatureDependencies(planWithOnly(CocoFeature.WEB));

        assertThat(project.getModel().getDependencies())
                .singleElement()
                .satisfies(dependency -> assertThat(dependency.getVersion()).isEqualTo("2.0.2"));
    }

    @Test
    void rejectsMixedResolvedCocoArtifactVersionsWithoutUsingBusinessVersion() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("mixed-coco-versions"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        project.setVersion("99.7.3");
        project.setArtifacts(Set.of(
                artifact("io.github.patton174", "coco-api", "2.0.1"),
                artifact("io.github.patton174", "coco-web", "2.0.2")));

        CocoFeaturesMojo mojo = newMojo(project);
        set(mojo, "outputDirectory", output.toFile());
        set(mojo, "classesDirectory", output.toFile());
        set(mojo, "featureGroupId", "io.github.patton174");

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Coco artifacts must use one version")
                .hasMessageContaining("coco-api:2.0.1")
                .hasMessageContaining("coco-web:2.0.2");
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
        return artifact(groupId, artifactId, "1.0.0-SNAPSHOT");
    }

    private Artifact artifact(String groupId, String artifactId, String version) {
        return artifact(groupId, artifactId, version, "jar", null);
    }

    private Artifact artifact(String groupId, String artifactId, String version,
            String type, String classifier) {
        return new DefaultArtifact(groupId, artifactId, version,
                Artifact.SCOPE_RUNTIME, type, classifier, new DefaultArtifactHandler(type));
    }

    private Dependency dependency(String groupId, String artifactId) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setVersion("1.0.0-SNAPSHOT");
        return dependency;
    }

    private CocoFeaturePlan planWithOnly(CocoFeature feature) {
        return planWith(feature);
    }

    private CocoFeaturePlan planWith(CocoFeature... features) {
        EnumSet<CocoFeature> disabled = EnumSet.allOf(CocoFeature.class);
        disabled.removeAll(List.of(features));
        return new CocoFeaturePlan(Set.of(features), disabled, StandardCocoFeatures.all());
    }

    private CocoFeaturesMojo newMojo(MavenProject project) throws Exception {
        CocoFeaturesMojo mojo = new CocoFeaturesMojo();
        set(mojo, "project", project);
        set(mojo, "featureGroupId", "io.github.patton174");
        set(mojo, "projectDependenciesResolver", projectDependenciesResolverReturning(request ->
                request.getMavenProject().getDependencies().stream()
                        .map(dependency -> resolvedDependency(
                                dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(),
                                dependency.getScope() == null ? Artifact.SCOPE_COMPILE : dependency.getScope()))
                        .toList()));
        set(mojo, "repositorySystemSession", repositorySystemSession());
        return mojo;
    }

    private ProjectDependenciesResolver projectDependenciesResolverReturning(
            java.util.function.Function<DependencyResolutionRequest,
                    java.util.List<org.eclipse.aether.graph.Dependency>> resolution) {
        return request -> dependencyResolutionResult(resolution.apply(request));
    }

    private DependencyResolutionResult dependencyResolutionResult(
            List<org.eclipse.aether.graph.Dependency> resolvedDependencies) {
        return dependencyResolutionResult(resolvedDependencies, List.of(), List.of(), List.of());
    }

    private DependencyResolutionResult dependencyResolutionResult(
            List<org.eclipse.aether.graph.Dependency> resolvedDependencies,
            List<org.eclipse.aether.graph.Dependency> unresolvedDependencies,
            List<Exception> collectionErrors, List<Exception> resolutionErrors) {
        return (DependencyResolutionResult) Proxy.newProxyInstance(
                DependencyResolutionResult.class.getClassLoader(),
                new Class<?>[] { DependencyResolutionResult.class },
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getDependencies", "getResolvedDependencies" -> resolvedDependencies;
                    case "getUnresolvedDependencies" -> unresolvedDependencies;
                    case "getCollectionErrors" -> collectionErrors;
                    case "getResolutionErrors" -> resolutionErrors;
                    case "getDependencyGraph" -> null;
                    default -> proxyObjectMethod(proxy, method.getName(), arguments);
                });
    }

    private org.eclipse.aether.graph.Dependency resolvedDependency(String groupId, String artifactId,
            String version, String scope) {
        org.eclipse.aether.artifact.Artifact artifact = new org.eclipse.aether.artifact.DefaultArtifact(
                groupId, artifactId, "jar", version)
                .setFile(resolvedArtifactFile(artifactId, version).toFile());
        return new org.eclipse.aether.graph.Dependency(artifact, scope);
    }

    private org.eclipse.aether.graph.DependencyNode dependencyNode(String artifactId, String scope,
            boolean optional) {
        org.eclipse.aether.artifact.Artifact artifact = new org.eclipse.aether.artifact.DefaultArtifact(
                "com.example", artifactId, "jar", "1.0.0");
        return new org.eclipse.aether.graph.DefaultDependencyNode(
                new org.eclipse.aether.graph.Dependency(artifact, scope, optional));
    }

    private Path resolvedArtifactFile(String artifactId, String version) {
        try {
            Path directory = Files.createDirectories(this.tempDir.resolve("resolver-artifacts"));
            Path artifact = directory.resolve(artifactId + "-" + version + ".jar");
            if (!Files.exists(artifact)) {
                try (java.util.jar.JarOutputStream ignored = new java.util.jar.JarOutputStream(
                        Files.newOutputStream(artifact))) {
                    // A readable empty JAR is sufficient for resolver-boundary unit tests.
                }
            }
            return artifact;
        }
        catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    private ProjectDependenciesResolver failingProjectDependenciesResolver() {
        return request -> {
            DependencyResolutionResult result = dependencyResolutionResult(List.of());
            throw new org.apache.maven.project.DependencyResolutionException(
                    result, "artifact unavailable", new IllegalStateException("artifact unavailable"));
        };
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

    private Log capturingLog(List<String> infoMessages) {
        return (Log) Proxy.newProxyInstance(
                Log.class.getClassLoader(),
                new Class<?>[] { Log.class },
                (proxy, method, arguments) -> {
                    if ("isInfoEnabled".equals(method.getName())) {
                        return true;
                    }
                    if ("info".equals(method.getName()) && arguments != null && arguments.length > 0
                            && arguments[0] instanceof CharSequence message) {
                        infoMessages.add(message.toString());
                        return null;
                    }
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

    private static final class FailingDependencyArtifactsProject extends MavenProject {

        private boolean failNextDependencyArtifactsPublication;

        private FailingDependencyArtifactsProject(Model model) {
            super(model);
        }

        private void failNextDependencyArtifactsPublication() {
            this.failNextDependencyArtifactsPublication = true;
        }

        @Override
        public void setDependencyArtifacts(Set<Artifact> dependencyArtifacts) {
            if (this.failNextDependencyArtifactsPublication) {
                this.failNextDependencyArtifactsPublication = false;
                throw new IllegalStateException("dependencyArtifacts publication failed");
            }
            super.setDependencyArtifacts(dependencyArtifacts);
        }
    }
}
