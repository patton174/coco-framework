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
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
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
    void prunesOnlyDisabledCocoMybatisArtifactsAndKeepsThirdPartyArtifacts() throws Exception {
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
                .contains("io.github.patton174:coco-audit")
                .doesNotContain(
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
                        "org.springframework:spring-jdbc")
                .doesNotContain(
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
                        "org.springframework:spring-jdbc")
                .doesNotContain(
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
                .contains("org.mybatis:mybatis", "org.freemarker:freemarker")
                .doesNotContain("io.github.patton174:coco-mybatis-plus",
                        "io.github.patton174:coco-feature-codegen");
        assertThat(project.getArtifacts())
                .extracting(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId())
                .contains("org.mybatis:mybatis", "org.freemarker:freemarker")
                .doesNotContain("io.github.patton174:coco-mybatis-plus",
                        "io.github.patton174:coco-feature-codegen");
        assertThat(project.getDependencyArtifacts())
                .extracting(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId())
                .contains("org.mybatis:mybatis", "org.freemarker:freemarker")
                .doesNotContain("io.github.patton174:coco-mybatis-plus",
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
    void failsClosedWhenRuntimeArtifactResolverIsUnavailable() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("resolver-unavailable"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        CocoFeaturesMojo mojo = new CocoFeaturesMojo();
        set(mojo, "project", project);
        set(mojo, "featureGroupId", "io.github.patton174");
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");

        assertThatThrownBy(() -> mojo.applyFeatureDependencies(planWithOnly(CocoFeature.WEB)))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Maven Resolver is required");
        assertThat(project.getModel().getDependencies()).isEmpty();
        assertThat(project.getArtifacts()).isEmpty();
    }

    @Test
    void failsClosedWhenRuntimeArtifactResolutionFails() throws Exception {
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

        assertThatThrownBy(() -> mojo.applyFeatureDependencies(planWithOnly(CocoFeature.WEB)))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Failed to resolve runtime dependency closure");
        assertThat(project.getModel().getDependencies()).isEmpty();
        assertThat(project.getArtifacts()).isEmpty();
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
    void addsResolvedTransitiveClosureToBothMavenArtifactViews() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("transitive-closure"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        CocoFeaturesMojo mojo = newMojo(project);
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");
        set(mojo, "repositorySystem", repositorySystemReturning(request -> java.util.List.of(
                withResolvedFile(request.getCollectRequest().getRoot().getArtifact()),
                resolvedArtifact("com.example", "feature-runtime", "3.2.1"))));

        mojo.applyFeatureDependencies(planWithOnly(CocoFeature.WEB));

        assertThat(project.getArtifacts())
                .extracting(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId())
                .containsExactlyInAnyOrder("io.github.patton174:coco-web", "com.example:feature-runtime");
        assertThat(project.getDependencyArtifacts())
                .extracting(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId())
                .containsExactlyInAnyOrder("io.github.patton174:coco-web", "com.example:feature-runtime");
    }

    @Test
    void failsClosedWhenResolvedClosureOmitsDirectFeatureArtifact() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("missing-direct-closure"));
        Path output = Files.createDirectories(baseDir.resolve("target/classes"));
        MavenProject project = project(baseDir, output);
        CocoFeaturesMojo mojo = newMojo(project);
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");
        set(mojo, "repositorySystem", repositorySystemReturning(request -> java.util.List.of(
                resolvedArtifact("com.example", "feature-runtime", "3.2.1"))));

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
        return new DefaultArtifact(groupId, artifactId, version,
                Artifact.SCOPE_RUNTIME, "jar", null, new DefaultArtifactHandler("jar"));
    }

    private Dependency dependency(String groupId, String artifactId) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setVersion("1.0.0-SNAPSHOT");
        return dependency;
    }

    private CocoFeaturePlan planWithOnly(CocoFeature feature) {
        EnumSet<CocoFeature> disabled = EnumSet.allOf(CocoFeature.class);
        disabled.remove(feature);
        return new CocoFeaturePlan(Set.of(feature), disabled, StandardCocoFeatures.all());
    }

    private CocoFeaturesMojo newMojo(MavenProject project) throws Exception {
        CocoFeaturesMojo mojo = new CocoFeaturesMojo();
        set(mojo, "project", project);
        set(mojo, "featureGroupId", "io.github.patton174");
        set(mojo, "repositorySystem", repositorySystemReturning(request -> java.util.List.of(
                withResolvedFile(request.getCollectRequest().getRoot().getArtifact()))));
        set(mojo, "repositorySystemSession", repositorySystemSession());
        set(mojo, "remoteRepositories", List.of());
        return mojo;
    }

    private RepositorySystem repositorySystemReturning(
            java.util.function.Function<DependencyRequest,
                    java.util.List<org.eclipse.aether.artifact.Artifact>> resolution) {
        return (RepositorySystem) Proxy.newProxyInstance(
                RepositorySystem.class.getClassLoader(),
                new Class<?>[] { RepositorySystem.class },
                (proxy, method, arguments) -> {
                    if ("resolveDependencies".equals(method.getName())) {
                        DependencyRequest request = (DependencyRequest) arguments[1];
                        DependencyResult result = new DependencyResult(request);
                        result.setArtifactResults(resolution.apply(request).stream()
                                .map(artifact -> new ArtifactResult(new ArtifactRequest(artifact, List.of(), null))
                                        .setArtifact(artifact))
                                .toList());
                        return result;
                    }
                    return proxyObjectMethod(proxy, method.getName(), arguments);
                });
    }

    private org.eclipse.aether.artifact.Artifact withResolvedFile(
            org.eclipse.aether.artifact.Artifact artifact) {
        return artifact.setFile(resolvedArtifactFile(artifact.getArtifactId(), artifact.getVersion()).toFile());
    }

    private org.eclipse.aether.artifact.Artifact resolvedArtifact(String groupId, String artifactId,
            String version) {
        return new org.eclipse.aether.artifact.DefaultArtifact(groupId, artifactId, "jar", version)
                .setFile(resolvedArtifactFile(artifactId, version).toFile());
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

    private RepositorySystem failingRepositorySystem() {
        return (RepositorySystem) Proxy.newProxyInstance(
                RepositorySystem.class.getClassLoader(),
                new Class<?>[] { RepositorySystem.class },
                (proxy, method, arguments) -> {
                    if ("resolveDependencies".equals(method.getName())) {
                        DependencyRequest request = (DependencyRequest) arguments[1];
                        DependencyResult result = new DependencyResult(request);
                        throw new DependencyResolutionException(result, "artifact unavailable",
                                new IllegalStateException("artifact unavailable"));
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
