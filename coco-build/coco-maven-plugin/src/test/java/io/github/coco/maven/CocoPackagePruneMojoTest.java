package io.github.coco.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.model.CocoFeatureManifestLoader;
import io.github.coco.feature.model.CocoFeatureSelection;
import io.github.coco.feature.model.StandardCocoFeatures;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.RepositorySystemSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Coco 业务应用打包裁剪 Mojo 测试。
 * <p>
 * 验证 Spring Boot 可执行包会根据 Coco 功能清单移除被禁用的功能模块依赖。
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
class CocoPackagePruneMojoTest {

    @TempDir
    Path tempDir;

    private final Map<String, byte[]> resolvedArtifactBytes = new HashMap<>();

    @Test
    void removesDisabledFeatureJarsFromSpringBootArchive() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("project"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.TENANT, CocoFeature.DATA_PERMISSION));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeArchive(archivePath);

        CocoPackagePruneMojo mojo = newMojo();
        set(mojo, "project", project(baseDir, buildDirectory, classesDirectory));
        set(mojo, "classesDirectory", classesDirectory.toFile());
        set(mojo, "buildDirectory", buildDirectory.toFile());
        set(mojo, "finalName", "demo");

        mojo.execute();

        assertThat(entries(archivePath))
                .contains(
                        "BOOT-INF/classpath.idx",
                        "BOOT-INF/layers.idx",
                        "BOOT-INF/classes/application.yml",
                        "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar")
                .doesNotContain(
                        "BOOT-INF/lib/coco-tenant-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-feature-tenant-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-data-permission-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-feature-data-permission-1.0.0-SNAPSHOT.jar");
        assertThat(readEntry(archivePath, "BOOT-INF/classpath.idx"))
                .contains("coco-web")
                .doesNotContain(
                        "coco-tenant",
                        "coco-feature-tenant",
                        "coco-data-permission",
                        "coco-feature-data-permission");
        assertThat(readEntry(archivePath, "BOOT-INF/layers.idx"))
                .contains("coco-web")
                .doesNotContain(
                        "coco-tenant",
                        "coco-feature-tenant",
                        "coco-data-permission",
                        "coco-feature-data-permission");
        assertRunnableSpringBootArchive(archivePath);
        Path originalArchivePath = buildDirectory.resolve("coco-prune.original.jar");
        assertThat(originalArchivePath).isRegularFile();
        assertThat(entries(originalArchivePath))
                .contains(
                        "BOOT-INF/lib/coco-tenant-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-feature-tenant-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-data-permission-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-feature-data-permission-1.0.0-SNAPSHOT.jar");
        assertThat(readEntry(originalArchivePath, "BOOT-INF/classpath.idx"))
                .contains(
                        "coco-tenant",
                        "coco-feature-tenant",
                        "coco-data-permission",
                        "coco-feature-data-permission");
    }

    @Test
    void removesOnlyDisabledCocoFeatureJarsFromSpringBootArchive() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("mybatis"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.MYBATIS_PLUS));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeMybatisArchive(archivePath);

        CocoPackagePruneMojo mojo = newMojo();
        set(mojo, "project", project(baseDir, buildDirectory, classesDirectory));
        set(mojo, "classesDirectory", classesDirectory.toFile());
        set(mojo, "buildDirectory", buildDirectory.toFile());
        set(mojo, "finalName", "demo");

        mojo.execute();

        assertThat(entries(archivePath))
                .contains(
                        "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/mybatis-3.5.19.jar",
                        "BOOT-INF/lib/mybatis-plus-core-3.5.16.jar",
                        "BOOT-INF/lib/mybatis-plus-jsqlparser-common-3.5.16.jar",
                        "BOOT-INF/lib/mybatis-plus-spring-3.5.16.jar",
                        "BOOT-INF/lib/mybatis-plus-spring-boot-native-image-3.5.17.jar",
                        "BOOT-INF/lib/mybatis-plus-spring-boot4-starter-3.5.16.jar",
                        "BOOT-INF/lib/mybatis-spring-3.0.5.jar",
                        "BOOT-INF/lib/freemarker-2.3.34.jar",
                        "BOOT-INF/lib/mybatis-extra-1.0.0.jar",
                        "BOOT-INF/lib/spring-jdbc-7.0.0.jar")
                .doesNotContain(
                        "BOOT-INF/lib/coco-mybatis-plus-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-feature-mybatis-plus-1.0.0-SNAPSHOT.jar");
        assertThat(readEntry(archivePath, "BOOT-INF/classpath.idx"))
                .contains("coco-audit", "mybatis-3.5.19", "mybatis-plus-core",
                        "mybatis-plus-jsqlparser-common", "mybatis-plus-spring-boot-native-image",
                        "mybatis-plus-spring-boot4-starter", "mybatis-spring", "freemarker", "spring-jdbc")
                .doesNotContain("coco-mybatis-plus", "coco-feature-mybatis-plus");
        assertThat(readEntry(archivePath, "BOOT-INF/layers.idx"))
                .contains("coco-audit", "mybatis-3.5.19", "mybatis-plus-core",
                        "mybatis-plus-jsqlparser-common", "mybatis-plus-spring-boot-native-image",
                        "mybatis-plus-spring-boot4-starter", "mybatis-spring", "freemarker", "spring-jdbc")
                .doesNotContain("coco-mybatis-plus", "coco-feature-mybatis-plus");
        assertRunnableSpringBootArchive(archivePath);
    }

    @Test
    void removesResolverProvenDisabledClosureAndKeepsDirectThirdPartyArtifacts() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("resolved-disabled-closure"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.MYBATIS_PLUS));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeMybatisArchive(archivePath);

        MavenProject project = project(baseDir, buildDirectory, classesDirectory);
        Dependency starter = dependency("io.github.patton174", "coco-spring-boot-starter", "1.0.0-SNAPSHOT");
        project.getModel().addDependency(starter);
        project.getModel().addDependency(dependency("com.example", "mybatis-extra", "1.0.0"));
        project.getModel().addDependency(dependency("org.springframework", "spring-jdbc", "7.0.0"));
        project.setArtifacts(mybatisArchiveArtifacts());

        List<org.eclipse.aether.graph.Dependency> survivingDependencies = List.of(
                resolvedDependency("io.github.patton174", "coco-web", "1.0.0-SNAPSHOT"),
                resolvedDependency("io.github.patton174", "coco-audit", "1.0.0-SNAPSHOT"),
                resolvedDependency("com.example", "mybatis-extra", "1.0.0"),
                resolvedDependency("org.springframework", "spring-jdbc", "7.0.0"));
        CocoPackagePruneMojo mojo = newMojo();
        set(mojo, "project", project);
        set(mojo, "projectDependenciesResolver", projectDependenciesResolverReturning(request -> {
            Dependency stagedStarter = request.getMavenProject().getModel().getDependencies().stream()
                    .filter(dependency -> "coco-spring-boot-starter".equals(dependency.getArtifactId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(stagedStarter.getExclusions())
                    .extracting(exclusion -> exclusion.getGroupId() + ":" + exclusion.getArtifactId())
                    .contains(
                            "io.github.patton174:coco-mybatis-plus",
                            "io.github.patton174:coco-feature-mybatis-plus",
                            "io.github.patton174:coco-feature-codegen");
            return survivingDependencies;
        }));
        set(mojo, "repositorySystemSession", repositorySystemSession());
        set(mojo, "classesDirectory", classesDirectory.toFile());
        set(mojo, "buildDirectory", buildDirectory.toFile());
        set(mojo, "finalName", "demo");

        mojo.execute();

        assertThat(entries(archivePath))
                .contains(
                        "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/mybatis-extra-1.0.0.jar",
                        "BOOT-INF/lib/spring-jdbc-7.0.0.jar")
                .doesNotContain(
                        "BOOT-INF/lib/coco-mybatis-plus-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-feature-mybatis-plus-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/mybatis-3.5.19.jar",
                        "BOOT-INF/lib/mybatis-plus-core-3.5.16.jar",
                        "BOOT-INF/lib/freemarker-2.3.34.jar");
        assertThat(readEntry(archivePath, "BOOT-INF/classpath.idx"))
                .contains("mybatis-extra-1.0.0.jar", "spring-jdbc-7.0.0.jar")
                .doesNotContain("coco-mybatis-plus", "mybatis-3.5.19", "mybatis-plus-core", "freemarker");
        assertThat(readEntry(archivePath, "BOOT-INF/layers.idx"))
                .contains("mybatis-extra-1.0.0.jar", "spring-jdbc-7.0.0.jar")
                .doesNotContain("coco-mybatis-plus", "mybatis-3.5.19", "mybatis-plus-core", "freemarker");
        assertThat(starter.getExclusions()).isEmpty();
        assertRunnableSpringBootArchive(archivePath);
        Path originalArchive = buildDirectory.resolve("coco-prune.original.jar");
        Set<String> removedLibraries = difference(bootLibraries(originalArchive), bootLibraries(archivePath));
        assertThat(removedLibraries).hasSize(10)
                .isEqualTo(difference(indexReferences(originalArchive, "BOOT-INF/classpath.idx"),
                        indexReferences(archivePath, "BOOT-INF/classpath.idx")))
                .isEqualTo(difference(indexReferences(originalArchive, "BOOT-INF/layers.idx"),
                        indexReferences(archivePath, "BOOT-INF/layers.idx")));
        assertIndexesMatchLibraries(archivePath);
    }

    @Test
    void rejectsResolverCandidateWithForgedMatchingMetadataButDifferentBytes() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("forged-resolver-bytes"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.MYBATIS_PLUS));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeMybatisArchive(archivePath);
        MavenProject project = project(baseDir, buildDirectory, classesDirectory);
        org.apache.maven.artifact.Artifact forged = resolvedArtifact(
                "org.mybatis", "mybatis", "3.5.19");
        Files.writeString(forged.getFile().toPath(), "different artifact bytes", StandardCharsets.UTF_8);
        project.setArtifacts(new LinkedHashSet<>(List.of(forged)));
        CocoPackagePruneMojo mojo = newMojo();
        set(mojo, "project", project);
        set(mojo, "classesDirectory", classesDirectory.toFile());
        set(mojo, "buildDirectory", buildDirectory.toFile());
        set(mojo, "finalName", "demo");

        assertArchiveRejected(mojo, archivePath,
                "Resolved Maven artifact SHA-256 does not match prunable nested library");
    }

    @Test
    void rejectsResolverCandidateWithMatchingBytesButDifferentMavenGav() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("forged-resolver-gav"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeArchiveWithRawPomAndNestedEntries(archivePath,
                "META-INF/maven/com.example/coco-web/pom.properties",
                "groupId=com.example\nartifactId=coco-web\nversion=1.0.0-SNAPSHOT\n", Map.of());
        org.apache.maven.artifact.Artifact candidate = resolvedArtifact(
                "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
        Files.write(candidate.getFile().toPath(),
                readEntryBytes(archivePath, "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar"));
        MavenProject project = project(baseDir, buildDirectory, classesDirectory);
        project.setArtifacts(new LinkedHashSet<>(List.of(candidate)));
        CocoPackagePruneMojo mojo = configuredMojo(baseDir, buildDirectory, classesDirectory);
        set(mojo, "project", project);

        assertArchiveRejected(mojo, archivePath,
                "Nested Maven metadata does not match resolved Maven GAV");
    }

    @Test
    void rejectsResolverCandidateWithMatchingBytesButNoMavenMetadata() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("resolver-no-metadata"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        byte[] nestedLibrary = nestedLibraryWithoutMavenMetadata();
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeArchiveWithNestedLibrary(archivePath,
                "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar", nestedLibrary);
        org.apache.maven.artifact.Artifact candidate = resolvedArtifact(
                "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
        setArtifactFileOutsideRepository(candidate, "no-metadata", nestedLibrary);
        MavenProject project = project(baseDir, buildDirectory, classesDirectory);
        project.setArtifacts(new LinkedHashSet<>(List.of(candidate)));
        CocoPackagePruneMojo mojo = configuredMojo(baseDir, buildDirectory, classesDirectory);
        set(mojo, "project", project);

        assertArchiveRejected(mojo, archivePath,
                "Nested artifact identity does not match resolved Maven GAV");
    }

    @Test
    void acceptsResolverCandidateWithoutMetadataFromExactMavenRepositoryLayout() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("resolver-repository-identity"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        byte[] nestedLibrary = nestedLibraryWithoutMavenMetadata();
        Path archivePath = buildDirectory.resolve("demo.jar");
        String entryName = "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar";
        writeArchiveWithNestedLibrary(archivePath, entryName, nestedLibrary);
        org.apache.maven.artifact.Artifact candidate = resolvedArtifact(
                "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
        Files.write(candidate.getFile().toPath(), nestedLibrary);
        MavenProject project = project(baseDir, buildDirectory, classesDirectory);
        project.setArtifacts(new LinkedHashSet<>(List.of(candidate)));
        CocoPackagePruneMojo mojo = configuredMojo(baseDir, buildDirectory, classesDirectory);
        set(mojo, "project", project);

        mojo.execute();

        assertThat(bootLibraries(archivePath)).doesNotContain(entryName);
    }

    @Test
    void acceptsResolverCandidateWithMatchingBytesAndExactManifestIdentity() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("resolver-manifest-identity"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        byte[] nestedLibrary = nestedLibraryWithManifestIdentity(
                "io.github.patton174.coco-web", "1.0.0-SNAPSHOT");
        Path archivePath = buildDirectory.resolve("demo.jar");
        String entryName = "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar";
        writeArchiveWithNestedLibrary(archivePath, entryName, nestedLibrary);
        org.apache.maven.artifact.Artifact candidate = resolvedArtifact(
                "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
        setArtifactFileOutsideRepository(candidate, "manifest-identity", nestedLibrary);
        MavenProject project = project(baseDir, buildDirectory, classesDirectory);
        project.setArtifacts(new LinkedHashSet<>(List.of(candidate)));
        CocoPackagePruneMojo mojo = configuredMojo(baseDir, buildDirectory, classesDirectory);
        set(mojo, "project", project);

        mojo.execute();

        assertThat(bootLibraries(archivePath)).doesNotContain(entryName);
        assertThat(bootLibraries(buildDirectory.resolve("coco-prune.original.jar"))).contains(entryName);
    }

    @Test
    void rejectsResolverCandidateWithMatchingBytesButMismatchedManifestIdentity() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("resolver-forged-manifest-identity"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        byte[] nestedLibrary = nestedLibraryWithManifestIdentity(
                "io.github.patton174.coco-web", "9.9.9");
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeArchiveWithNestedLibrary(archivePath,
                "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar", nestedLibrary);
        org.apache.maven.artifact.Artifact candidate = resolvedArtifact(
                "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
        setArtifactFileOutsideRepository(candidate, "forged-manifest-identity", nestedLibrary);
        MavenProject project = project(baseDir, buildDirectory, classesDirectory);
        project.setArtifacts(new LinkedHashSet<>(List.of(candidate)));
        CocoPackagePruneMojo mojo = configuredMojo(baseDir, buildDirectory, classesDirectory);
        set(mojo, "project", project);

        assertArchiveRejected(mojo, archivePath,
                "Nested artifact identity does not match resolved Maven GAV");
    }

    @Test
    void boundsResolvedArtifactFingerprintBeforeArchiveRewrite() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("resolver-hash-budget"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeArchiveWithoutIndexes(archivePath);
        MavenProject project = project(baseDir, buildDirectory, classesDirectory);
        org.apache.maven.artifact.Artifact candidate = resolvedArtifact(
                "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
        project.setArtifacts(new LinkedHashSet<>(List.of(candidate)));
        CocoPackagePruneMojo mojo = configuredMojo(baseDir, buildDirectory, classesDirectory);
        set(mojo, "project", project);
        CocoArchiveLimits defaults = CocoArchiveLimits.DEFAULT;
        set(mojo, "archiveLimits", withOuterLimits(defaults,
                Files.size(candidate.getFile().toPath()) - 1, defaults.outerTotalBytes(),
                defaults.entryCount(), defaults.entryNameBytes()));

        assertArchiveRejected(mojo, archivePath,
                "Resolved dependency artifact");
    }

    @Test
    void boundsResolvedArtifactFingerprintsCumulativelyAcrossFilesAtTheExactBoundary() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("resolver-cumulative-budget"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeArchiveWithoutIndexes(archivePath);
        Set<org.apache.maven.artifact.Artifact> candidates = new LinkedHashSet<>(List.of(
                resolvedArtifact("io.github.patton174", "coco-web", "1.0.0-SNAPSHOT"),
                resolvedArtifact("io.github.patton174", "coco-audit", "1.0.0-SNAPSHOT")));
        long cumulativeBytes = candidates.stream()
                .mapToLong(candidate -> candidate.getFile().length()).sum();
        assertThat(candidates).allMatch(candidate -> candidate.getFile().length() < cumulativeBytes);
        MavenProject project = project(baseDir, buildDirectory, classesDirectory);
        project.setArtifacts(candidates);
        CocoPackagePruneMojo mojo = configuredMojo(baseDir, buildDirectory, classesDirectory);
        set(mojo, "project", project);
        set(mojo, "archiveLimits", withResolvedArtifactsBytes(CocoArchiveLimits.DEFAULT, cumulativeBytes - 1));

        assertArchiveRejected(mojo, archivePath, "Resolved artifact cumulative SHA-256 bytes");

        writeArchiveWithoutIndexes(archivePath);
        set(mojo, "archiveLimits", withResolvedArtifactsBytes(CocoArchiveLimits.DEFAULT, cumulativeBytes));
        mojo.execute();
        assertThat(buildDirectory.resolve("coco-prune.original.jar")).isRegularFile();
    }

    @Test
    void resolutionFailureDoesNotPartiallyRewriteArchiveOrProjectModel() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("closure-resolution-failure"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.MYBATIS_PLUS));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeMybatisArchive(archivePath);

        MavenProject project = project(baseDir, buildDirectory, classesDirectory);
        Dependency starter = dependency("io.github.patton174", "coco-spring-boot-starter", "1.0.0-SNAPSHOT");
        project.getModel().addDependency(starter);
        project.setArtifacts(new LinkedHashSet<>(List.of(
                resolvedArtifact("io.github.patton174", "coco-mybatis-plus", "1.0.0-SNAPSHOT"))));
        byte[] originalArchive = Files.readAllBytes(archivePath);

        CocoPackagePruneMojo mojo = newMojo();
        set(mojo, "project", project);
        set(mojo, "projectDependenciesResolver", (ProjectDependenciesResolver) request -> {
            DependencyResolutionResult result = dependencyResolutionResult(List.of());
            throw new org.apache.maven.project.DependencyResolutionException(
                    result, "resolver unavailable", new IllegalStateException("resolver unavailable"));
        });
        set(mojo, "repositorySystemSession", repositorySystemSession());
        set(mojo, "classesDirectory", classesDirectory.toFile());
        set(mojo, "buildDirectory", buildDirectory.toFile());
        set(mojo, "finalName", "demo");

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Failed to resolve project without disabled Coco features");
        assertThat(Files.readAllBytes(archivePath)).isEqualTo(originalArchive);
        assertThat(starter.getExclusions()).isEmpty();
        assertThat(buildDirectory.resolve("coco-prune.original.jar")).doesNotExist();
    }

    @Test
    void rejectsRewrittenArchiveWhenBootIndexDoesNotMatchLibraries() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("mismatched-rewritten-index"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.TENANT));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeArchiveWithMismatchedIndexes(archivePath);

        assertArchiveRejected(configuredMojo(baseDir, buildDirectory, classesDirectory), archivePath,
                "does not match BOOT-INF/lib");
    }

    @Test
    void postflightFailureDeletesTemporaryArchiveWithoutChangingOriginalOrCreatingBackup() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("postflight-failure"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeArchive(archivePath);
        byte[] original = Files.readAllBytes(archivePath);
        Path temporaryPath = buildDirectory.resolve("demo.jar.postflight.tmp");
        writeArchiveWithMismatchedIndexes(temporaryPath);
        CocoPackagePruneMojo mojo = configuredMojo(baseDir, buildDirectory, classesDirectory);
        CocoPackagePruneMojo.BootArchiveView sourceView = new CocoPackagePruneMojo.BootArchiveView(
                List.of(
                        "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-tenant-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-feature-tenant-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-data-permission-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-feature-data-permission-1.0.0-SNAPSHOT.jar"),
                Set.of("BOOT-INF/classpath.idx", "BOOT-INF/layers.idx"));

        assertThatThrownBy(() -> mojo.publishRewrittenArchive(
                archivePath, temporaryPath, sourceView, Set.of("BOOT-INF/lib/coco-tenant-1.0.0-SNAPSHOT.jar")))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("does not match BOOT-INF/lib");
        assertThat(Files.readAllBytes(archivePath)).isEqualTo(original);
        assertThat(temporaryPath).doesNotExist();
        assertThat(buildDirectory.resolve("coco-prune.original.jar")).doesNotExist();
    }

    @Test
    void backupAtomicMoveFailurePreservesExistingBackupAndMainArchive() throws Exception {
        ArchivePublicationFixture fixture = archivePublicationFixture("backup-move-failure");
        byte[] original = Files.readAllBytes(fixture.archivePath());
        Path backup = fixture.buildDirectory().resolve("coco-prune.original.jar");
        byte[] existingBackup = "existing-backup".getBytes(StandardCharsets.UTF_8);
        Files.write(backup, existingBackup);
        set(fixture.mojo(), "archiveFileOperations",
                (CocoPackagePruneMojo.ArchiveFileOperations) (source, target) -> {
                    if (target.equals(backup.toAbsolutePath().normalize())) {
                        throw new IOException("injected backup atomic move failure");
                    }
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                });

        assertThatThrownBy(() -> fixture.mojo().publishRewrittenArchive(
                fixture.archivePath(), fixture.temporaryPath(), fixture.sourceView(), fixture.prunedEntries()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("injected backup atomic move failure");
        assertThat(Files.readAllBytes(fixture.archivePath())).isEqualTo(original);
        assertThat(Files.readAllBytes(backup)).isEqualTo(existingBackup);
        assertNoPublicationTemps(fixture.buildDirectory());
    }

    @Test
    void finalAtomicMoveFailureRollsBackExistingBackupAndPreservesMainArchive() throws Exception {
        ArchivePublicationFixture fixture = archivePublicationFixture("final-move-failure");
        byte[] original = Files.readAllBytes(fixture.archivePath());
        Path backup = fixture.buildDirectory().resolve("coco-prune.original.jar");
        byte[] existingBackup = "existing-backup".getBytes(StandardCharsets.UTF_8);
        Files.write(backup, existingBackup);
        Set<PosixFilePermission> archivePermissions = null;
        Set<PosixFilePermission> backupPermissions = null;
        if (Files.getFileAttributeView(fixture.archivePath(), PosixFileAttributeView.class) != null) {
            archivePermissions = Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ);
            backupPermissions = Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(fixture.archivePath(), archivePermissions);
            Files.setPosixFilePermissions(backup, backupPermissions);
        }
        Path normalizedArchive = fixture.archivePath().toAbsolutePath().normalize();
        set(fixture.mojo(), "archiveFileOperations",
                (CocoPackagePruneMojo.ArchiveFileOperations) (source, target) -> {
                    if (target.equals(normalizedArchive)) {
                        throw new IOException("injected final atomic move failure");
                    }
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                });

        assertThatThrownBy(() -> fixture.mojo().publishRewrittenArchive(
                fixture.archivePath(), fixture.temporaryPath(), fixture.sourceView(), fixture.prunedEntries()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("injected final atomic move failure");
        assertThat(Files.readAllBytes(fixture.archivePath())).isEqualTo(original);
        assertThat(Files.readAllBytes(backup)).isEqualTo(existingBackup);
        if (archivePermissions != null) {
            assertThat(Files.getPosixFilePermissions(fixture.archivePath()))
                    .containsExactlyInAnyOrderElementsOf(archivePermissions);
            assertThat(Files.getPosixFilePermissions(backup))
                    .containsExactlyInAnyOrderElementsOf(backupPermissions);
        }
        assertNoPublicationTemps(fixture.buildDirectory());
    }

    @Test
    void rejectsMainArchiveThatCollidesWithTheFixedBackupPathBeforeAnyMove() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("backup-path-collision"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        Path archivePath = buildDirectory.resolve("coco-prune.original.jar");
        writeArchiveWithoutIndexes(archivePath);
        byte[] original = Files.readAllBytes(archivePath);
        CocoPackagePruneMojo mojo = configuredMojo(baseDir, buildDirectory, classesDirectory);
        set(mojo, "finalName", "coco-prune.original");
        set(mojo, "archiveFileOperations",
                (CocoPackagePruneMojo.ArchiveFileOperations) (source, target) -> {
                    throw new AssertionError("No archive move is allowed for a backup-path collision.");
                });

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasRootCauseInstanceOf(IOException.class)
                .hasStackTraceContaining("backup path collides with the main archive");
        assertThat(Files.readAllBytes(archivePath)).isEqualTo(original);
        assertNoPublicationTemps(buildDirectory);
    }

    @Test
    void rejectsHardLinkedBackupThatAliasesTheMainArchiveBeforeAnyMove() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("backup-hardlink-collision"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeArchiveWithoutIndexes(archivePath);
        byte[] original = Files.readAllBytes(archivePath);
        Path backupPath = buildDirectory.resolve("coco-prune.original.jar");
        Files.createLink(backupPath, archivePath);
        CocoPackagePruneMojo mojo = configuredMojo(baseDir, buildDirectory, classesDirectory);
        set(mojo, "archiveFileOperations",
                (CocoPackagePruneMojo.ArchiveFileOperations) (source, target) -> {
                    throw new AssertionError("No archive move is allowed for a hard-link collision.");
                });

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasRootCauseInstanceOf(IOException.class)
                .hasStackTraceContaining("backup path collides with the main archive");
        assertThat(Files.readAllBytes(archivePath)).isEqualTo(original);
        assertThat(Files.readAllBytes(backupPath)).isEqualTo(original);
        assertThat(Files.isSameFile(archivePath, backupPath)).isTrue();
        assertNoPublicationTemps(buildDirectory);
    }

    @Test
    void rejectsNormalizedBackupAliasBeforeTemporaryArchiveValidation() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("backup-normalized-alias"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path child = Files.createDirectories(buildDirectory.resolve("child"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        Path archivePath = buildDirectory.resolve("coco-prune.original.jar");
        writeArchiveWithoutIndexes(archivePath);
        byte[] original = Files.readAllBytes(archivePath);
        Path archiveAlias = child.resolve("..").resolve("coco-prune.original.jar");
        Path invalidTemporaryPath = buildDirectory.resolve("not-created.tmp");
        CocoPackagePruneMojo mojo = configuredMojo(baseDir, buildDirectory, classesDirectory);

        assertThatThrownBy(() -> mojo.publishRewrittenArchive(archiveAlias, invalidTemporaryPath,
                new CocoPackagePruneMojo.BootArchiveView(List.of(), Set.of()), Set.of()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("backup path collides with the main archive");
        assertThat(Files.readAllBytes(archivePath)).isEqualTo(original);
        assertThat(invalidTemporaryPath).doesNotExist();
    }

    @Test
    void rejectsSymbolicLinkBackupAliasBeforeAnyMove() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("backup-symlink-collision"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeArchiveWithoutIndexes(archivePath);
        byte[] original = Files.readAllBytes(archivePath);
        Path normalizedArchive = archivePath.toAbsolutePath().normalize();
        Path backupPath = buildDirectory.resolve("coco-prune.original.jar").toAbsolutePath().normalize();
        BasicFileAttributes archiveAttributes = Files.readAttributes(
                archivePath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        CocoPackagePruneMojo mojo = configuredMojo(baseDir, buildDirectory, classesDirectory);
        set(mojo, "archivePathInspector", new CocoPackagePruneMojo.ArchivePathInspector() {
            @Override
            public BasicFileAttributes readAttributes(Path path) throws IOException {
                if (path.equals(backupPath)) {
                    return symbolicLinkAttributes(archiveAttributes);
                }
                return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            }

            @Override
            public boolean isSameFile(Path first, Path second) {
                return first.equals(normalizedArchive) && second.equals(backupPath);
            }

            @Override
            public Set<PosixFilePermission> posixPermissions(Path path) throws IOException {
                return path.equals(backupPath) ? null
                        : CocoPackagePruneMojo.ArchivePathInspector.super.posixPermissions(path);
            }
        });
        set(mojo, "archiveFileOperations",
                (CocoPackagePruneMojo.ArchiveFileOperations) (source, target) -> {
                    throw new AssertionError("No archive move is allowed for a symbolic-link collision.");
                });

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasRootCauseInstanceOf(IOException.class)
                .hasStackTraceContaining("backup path collides with the main archive");
        assertThat(Files.readAllBytes(archivePath)).isEqualTo(original);
        assertThat(backupPath).doesNotExist();
        assertNoPublicationTemps(buildDirectory);
    }

    @Test
    void propagatesBackupAccessDeniedBeforeCreatingPublicationTemps() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("backup-access-denied"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeArchiveWithoutIndexes(archivePath);
        byte[] original = Files.readAllBytes(archivePath);
        Path backupPath = buildDirectory.resolve("coco-prune.original.jar").toAbsolutePath().normalize();
        CocoPackagePruneMojo mojo = configuredMojo(baseDir, buildDirectory, classesDirectory);
        set(mojo, "archivePathInspector", new CocoPackagePruneMojo.ArchivePathInspector() {
            @Override
            public BasicFileAttributes readAttributes(Path path) throws IOException {
                if (path.equals(backupPath)) {
                    throw new AccessDeniedException(path.toString());
                }
                return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            }

            @Override
            public boolean isSameFile(Path first, Path second) throws IOException {
                return Files.isSameFile(first, second);
            }
        });
        set(mojo, "archiveFileOperations",
                (CocoPackagePruneMojo.ArchiveFileOperations) (source, target) -> {
                    throw new AssertionError("No archive move is allowed after access denial.");
                });

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasRootCauseInstanceOf(AccessDeniedException.class);
        assertThat(Files.readAllBytes(archivePath)).isEqualTo(original);
        assertThat(backupPath).doesNotExist();
        assertNoPublicationTemps(buildDirectory);
    }

    @Test
    void abortsWhenAbsentBackupAppearsBeforeFirstMove() throws Exception {
        ArchivePublicationFixture fixture = archivePublicationFixture("backup-appears");
        byte[] original = Files.readAllBytes(fixture.archivePath());
        Path backupPath = fixture.buildDirectory().resolve("coco-prune.original.jar")
                .toAbsolutePath().normalize();
        byte[] appeared = "appeared-backup".getBytes(StandardCharsets.UTF_8);
        AtomicInteger backupReads = new AtomicInteger();
        set(fixture.mojo(), "archivePathInspector", new CocoPackagePruneMojo.ArchivePathInspector() {
            @Override
            public BasicFileAttributes readAttributes(Path path) throws IOException {
                if (path.equals(backupPath)) {
                    if (backupReads.incrementAndGet() == 1) {
                        throw new NoSuchFileException(path.toString());
                    }
                    if (!Files.isRegularFile(path)) {
                        Files.write(path, appeared);
                    }
                }
                return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            }

            @Override
            public boolean isSameFile(Path first, Path second) throws IOException {
                return Files.isSameFile(first, second);
            }
        });
        set(fixture.mojo(), "archiveFileOperations",
                (CocoPackagePruneMojo.ArchiveFileOperations) (source, target) -> {
                    throw new AssertionError("No archive move is allowed after backup state changed.");
                });

        assertThatThrownBy(() -> fixture.mojo().publishRewrittenArchive(
                fixture.archivePath(), fixture.temporaryPath(), fixture.sourceView(), fixture.prunedEntries()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("changed before publication");
        assertThat(backupReads).hasValue(2);
        assertThat(Files.readAllBytes(fixture.archivePath())).isEqualTo(original);
        assertThat(Files.readAllBytes(backupPath)).isEqualTo(appeared);
        assertThat(fixture.temporaryPath()).doesNotExist();
    }

    @Test
    void rollbackFailurePreservesAndReportsTheExactRecoveryPath() throws Exception {
        ArchivePublicationFixture fixture = archivePublicationFixture("rollback-recovery");
        byte[] original = Files.readAllBytes(fixture.archivePath());
        Path backupPath = fixture.buildDirectory().resolve("coco-prune.original.jar")
                .toAbsolutePath().normalize();
        byte[] existingBackup = "existing-backup".getBytes(StandardCharsets.UTF_8);
        Files.write(backupPath, existingBackup);
        Path normalizedArchive = fixture.archivePath().toAbsolutePath().normalize();
        AtomicInteger backupMoves = new AtomicInteger();
        AtomicReference<Path> recoveryPath = new AtomicReference<>();
        set(fixture.mojo(), "archiveFileOperations",
                (CocoPackagePruneMojo.ArchiveFileOperations) (source, target) -> {
                    if (target.equals(normalizedArchive)) {
                        throw new IOException("injected final atomic move failure");
                    }
                    if (target.equals(backupPath) && backupMoves.incrementAndGet() == 2) {
                        recoveryPath.set(source.toAbsolutePath().normalize());
                        throw new IOException("injected rollback atomic move failure");
                    }
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                });

        assertThatThrownBy(() -> fixture.mojo().publishRewrittenArchive(
                fixture.archivePath(), fixture.temporaryPath(), fixture.sourceView(), fixture.prunedEntries()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("injected final atomic move failure")
                .satisfies(primary -> assertThat(primary.getSuppressed())
                        .singleElement()
                        .satisfies(suppressed -> assertThat(suppressed.getMessage())
                                .contains("recovery bytes are preserved at")
                                .contains(recoveryPath.get().toString())));
        assertThat(Files.readAllBytes(fixture.archivePath())).isEqualTo(original);
        assertThat(Files.readAllBytes(recoveryPath.get())).isEqualTo(existingBackup);
    }

    @Test
    void rejectsTemporaryReparsePointBeforeValidationOrAnyMove() throws Exception {
        ArchiveExecutionFixture fixture = archiveExecutionFixture("temporary-reparse-execute");
        ProjectViewSnapshot projectViews = primeProjectViews(fixture.project());
        byte[] original = Files.readAllBytes(fixture.archivePath());
        Path normalizedArchive = fixture.archivePath().toAbsolutePath().normalize();
        set(fixture.mojo(), "archivePathInspector", new CocoPackagePruneMojo.ArchivePathInspector() {
            @Override
            public BasicFileAttributes readAttributes(Path path) throws IOException {
                BasicFileAttributes attributes = Files.readAttributes(
                        path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                return isRewrittenTemporaryArchive(path, normalizedArchive)
                        ? otherAttributes(attributes) : attributes;
            }

            @Override
            public boolean isSameFile(Path first, Path second) throws IOException {
                return Files.isSameFile(first, second);
            }
        });
        set(fixture.mojo(), "archiveFileOperations",
                (CocoPackagePruneMojo.ArchiveFileOperations) (source, target) -> {
                    throw new AssertionError("No archive move is allowed for a temporary reparse point.");
                });

        assertThatThrownBy(fixture.mojo()::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasStackTraceContaining("must not be a symbolic link or reparse point");
        assertThat(Files.readAllBytes(fixture.archivePath())).isEqualTo(original);
        assertThat(fixture.buildDirectory().resolve("coco-prune.original.jar")).doesNotExist();
        assertProjectViewsUnchanged(fixture.project(), projectViews);
        assertNoPublicationTemps(fixture.buildDirectory());
    }

    @Test
    void rejectsTemporaryArchiveOnDifferentFileStoreBeforeValidationOrAnyMove() throws Exception {
        ArchiveExecutionFixture fixture = archiveExecutionFixture("temporary-foreign-store-execute");
        ProjectViewSnapshot projectViews = primeProjectViews(fixture.project());
        byte[] original = Files.readAllBytes(fixture.archivePath());
        Path normalizedArchive = fixture.archivePath().toAbsolutePath().normalize();
        FileStore foreignStore = new TestFileStore("foreign");
        set(fixture.mojo(), "archivePathInspector", new CocoPackagePruneMojo.ArchivePathInspector() {
            @Override
            public BasicFileAttributes readAttributes(Path path) throws IOException {
                return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            }

            @Override
            public boolean isSameFile(Path first, Path second) throws IOException {
                return Files.isSameFile(first, second);
            }

            @Override
            public FileStore fileStore(Path path) throws IOException {
                return isRewrittenTemporaryArchive(path, normalizedArchive)
                        ? foreignStore : Files.getFileStore(path);
            }
        });
        set(fixture.mojo(), "archiveFileOperations",
                (CocoPackagePruneMojo.ArchiveFileOperations) (source, target) -> {
                    throw new AssertionError("No archive move is allowed across file stores.");
                });

        assertThatThrownBy(fixture.mojo()::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasStackTraceContaining("real directory and file store");
        assertThat(Files.readAllBytes(fixture.archivePath())).isEqualTo(original);
        assertThat(fixture.buildDirectory().resolve("coco-prune.original.jar")).doesNotExist();
        assertProjectViewsUnchanged(fixture.project(), projectViews);
        assertNoPublicationTemps(fixture.buildDirectory());
    }

    @Test
    void temporaryIdentityChangeBeforeFinalMoveRollsBackBackupAndPreservesMain() throws Exception {
        ArchivePublicationFixture fixture = archivePublicationFixture("temporary-final-swap");
        byte[] original = Files.readAllBytes(fixture.archivePath());
        Path backupPath = fixture.buildDirectory().resolve("coco-prune.original.jar");
        byte[] existingBackup = "existing-backup".getBytes(StandardCharsets.UTF_8);
        Files.write(backupPath, existingBackup);
        Path normalizedTemporary = fixture.temporaryPath().toAbsolutePath().normalize();
        AtomicInteger temporaryReads = new AtomicInteger();
        set(fixture.mojo(), "archivePathInspector", new CocoPackagePruneMojo.ArchivePathInspector() {
            @Override
            public BasicFileAttributes readAttributes(Path path) throws IOException {
                BasicFileAttributes attributes = Files.readAttributes(
                        path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (path.equals(normalizedTemporary) && temporaryReads.incrementAndGet() == 5) {
                    return attributesWithSize(attributes, attributes.size() + 1);
                }
                return attributes;
            }

            @Override
            public boolean isSameFile(Path first, Path second) throws IOException {
                return Files.isSameFile(first, second);
            }
        });

        assertThatThrownBy(() -> fixture.mojo().publishRewrittenArchive(
                fixture.archivePath(), fixture.temporaryPath(), fixture.sourceView(), fixture.prunedEntries()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Rewritten archive changed before publication");
        assertThat(temporaryReads).hasValue(5);
        assertThat(Files.readAllBytes(fixture.archivePath())).isEqualTo(original);
        assertThat(Files.readAllBytes(backupPath)).isEqualTo(existingBackup);
        assertNoPublicationTemps(fixture.buildDirectory());
    }

    @Test
    void executeWrapsBackupMoveFailureWithoutMutatingArchiveBackupOrProjectViews() throws Exception {
        ArchiveExecutionFixture fixture = archiveExecutionFixture("execute-backup-move-failure");
        ProjectViewSnapshot projectViews = primeProjectViews(fixture.project());
        byte[] original = Files.readAllBytes(fixture.archivePath());
        Path backupPath = fixture.buildDirectory().resolve("coco-prune.original.jar")
                .toAbsolutePath().normalize();
        byte[] existingBackup = "existing-backup".getBytes(StandardCharsets.UTF_8);
        Files.write(backupPath, existingBackup);
        set(fixture.mojo(), "archiveFileOperations",
                (CocoPackagePruneMojo.ArchiveFileOperations) (source, target) -> {
                    if (target.equals(backupPath)) {
                        throw new IOException("injected execute backup move failure");
                    }
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                });

        assertThatThrownBy(fixture.mojo()::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasRootCauseMessage("injected execute backup move failure");
        assertThat(Files.readAllBytes(fixture.archivePath())).isEqualTo(original);
        assertThat(Files.readAllBytes(backupPath)).isEqualTo(existingBackup);
        assertProjectViewsUnchanged(fixture.project(), projectViews);
        assertNoPublicationTemps(fixture.buildDirectory());
    }

    @Test
    void executeRollsBackFinalMoveFailureWithoutMutatingArchiveBackupOrProjectViews() throws Exception {
        ArchiveExecutionFixture fixture = archiveExecutionFixture("execute-final-move-failure");
        ProjectViewSnapshot projectViews = primeProjectViews(fixture.project());
        byte[] original = Files.readAllBytes(fixture.archivePath());
        Path backupPath = fixture.buildDirectory().resolve("coco-prune.original.jar")
                .toAbsolutePath().normalize();
        byte[] existingBackup = "existing-backup".getBytes(StandardCharsets.UTF_8);
        Files.write(backupPath, existingBackup);
        Path normalizedArchive = fixture.archivePath().toAbsolutePath().normalize();
        set(fixture.mojo(), "archiveFileOperations",
                (CocoPackagePruneMojo.ArchiveFileOperations) (source, target) -> {
                    if (target.equals(normalizedArchive)) {
                        throw new IOException("injected execute final move failure");
                    }
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                });

        assertThatThrownBy(fixture.mojo()::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasRootCauseMessage("injected execute final move failure");
        assertThat(Files.readAllBytes(fixture.archivePath())).isEqualTo(original);
        assertThat(Files.readAllBytes(backupPath)).isEqualTo(existingBackup);
        assertProjectViewsUnchanged(fixture.project(), projectViews);
        assertNoPublicationTemps(fixture.buildDirectory());
    }

    @Test
    void executeReportsAndPreservesRollbackRecoveryAfterFinalMoveFailure() throws Exception {
        ArchiveExecutionFixture fixture = archiveExecutionFixture("execute-rollback-recovery");
        ProjectViewSnapshot projectViews = primeProjectViews(fixture.project());
        byte[] original = Files.readAllBytes(fixture.archivePath());
        Path backupPath = fixture.buildDirectory().resolve("coco-prune.original.jar")
                .toAbsolutePath().normalize();
        byte[] existingBackup = "existing-backup".getBytes(StandardCharsets.UTF_8);
        Files.write(backupPath, existingBackup);
        Path normalizedArchive = fixture.archivePath().toAbsolutePath().normalize();
        AtomicInteger backupMoves = new AtomicInteger();
        AtomicReference<Path> recoveryPath = new AtomicReference<>();
        set(fixture.mojo(), "archiveFileOperations",
                (CocoPackagePruneMojo.ArchiveFileOperations) (source, target) -> {
                    if (target.equals(normalizedArchive)) {
                        throw new IOException("injected execute final move failure");
                    }
                    if (target.equals(backupPath) && backupMoves.incrementAndGet() == 2) {
                        recoveryPath.set(source.toAbsolutePath().normalize());
                        throw new IOException("injected execute rollback move failure");
                    }
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                });

        assertThatThrownBy(fixture.mojo()::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasRootCauseMessage("injected execute final move failure")
                .satisfies(wrapper -> assertThat(wrapper.getCause().getSuppressed())
                        .singleElement()
                        .satisfies(suppressed -> assertThat(suppressed.getMessage())
                                .contains(recoveryPath.get().toString())));
        assertThat(Files.readAllBytes(fixture.archivePath())).isEqualTo(original);
        assertThat(Files.readAllBytes(recoveryPath.get())).isEqualTo(existingBackup);
        assertProjectViewsUnchanged(fixture.project(), projectViews);
    }

    @Test
    void allowsMissingBootIndexesAndPreservesTheirAbsence() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("missing-indexes"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeArchiveWithoutIndexes(archivePath);

        configuredMojo(baseDir, buildDirectory, classesDirectory).execute();

        assertThat(entries(archivePath))
                .contains("BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar")
                .doesNotContain(
                        "BOOT-INF/classpath.idx",
                        "BOOT-INF/layers.idx",
                        "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar");
    }

    @Test
    void rejectsInvalidBootIndexEncodingFormatPathsAndDuplicatesWithoutRewriting() throws Exception {
        List<byte[]> invalidIndexes = List.of(
                new byte[] {(byte) 0xc3, (byte) 0x28},
                "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar\n"
                        .getBytes(StandardCharsets.UTF_8),
                "- \"BOOT-INF/lib/../coco-web-1.0.0-SNAPSHOT.jar\"\n"
                        .getBytes(StandardCharsets.UTF_8),
                ("- \"BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar\"\n"
                        + "- \"BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar\"\n"
                        + "- \"BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar\"\n")
                        .getBytes(StandardCharsets.UTF_8));
        List<String> expectedMessages = List.of(
                "not valid UTF-8",
                "must have a sequence of library paths",
                "Non-canonical Spring Boot library index path",
                "duplicate library reference");

        for (int index = 0; index < invalidIndexes.size(); index++) {
            Path baseDir = Files.createDirectories(this.tempDir.resolve("invalid-index-" + index));
            Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
            Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
            writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
            Path archivePath = buildDirectory.resolve("demo.jar");
            writeArchiveWithClasspathIndex(archivePath, invalidIndexes.get(index));

            assertArchiveRejected(configuredMojo(baseDir, buildDirectory, classesDirectory), archivePath,
                    expectedMessages.get(index));
        }
    }

    @Test
    void rejectsNonFiniteLayersIndexSyntaxWithoutRewriting() throws Exception {
        List<String> invalidIndexes = List.of(
                "- &defaults \"dependencies\":\n  - \"BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar\"\n",
                "- \"dependencies\":\n  - !!str \"BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar\"\n",
                "- \"dependencies\":\n  - *defaults\n",
                "- \"dependencies\":\n  - \"BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar\"\n"
                        + "---\n- \"dependencies\":\n  - \"BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar\"\n",
                "- \"custom\":\n  - \"BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar\"\n",
                "- \"dependencies\":\n  - \"BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar\"\n"
                        + "- \"dependencies\":\n  - \"BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar\"\n",
                "- \"dependencies\":\n  - \"BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar\"\n"
                        + "  \"dependencies\":\n    - \"BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar\"\n",
                "- [[[[[[[[[[[[[[[[[\"BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar\"]]]]]]]]]]]]]]]]]\n");
        List<String> expectedMessages = List.of(
                "YAML anchors are forbidden",
                "Explicit YAML tags are forbidden",
                "YAML aliases are forbidden",
                "exactly one YAML document",
                "Unsupported Spring Boot layer",
                "Duplicate Spring Boot layer",
                "Invalid Spring Boot index YAML",
                "Invalid Spring Boot index YAML");

        for (int index = 0; index < invalidIndexes.size(); index++) {
            Path baseDir = Files.createDirectories(this.tempDir.resolve("invalid-layers-index-" + index));
            Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
            Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
            writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
            Path archivePath = buildDirectory.resolve("demo.jar");
            writeArchiveWithLayersIndex(archivePath,
                    invalidIndexes.get(index).getBytes(StandardCharsets.UTF_8));

            assertArchiveRejected(configuredMojo(baseDir, buildDirectory, classesDirectory), archivePath,
                    expectedMessages.get(index));
        }
    }

    @Test
    void boundsSpringBootIndexesByActualBytesAtLimitAndLimitPlusOne() throws Exception {
        CocoArchiveLimits defaults = CocoArchiveLimits.DEFAULT;
        assertThat(defaults.indexBytes()).isEqualTo(8L * 1024 * 1024);
        int injectedLimit = 1024;
        CocoArchiveLimits limits = withIndexBytes(defaults, injectedLimit);
        byte[] exact = new byte[injectedLimit];
        java.util.Arrays.fill(exact, (byte) 'a');
        Path exactArchive = this.tempDir.resolve("exact-index-limit.jar");
        writeArchiveWithClasspathIndex(exactArchive, exact);
        try (JarFile archive = new JarFile(exactArchive.toFile())) {
            assertThat(CocoPackagePruneMojo.readUtf8(
                    archive, archive.getJarEntry("BOOT-INF/classpath.idx"), limits)).hasSize(exact.length);
        }

        Path oversizedArchive = this.tempDir.resolve("oversized-index.jar");
        writeArchiveWithClasspathIndex(oversizedArchive, java.util.Arrays.copyOf(exact, exact.length + 1));
        try (JarFile archive = new JarFile(oversizedArchive.toFile())) {
            assertThatThrownBy(() -> CocoPackagePruneMojo.readUtf8(
                    archive, archive.getJarEntry("BOOT-INF/classpath.idx"), limits))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("exceed limit " + injectedLimit);
        }
    }

    @Test
    void sharesOneArchiveBudgetAcrossNestedParsingSecondHashAndRepeatedIndexReads() throws Exception {
        Path archivePath = this.tempDir.resolve("shared-archive-budget.jar");
        writeArchive(archivePath);
        byte[] nestedLibrary = readEntryBytes(
                archivePath, "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar");
        String sha256 = CocoArchiveIo.sha256Bounded(
                new java.io.ByteArrayInputStream(nestedLibrary), nestedLibrary.length, "test nested library");
        Set<CocoBootArchivePreflight.PrunableArtifact> resolved = Set.of(
                new CocoBootArchivePreflight.PrunableArtifact(
                        "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                        "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT", sha256, false));

        ArchiveReadMetrics withoutSecondHash = consumeArchiveReads(archivePath, Set.of(), Long.MAX_VALUE);
        ArchiveReadMetrics withSecondHash = consumeArchiveReads(archivePath, resolved, Long.MAX_VALUE);
        assertThat(withSecondHash.afterPreflight() - withoutSecondHash.afterPreflight())
                .isEqualTo(nestedLibrary.length);
        assertThat(withSecondHash.afterClasspath() - withSecondHash.afterPreflight())
                .isEqualTo(readEntryBytes(archivePath, "BOOT-INF/classpath.idx").length);
        assertThat(withSecondHash.afterLayers() - withSecondHash.afterClasspath())
                .isEqualTo(readEntryBytes(archivePath, "BOOT-INF/layers.idx").length);

        long exactBudget = withSecondHash.afterLayers();
        assertThat(consumeArchiveReads(archivePath, resolved, exactBudget).afterLayers())
                .isEqualTo(exactBudget);
        assertThatThrownBy(() -> consumeArchiveReads(archivePath, resolved, exactBudget - 1))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Archive cumulative read bytes");
    }

    @Test
    void enforcesOneArchiveBudgetAcrossRewritePostflightBackupAndPublication() throws Exception {
        ArchiveExecutionFixture measuredFixture = archiveExecutionFixture("end-to-end-budget-measured");
        long sourceArchiveBytes = Files.size(measuredFixture.archivePath());
        AtomicReference<CocoArchiveIo.CumulativeBudget> measuredBudget = new AtomicReference<>();
        set(measuredFixture.mojo(), "archiveReadBudgetFactory",
                (CocoPackagePruneMojo.ArchiveReadBudgetFactory) limit -> {
                    CocoArchiveIo.CumulativeBudget budget = new CocoArchiveIo.CumulativeBudget(
                            limit, "Archive cumulative read bytes");
                    measuredBudget.set(budget);
                    return budget;
                });
        measuredFixture.mojo().execute();
        long exactBudget = measuredBudget.get().consumed();
        assertThat(exactBudget).isGreaterThan(sourceArchiveBytes * 2);
        assertThat(measuredFixture.buildDirectory().resolve("coco-prune.original.jar")).isRegularFile();

        ArchiveExecutionFixture exactFixture = archiveExecutionFixture("end-to-end-budget-exact");
        set(exactFixture.mojo(), "archiveLimits",
                withArchiveReadBytes(CocoArchiveLimits.DEFAULT, exactBudget));
        exactFixture.mojo().execute();
        assertThat(exactFixture.buildDirectory().resolve("coco-prune.original.jar")).isRegularFile();

        ArchiveExecutionFixture limitedFixture = archiveExecutionFixture("end-to-end-budget-limit-minus-one");
        set(limitedFixture.mojo(), "archiveLimits",
                withArchiveReadBytes(CocoArchiveLimits.DEFAULT, exactBudget - 1));
        assertArchiveRejected(limitedFixture.mojo(), limitedFixture.archivePath(),
                "Archive cumulative read bytes");
    }

    @Test
    void enforcesOneArchiveBudgetAcrossPrefixedLayoutRelocationAndPublication() throws Exception {
        byte[] prefix = "#!/bin/sh\n# PK\\003\\004 budget decoy\n".getBytes(StandardCharsets.US_ASCII);
        ArchiveExecutionFixture measuredFixture = archiveExecutionFixture("prefixed-budget-measured");
        prependExecutablePrefix(measuredFixture.archivePath(), prefix);
        AtomicReference<CocoArchiveIo.CumulativeBudget> measuredBudget = new AtomicReference<>();
        set(measuredFixture.mojo(), "archiveReadBudgetFactory",
                (CocoPackagePruneMojo.ArchiveReadBudgetFactory) limit -> {
                    CocoArchiveIo.CumulativeBudget budget = new CocoArchiveIo.CumulativeBudget(
                            limit, "Archive cumulative read bytes");
                    measuredBudget.set(budget);
                    return budget;
                });
        measuredFixture.mojo().execute();
        long exactBudget = measuredBudget.get().consumed();
        assertThat(CocoExecutableArchive.readPrefix(measuredFixture.archivePath())).isEqualTo(prefix);
        assertThat(CocoExecutableArchive.readPrefix(
                measuredFixture.buildDirectory().resolve("coco-prune.original.jar"))).isEqualTo(prefix);

        ArchiveExecutionFixture exactFixture = archiveExecutionFixture("prefixed-budget-exact");
        prependExecutablePrefix(exactFixture.archivePath(), prefix);
        set(exactFixture.mojo(), "archiveLimits", withArchiveReadBytes(
                withExecutablePrefixBytes(CocoArchiveLimits.DEFAULT, prefix.length), exactBudget));
        exactFixture.mojo().execute();
        assertThat(CocoExecutableArchive.readPrefix(exactFixture.archivePath())).isEqualTo(prefix);

        ArchiveExecutionFixture limitedFixture = archiveExecutionFixture("prefixed-budget-limit-minus-one");
        prependExecutablePrefix(limitedFixture.archivePath(), prefix);
        set(limitedFixture.mojo(), "archiveLimits", withArchiveReadBytes(
                withExecutablePrefixBytes(CocoArchiveLimits.DEFAULT, prefix.length), exactBudget - 1));
        assertArchiveRejected(limitedFixture.mojo(), limitedFixture.archivePath(),
                "Archive cumulative read bytes");
    }

    @Test
    void enforcesManifestNestedArchiveLineAndLayerBudgetsAtBoundaries() throws Exception {
        CocoArchiveLimits defaults = CocoArchiveLimits.DEFAULT;
        Path archivePath = this.tempDir.resolve("aggregate-budget.jar");
        writeArchiveWithoutIndexes(archivePath);
        long manifestBytes = readEntryBytes(archivePath, JarFile.MANIFEST_NAME).length;
        long nestedArchiveBytes = nestedArchiveMetrics(archivePath,
                "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar").totalBytes()
                + nestedArchiveMetrics(archivePath,
                        "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar").totalBytes();
        CocoArchiveLimits exact = withManifestBytes(
                withNestedLimits(defaults, defaults.nestedEntryBytes(),
                        defaults.nestedLibraryBytes(), nestedArchiveBytes,
                        defaults.pomPropertiesBytes()), manifestBytes);

        inspectPreflight(archivePath, exact);
        assertPreflightRejected(archivePath, withManifestBytes(exact, manifestBytes - 1),
                "Executable JAR manifest");
        assertPreflightRejected(archivePath, withNestedLimits(exact, defaults.nestedEntryBytes(),
                defaults.nestedLibraryBytes(), nestedArchiveBytes - 1,
                defaults.pomPropertiesBytes()), "Nested archive uncompressed bytes");

        String classpath = "- \"BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar\"";
        int lineBytes = classpath.getBytes(StandardCharsets.UTF_8).length;
        CocoArchiveLimits exactLine = withParserLimits(defaults, lineBytes, defaults.layerCount());
        assertThat(CocoBootIndexParser.parse("BOOT-INF/classpath.idx", classpath, exactLine))
                .containsExactly("BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar");
        assertThatThrownBy(() -> CocoBootIndexParser.parse("BOOT-INF/classpath.idx", classpath,
                withParserLimits(defaults, lineBytes - 1, defaults.layerCount())))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("index line exceeds byte limit");

        String layers = "- \"dependencies\":\n"
                + "  - \"BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar\"\n"
                + "- \"application\":\n"
                + "  - \"BOOT-INF/classes/\"\n";
        assertThat(CocoBootIndexParser.parse("BOOT-INF/layers.idx", layers,
                withParserLimits(defaults, defaults.indexLineBytes(), 2)))
                .containsExactly("BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar");
        assertThatThrownBy(() -> CocoBootIndexParser.parse("BOOT-INF/layers.idx", layers,
                withParserLimits(defaults, defaults.indexLineBytes(), 1)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("exceeds layer limit 1");
    }

    @Test
    void usesDocumentedDefaultArchiveResourceLimits() {
        CocoArchiveLimits limits = CocoArchiveLimits.DEFAULT;
        assertThat(limits.outerEntryBytes()).isEqualTo(256L * 1024 * 1024);
        assertThat(limits.outerTotalBytes()).isEqualTo(2L * 1024 * 1024 * 1024);
        assertThat(limits.archiveReadBytes()).isEqualTo(8L * 1024 * 1024 * 1024);
        assertThat(limits.resolvedArtifactsBytes()).isEqualTo(2L * 1024 * 1024 * 1024);
        assertThat(limits.nestedEntryBytes()).isEqualTo(64L * 1024 * 1024);
        assertThat(limits.nestedLibraryBytes()).isEqualTo(512L * 1024 * 1024);
        assertThat(limits.nestedArchiveBytes()).isEqualTo(2L * 1024 * 1024 * 1024);
        assertThat(limits.pomPropertiesBytes()).isEqualTo(64L * 1024);
        assertThat(limits.manifestBytes()).isEqualTo(1024L * 1024);
        assertThat(limits.indexBytes()).isEqualTo(8L * 1024 * 1024);
        assertThat(limits.indexLineBytes()).isEqualTo(4 * 1024);
        assertThat(limits.layerCount()).isEqualTo(64);
        assertThat(limits.entryCount()).isEqualTo(65_536);
        assertThat(limits.entryNameBytes()).isEqualTo(1024);
        assertThat(limits.gavValueBytes()).isEqualTo(512);
        assertThat(limits.yamlNestingDepth()).isEqualTo(16);
        assertThat(limits.executablePrefixBytes()).isEqualTo(16L * 1024 * 1024);
    }

    @Test
    void preservesExecutablePrefixAtLimitAndRejectsLimitPlusOneWithoutMutation() throws Exception {
        byte[] prefix = "#!/bin/sh\n# PK\\003\\004 decoy\n".getBytes(StandardCharsets.US_ASCII);
        Path exactBase = Files.createDirectories(this.tempDir.resolve("prefix-exact"));
        Path exactBuild = Files.createDirectories(exactBase.resolve("target"));
        Path exactClasses = Files.createDirectories(exactBuild.resolve("classes"));
        writeManifest(exactClasses, Set.of(CocoFeature.WEB));
        Path exactArchive = exactBuild.resolve("demo.jar");
        writeArchiveWithoutIndexes(exactArchive);
        prependExecutablePrefix(exactArchive, prefix);
        Set<PosixFilePermission> exactPermissions = null;
        if (Files.getFileAttributeView(exactArchive, PosixFileAttributeView.class) != null) {
            exactPermissions = Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE);
            Files.setPosixFilePermissions(exactArchive, exactPermissions);
        }
        byte[] exactOriginal = Files.readAllBytes(exactArchive);
        CocoPackagePruneMojo exactMojo = configuredMojo(exactBase, exactBuild, exactClasses);
        set(exactMojo, "archiveLimits",
                withExecutablePrefixBytes(CocoArchiveLimits.DEFAULT, prefix.length));

        exactMojo.execute();

        assertThat(CocoExecutableArchive.readPrefix(exactArchive)).isEqualTo(prefix);
        assertThat(entries(exactArchive))
                .contains("BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar")
                .doesNotContain("BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar");
        Path exactBackup = exactBuild.resolve("coco-prune.original.jar");
        assertThat(Files.readAllBytes(exactBackup)).isEqualTo(exactOriginal);
        assertThat(CocoExecutableArchive.readPrefix(exactBackup)).isEqualTo(prefix);
        if (exactPermissions != null) {
            assertThat(Files.getPosixFilePermissions(exactArchive))
                    .containsExactlyInAnyOrderElementsOf(exactPermissions);
            assertThat(Files.getPosixFilePermissions(exactBackup))
                    .containsExactlyInAnyOrderElementsOf(exactPermissions);
        }

        Path oversizedBase = Files.createDirectories(this.tempDir.resolve("prefix-limit-plus-one"));
        Path oversizedBuild = Files.createDirectories(oversizedBase.resolve("target"));
        Path oversizedClasses = Files.createDirectories(oversizedBuild.resolve("classes"));
        writeManifest(oversizedClasses, Set.of(CocoFeature.WEB));
        Path oversizedArchive = oversizedBuild.resolve("demo.jar");
        writeArchiveWithoutIndexes(oversizedArchive);
        byte[] oversizedPrefix = java.util.Arrays.copyOf(prefix, prefix.length + 1);
        oversizedPrefix[oversizedPrefix.length - 1] = '#';
        prependExecutablePrefix(oversizedArchive, oversizedPrefix);
        byte[] oversizedOriginal = Files.readAllBytes(oversizedArchive);
        CocoPackagePruneMojo oversizedMojo = configuredMojo(
                oversizedBase, oversizedBuild, oversizedClasses);
        set(oversizedMojo, "archiveLimits",
                withExecutablePrefixBytes(CocoArchiveLimits.DEFAULT, prefix.length));

        assertArchiveRejected(oversizedMojo, oversizedArchive,
                "Executable archive prefix exceeds byte limit " + prefix.length);
        assertThat(Files.readAllBytes(oversizedArchive)).isEqualTo(oversizedOriginal);
        assertThat(oversizedBuild.resolve("coco-prune.original.jar")).doesNotExist();
    }

    @Test
    void prunesPrefixedZip64BootArchiveThroughExecuteAndPreservesZip64Backup() throws Exception {
        byte[] prefix = "#!/bin/sh\n# PK\\003\\004 ZIP64 decoy\n".getBytes(StandardCharsets.US_ASCII);
        Path baseDir = Files.createDirectories(this.tempDir.resolve("zip64-boot-execute"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeZip64BootArchive(archivePath);
        prependExecutablePrefix(archivePath, prefix);
        byte[] original = Files.readAllBytes(archivePath);

        configuredMojo(baseDir, buildDirectory, classesDirectory).execute();

        Path backupPath = buildDirectory.resolve("coco-prune.original.jar");
        assertThat(Files.readAllBytes(backupPath)).isEqualTo(original);
        assertThat(CocoExecutableArchive.readPrefix(archivePath)).isEqualTo(prefix);
        assertThat(CocoExecutableArchive.readPrefix(backupPath)).isEqualTo(prefix);
        try (JarFile rewritten = new JarFile(archivePath.toFile());
                JarFile backup = new JarFile(backupPath.toFile())) {
            assertThat(rewritten.size()).isEqualTo(65_535);
            assertThat(rewritten.getEntry("BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar")).isNull();
            assertThat(rewritten.getEntry("BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar")).isNotNull();
            assertThat(backup.size()).isEqualTo(65_536);
            assertThat(backup.getEntry("BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar")).isNotNull();
        }
    }

    @Test
    void enforcesOuterEntryTotalAndCountBudgetsAtActualByteBoundaries() throws Exception {
        Path archivePath = this.tempDir.resolve("outer-budget.jar");
        writeArchiveWithoutIndexes(archivePath);
        ArchiveMetrics metrics = outerArchiveMetrics(archivePath);
        int maximumNameBytes = Math.max(metrics.maximumNameBytes(), Math.max(
                nestedArchiveMetrics(archivePath,
                        "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar").maximumNameBytes(),
                nestedArchiveMetrics(archivePath,
                        "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar").maximumNameBytes()));
        CocoArchiveLimits defaults = CocoArchiveLimits.DEFAULT;
        CocoArchiveLimits exact = withOuterLimits(defaults,
                metrics.maximumEntryBytes(), metrics.totalBytes(), metrics.entryCount(), maximumNameBytes);

        inspectPreflight(archivePath, exact);
        assertPreflightRejected(archivePath, withOuterLimits(defaults,
                metrics.maximumEntryBytes() - 1, metrics.totalBytes(), metrics.entryCount(),
                maximumNameBytes),
                "Outer ZIP entry");
        assertPreflightRejected(archivePath, withOuterLimits(defaults,
                metrics.maximumEntryBytes(), metrics.totalBytes() - 1, metrics.entryCount(),
                maximumNameBytes),
                "Outer archive uncompressed bytes");
        assertPreflightRejected(archivePath, withOuterLimits(defaults,
                metrics.maximumEntryBytes(), metrics.totalBytes(), metrics.entryCount() - 1,
                maximumNameBytes),
                "Outer archive exceeds ZIP entry count limit");
        assertPreflightRejected(archivePath, withOuterLimits(defaults,
                metrics.maximumEntryBytes(), metrics.totalBytes(), metrics.entryCount(),
                maximumNameBytes - 1),
                "ZIP entry name exceeds byte limit");
        assertThat(defaults.entryCount()).isEqualTo(65_536);
    }

    @Test
    void enforcesNestedEntryTotalPomAndCountBudgetsAtActualByteBoundaries() throws Exception {
        Map<String, byte[]> extraEntries = new java.util.LinkedHashMap<>();
        for (int index = 0; index < 6; index++) {
            extraEntries.put("data/entry-" + index + ".bin", new byte[100 + index]);
        }
        Path archivePath = this.tempDir.resolve("nested-budget.jar");
        writeArchiveWithRawPomAndNestedEntries(archivePath, validPomProperties(), extraEntries);
        ArchiveMetrics outer = outerArchiveMetrics(archivePath);
        ArchiveMetrics nested = nestedArchiveMetrics(archivePath,
                "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar");
        CocoArchiveLimits defaults = CocoArchiveLimits.DEFAULT;
        CocoArchiveLimits exact = withNestedLimits(withOuterLimits(defaults,
                outer.maximumEntryBytes(), outer.totalBytes(), nested.entryCount(), defaults.entryNameBytes()),
                nested.maximumEntryBytes(), nested.totalBytes(), defaults.nestedArchiveBytes(),
                defaults.pomPropertiesBytes());

        inspectPreflight(archivePath, exact);
        assertPreflightRejected(archivePath, withNestedLimits(exact,
                nested.maximumEntryBytes() - 1, nested.totalBytes(), defaults.nestedArchiveBytes(),
                defaults.pomPropertiesBytes()),
                "Nested ZIP entry");
        assertPreflightRejected(archivePath, withNestedLimits(exact,
                nested.maximumEntryBytes(), nested.totalBytes() - 1, defaults.nestedArchiveBytes(),
                defaults.pomPropertiesBytes()),
                "uncompressed bytes exceed limit");
        assertPreflightRejected(archivePath, withOuterLimits(exact,
                outer.maximumEntryBytes(), outer.totalBytes(), nested.entryCount() - 1,
                defaults.entryNameBytes()),
                "Nested library");

        String exactPom = paddedPomProperties((int) defaults.pomPropertiesBytes());
        Path exactPomArchive = this.tempDir.resolve("exact-pom-limit.jar");
        writeArchiveWithRawPomAndNestedEntries(exactPomArchive, exactPom, Map.of());
        inspectPreflight(exactPomArchive, defaults);
        Path oversizedPomArchive = this.tempDir.resolve("oversized-pom.jar");
        writeArchiveWithRawPomAndNestedEntries(oversizedPomArchive, exactPom + "a", Map.of());
        assertPreflightRejected(oversizedPomArchive, defaults,
                "Maven pom.properties");
    }

    @Test
    void rejectsCaseFoldNfcAndFileDirectoryCollisionsInOuterAndNestedArchives() throws Exception {
        List<List<String>> collisions = List.of(
                List.of("data/Case.txt", "data/case.txt"),
                List.of("data/caf\u00e9.txt", "data/cafe\u0301.txt"),
                List.of("data/item", "data/item/child.txt"));
        List<String> expectedMessages = List.of("Case-folded ZIP entry collision", "Non-NFC ZIP entry name",
                "ZIP file/directory conflict");
        for (int index = 0; index < collisions.size(); index++) {
            Path outerArchive = this.tempDir.resolve("outer-name-collision-" + index + ".jar");
            writeArchiveWithOuterEntries(outerArchive, collisions.get(index));
            assertPreflightRejected(outerArchive, CocoArchiveLimits.DEFAULT,
                    expectedMessages.get(index));

            Path nestedArchive = this.tempDir.resolve("nested-name-collision-" + index + ".jar");
            Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
            collisions.get(index).forEach(name -> entries.put(name, new byte[] {1}));
            writeArchiveWithRawPomAndNestedEntries(nestedArchive, validPomProperties(), entries);
            assertPreflightRejected(nestedArchive, CocoArchiveLimits.DEFAULT,
                    expectedMessages.get(index));
        }
    }

    @Test
    void rejectsUnexpectedDuplicateOversizedAndControlledGavValues() throws Exception {
        List<String> invalidPomProperties = List.of(
                validPomProperties() + "classifier=tests\n",
                validPomProperties() + "version=2.0.0\n",
                "groupId=io.github.patton174\nartifactId=" + "a".repeat(513) + "\nversion=1.0.0\n",
                "groupId=io.github.patton174\nartifactId=coco\u0001web\nversion=1.0.0\n");
        List<String> expectedMessages = List.of(
                "Unexpected Maven GAV key", "Duplicate Maven GAV key",
                "Invalid Maven GAV value", "Invalid Maven GAV value");
        for (int index = 0; index < invalidPomProperties.size(); index++) {
            Path archivePath = this.tempDir.resolve("invalid-gav-" + index + ".jar");
            writeArchiveWithRawPomAndNestedEntries(archivePath, invalidPomProperties.get(index), Map.of());
            assertPreflightRejected(archivePath, CocoArchiveLimits.DEFAULT,
                    expectedMessages.get(index));
        }
    }

    @Test
    void keepsPrimaryPostflightFailureAndSuppressesCleanupFailure() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("cleanup-suppressed"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeArchive(archivePath);
        byte[] original = Files.readAllBytes(archivePath);
        Path backup = buildDirectory.resolve("coco-prune.original.jar");
        byte[] existingBackup = "existing-backup".getBytes(StandardCharsets.UTF_8);
        Files.write(backup, existingBackup);
        Path temporaryPath = Files.createDirectories(buildDirectory.resolve("nonempty.tmp"));
        Files.writeString(temporaryPath.resolve("retained.txt"), "retained", StandardCharsets.UTF_8);
        CocoPackagePruneMojo mojo = configuredMojo(baseDir, buildDirectory, classesDirectory);
        CocoPackagePruneMojo.BootArchiveView sourceView = new CocoPackagePruneMojo.BootArchiveView(
                List.of("BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar"), Set.of());

        assertThatThrownBy(() -> mojo.publishRewrittenArchive(
                archivePath, temporaryPath, sourceView, Set.of()))
                .isInstanceOf(IOException.class)
                .satisfies(failure -> assertThat(failure.getSuppressed())
                        .anyMatch(IOException.class::isInstance));
        assertThat(Files.readAllBytes(archivePath)).isEqualTo(original);
        assertThat(Files.readAllBytes(backup)).isEqualTo(existingBackup);
        assertThat(temporaryPath).isDirectory();
    }

    @Test
    void rejectsResolvedArtifactsThatMapToTheSameNestedLibraryName() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("duplicate-resolver-entry"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeArchiveWithoutIndexes(archivePath);
        MavenProject project = project(baseDir, buildDirectory, classesDirectory);
        project.setArtifacts(new LinkedHashSet<>(List.of(
                resolvedArtifact("com.example.one", "shared-library", "1.0.0"),
                resolvedArtifact("com.example.two", "shared-library", "1.0.0"))));
        CocoPackagePruneMojo mojo = newMojo();
        set(mojo, "project", project);
        set(mojo, "classesDirectory", classesDirectory.toFile());
        set(mojo, "buildDirectory", buildDirectory.toFile());
        set(mojo, "finalName", "demo");

        assertArchiveRejected(mojo, archivePath,
                "Multiple resolved Maven artifacts map to nested library 'BOOT-INF/lib/shared-library-1.0.0.jar'");
    }

    @Test
    void keepsDirectMybatisAndFreemarkerDependenciesInSpringBootArchive() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("direct-third-party-archive"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.MYBATIS_PLUS, CocoFeature.CODEGEN));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeMybatisArchive(archivePath);
        MavenProject project = project(baseDir, buildDirectory, classesDirectory);
        project.getModel().addDependency(dependency("org.mybatis", "mybatis", "3.5.19"));
        project.getModel().addDependency(dependency("org.freemarker", "freemarker", "2.3.34"));
        project.setArtifacts(mybatisArchiveArtifacts());
        List<org.eclipse.aether.graph.Dependency> survivingDependencies = List.of(
                resolvedDependency("org.mybatis", "mybatis", "3.5.19"),
                resolvedDependency("org.freemarker", "freemarker", "2.3.34"));

        CocoPackagePruneMojo mojo = newMojo();
        set(mojo, "project", project);
        set(mojo, "projectDependenciesResolver",
                projectDependenciesResolverReturning(request -> survivingDependencies));
        set(mojo, "classesDirectory", classesDirectory.toFile());
        set(mojo, "buildDirectory", buildDirectory.toFile());
        set(mojo, "finalName", "demo");

        mojo.execute();

        assertThat(entries(archivePath))
                .contains(
                        "BOOT-INF/lib/mybatis-3.5.19.jar",
                        "BOOT-INF/lib/freemarker-2.3.34.jar")
                .doesNotContain(
                        "BOOT-INF/lib/coco-mybatis-plus-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-feature-mybatis-plus-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/mybatis-extra-1.0.0.jar",
                        "BOOT-INF/lib/mybatis-plus-core-3.5.16.jar",
                        "BOOT-INF/lib/mybatis-plus-spring-3.5.16.jar",
                        "BOOT-INF/lib/mybatis-spring-3.0.5.jar",
                        "BOOT-INF/lib/spring-jdbc-7.0.0.jar");
        assertThat(readEntry(archivePath, "BOOT-INF/classpath.idx"))
                .contains("mybatis-3.5.19", "freemarker-2.3.34")
                .doesNotContain("coco-mybatis-plus", "mybatis-extra", "mybatis-plus-core",
                        "mybatis-spring", "spring-jdbc");
        assertThat(readEntry(archivePath, "BOOT-INF/layers.idx"))
                .contains("mybatis-3.5.19", "freemarker-2.3.34")
                .doesNotContain("coco-mybatis-plus", "mybatis-extra", "mybatis-plus-core",
                        "mybatis-spring", "spring-jdbc");
        assertRunnableSpringBootArchive(archivePath);
    }

    @Test
    void resolvesCurrentClosureWhenMavenProjectArtifactsAreEmpty() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("empty-project-artifacts"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.MYBATIS_PLUS));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeMybatisArchive(archivePath);

        MavenProject project = project(baseDir, buildDirectory, classesDirectory);
        project.getModel().addDependency(
                dependency("io.github.patton174", "coco-spring-boot-starter", "1.0.0-SNAPSHOT"));
        Set<org.apache.maven.artifact.Artifact> originalArtifacts = mybatisArchiveArtifacts();
        List<org.eclipse.aether.graph.Dependency> originalDependencies = resolvedDependencies(originalArtifacts);
        List<org.eclipse.aether.graph.Dependency> survivingDependencies = List.of(
                resolvedDependency("io.github.patton174", "coco-web", "1.0.0-SNAPSHOT"),
                resolvedDependency("io.github.patton174", "coco-audit", "1.0.0-SNAPSHOT"));
        AtomicInteger resolutions = new AtomicInteger();

        CocoPackagePruneMojo mojo = newMojo();
        set(mojo, "project", project);
        set(mojo, "projectDependenciesResolver", projectDependenciesResolverReturning(
                request -> resolutions.getAndIncrement() == 0 ? originalDependencies : survivingDependencies));
        set(mojo, "classesDirectory", classesDirectory.toFile());
        set(mojo, "buildDirectory", buildDirectory.toFile());
        set(mojo, "finalName", "demo");

        mojo.execute();

        assertThat(resolutions).hasValue(2);
        assertThat(project.getArtifacts()).isEmpty();
        assertThat(entries(archivePath))
                .contains(
                        "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar")
                .doesNotContain(
                        "BOOT-INF/lib/coco-mybatis-plus-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/mybatis-3.5.19.jar",
                        "BOOT-INF/lib/mybatis-plus-core-3.5.16.jar",
                        "BOOT-INF/lib/freemarker-2.3.34.jar");
    }

    @Test
    void rejectsManifestPruneIdsOutsideStandardCocoDefinitions() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("existing-unsafe-manifest"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeExistingUnsafeMybatisManifest(classesDirectory);
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeMybatisArchive(archivePath);

        CocoPackagePruneMojo mojo = newMojo();
        set(mojo, "project", project(baseDir, buildDirectory, classesDirectory));
        set(mojo, "classesDirectory", classesDirectory.toFile());
        set(mojo, "buildDirectory", buildDirectory.toFile());
        set(mojo, "finalName", "demo");

        byte[] original = Files.readAllBytes(archivePath);
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasStackTraceContaining("pruneArtifactIds")
                .hasStackTraceContaining("mybatis-plus-core");
        assertThat(Files.readAllBytes(archivePath)).isEqualTo(original);
    }

    @Test
    void keepsThirdPartyArtifactWhenItsArtifactIdMatchesCocoArtifact() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("third-party-coco-artifact-id"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeThirdPartyCocoNamedArchive(archivePath);

        CocoPackagePruneMojo mojo = newMojo();
        set(mojo, "project", project(baseDir, buildDirectory, classesDirectory));
        set(mojo, "classesDirectory", classesDirectory.toFile());
        set(mojo, "buildDirectory", buildDirectory.toFile());
        set(mojo, "finalName", "demo");

        mojo.execute();

        assertThat(entries(archivePath)).contains(
                "BOOT-INF/lib/coco-web-9.0.0.jar",
                "BOOT-INF/lib/coco-feature-web-9.0.0.jar");
        assertThat(readEntry(archivePath, "BOOT-INF/classpath.idx"))
                .contains("coco-web-9.0.0.jar", "coco-feature-web-9.0.0.jar");
        assertThat(readEntry(archivePath, "BOOT-INF/layers.idx"))
                .contains("coco-web-9.0.0.jar", "coco-feature-web-9.0.0.jar");
    }

    @Test
    void keepsExplicitOptionalExtensionArtifactsWhenAStandardFeatureIsDisabled() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("optional-extensions"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeOptionalExtensionArchive(archivePath);

        configuredMojo(baseDir, buildDirectory, classesDirectory).execute();

        assertThat(entries(archivePath)).contains(
                "BOOT-INF/lib/coco-audit-jdbc-1.0.0-SNAPSHOT.jar",
                "BOOT-INF/lib/coco-replay-redis-1.0.0-SNAPSHOT.jar",
                "BOOT-INF/lib/coco-rate-limit-1.0.0-SNAPSHOT.jar",
                "BOOT-INF/lib/coco-observability-1.0.0-SNAPSHOT.jar")
                .doesNotContain("BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar");
        assertThat(readEntry(archivePath, "BOOT-INF/classpath.idx"))
                .contains("coco-audit-jdbc", "coco-replay-redis", "coco-rate-limit", "coco-observability")
                .doesNotContain("coco-web-1.0.0-SNAPSHOT.jar");
        assertThat(readEntry(archivePath, "BOOT-INF/layers.idx"))
                .contains("coco-audit-jdbc", "coco-replay-redis", "coco-rate-limit", "coco-observability")
                .doesNotContain("coco-web-1.0.0-SNAPSHOT.jar");
    }

    @Test
    void rewritesStoredSpringBootIndexesWhenContentChanges() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("stored-index"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.MYBATIS_PLUS));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeArchiveWithStoredIndexes(archivePath);

        CocoPackagePruneMojo mojo = newMojo();
        set(mojo, "project", project(baseDir, buildDirectory, classesDirectory));
        set(mojo, "classesDirectory", classesDirectory.toFile());
        set(mojo, "buildDirectory", buildDirectory.toFile());
        set(mojo, "finalName", "demo");

        mojo.execute();

        assertThat(readEntry(archivePath, "BOOT-INF/classpath.idx"))
                .contains("mybatis-plus-core", "mybatis-plus-extension", "mybatis-RELEASE.jar",
                        "mybatis-v1.jar", "mybatis-extra-1.0.0.jar")
                .doesNotContain("coco-mybatis-plus", "coco-feature-mybatis-plus");
        assertThat(entries(archivePath))
                .doesNotContain(
                        "BOOT-INF/lib/coco-mybatis-plus-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-feature-mybatis-plus-1.0.0-SNAPSHOT.jar")
                .contains(
                        "BOOT-INF/lib/mybatis-plus-core-RELEASE.jar",
                        "BOOT-INF/lib/mybatis-plus-extension-v1.jar",
                        "BOOT-INF/lib/mybatis-RELEASE.jar",
                        "BOOT-INF/lib/mybatis-v1.jar",
                        "BOOT-INF/lib/spring-boot-4.1.0.jar",
                        "BOOT-INF/lib/mybatis-extra-1.0.0.jar");
        assertThat(entryMethod(archivePath, "BOOT-INF/lib/spring-boot-4.1.0.jar"))
                .isEqualTo(ZipEntry.STORED);
        assertRunnableSpringBootArchive(archivePath);
    }

    @Test
    void augmentsLegacyManifestAliasesWithCurrentCanonicalArtifactIds() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("legacy-manifest"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeLegacyDisabledWebManifest(classesDirectory);
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeLegacyManifestArchive(archivePath);

        CocoPackagePruneMojo mojo = newMojo();
        set(mojo, "project", project(baseDir, buildDirectory, classesDirectory));
        set(mojo, "classesDirectory", classesDirectory.toFile());
        set(mojo, "buildDirectory", buildDirectory.toFile());
        set(mojo, "finalName", "demo");

        mojo.execute();

        assertThat(entries(archivePath))
                .contains("BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar")
                .doesNotContain(
                        "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-feature-web-1.0.0-SNAPSHOT.jar");
        assertThat(readEntry(archivePath, "BOOT-INF/classpath.idx"))
                .contains("coco-audit")
                .doesNotContain("coco-web", "coco-feature-web");
        assertThat(readEntry(archivePath, "BOOT-INF/layers.idx"))
                .contains("coco-audit")
                .doesNotContain("coco-web", "coco-feature-web");
    }

    @Test
    void rejectsSignedOuterArchiveBeforeCreatingTemporaryFile() throws Exception {
        java.util.List<String> signatureEntries = java.util.List.of(
                "META-INF/COCO.SF", "META-INF/COCO.RSA", "META-INF/COCO.DSA",
                "META-INF/COCO.EC", "META-INF/SIG-COCO");
        for (int index = 0; index < signatureEntries.size(); index++) {
            Path baseDir = Files.createDirectories(this.tempDir.resolve("signed-archive-" + index));
            Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
            Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
            writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
            Path archivePath = buildDirectory.resolve("demo.jar");
            String signatureEntry = signatureEntries.get(index);
            writeSignedArchive(archivePath, signatureEntry);

            byte[] original = Files.readAllBytes(archivePath);
            CocoPackagePruneMojo mojo = configuredMojo(baseDir, buildDirectory, classesDirectory);

            assertThatThrownBy(mojo::execute)
                    .isInstanceOf(MojoExecutionException.class)
                    .hasRootCauseMessage("Refusing to rewrite signed archive containing '"
                            + signatureEntry + "'.");
            assertThat(Files.readAllBytes(archivePath)).isEqualTo(original);
            assertThat(buildDirectory.resolve("coco-prune.original.jar")).doesNotExist();
            try (var files = Files.list(buildDirectory)) {
                assertThat(files.map(path -> path.getFileName().toString()))
                        .noneMatch(name -> name.startsWith("demo.jar") && name.endsWith(".tmp"));
            }
        }
    }

    @Test
    void rejectsNonExecutableSpringBootStructuresWithoutRewriting() throws Exception {
        for (InvalidBootArchive invalid : InvalidBootArchive.values()) {
            Path baseDir = Files.createDirectories(this.tempDir.resolve("invalid-boot-" + invalid.name()));
            Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
            Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
            writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
            Path archivePath = buildDirectory.resolve("demo.jar");
            writeInvalidBootArchive(archivePath, invalid);

            assertArchiveRejected(configuredMojo(baseDir, buildDirectory, classesDirectory), archivePath,
                    "not an executable Spring Boot JAR");
        }
    }

    @Test
    void rejectsUnsafeOuterZipEntryNamesWithoutRewriting() throws Exception {
        java.util.List<String> unsafeNames = java.util.List.of(
                "/absolute.txt", "C:/absolute.txt", "../escape.txt",
                "BOOT-INF/../escape.txt", "BOOT-INF\\classes\\escape.txt");
        for (int index = 0; index < unsafeNames.size(); index++) {
            Path baseDir = Files.createDirectories(this.tempDir.resolve("unsafe-entry-" + index));
            Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
            Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
            writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
            Path archivePath = buildDirectory.resolve("demo.jar");
            writeArchiveWithExtraEntry(archivePath, unsafeNames.get(index));

            assertArchiveRejected(configuredMojo(baseDir, buildDirectory, classesDirectory), archivePath,
                    "Unsafe ZIP entry name");
        }
    }

    @Test
    void rejectsDuplicateOuterZipEntriesInsteadOfRemovingEverySameNamedEntry() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("duplicate-entry"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeDuplicateEntryArchive(archivePath);

        assertArchiveRejected(configuredMojo(baseDir, buildDirectory, classesDirectory), archivePath,
                "Duplicate ZIP entry");
    }

    @Test
    void rejectsNestedCocoVersionThatDisagreesWithExpectedVersion() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("wrong-version"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeLegacyManifestArchive(archivePath);
        CocoPackagePruneMojo mojo = configuredMojo(baseDir, buildDirectory, classesDirectory);
        set(mojo, "featureVersion", "2.0.2");

        assertArchiveRejected(mojo, archivePath, "does not match expected Coco version 2.0.2");
    }

    @Test
    void rejectsPruneCandidateWithoutCompleteMavenGav() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("incomplete-gav"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeIncompleteGavArchive(archivePath);

        assertArchiveRejected(configuredMojo(baseDir, buildDirectory, classesDirectory), archivePath,
                "Incomplete Maven GAV");
    }

    @Test
    void rejectsNestedCocoGavThatDoesNotMatchBootLibraryName() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("mismatched-gav-name"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        Path archivePath = buildDirectory.resolve("demo.jar");
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-tenant", "1.0.0-SNAPSHOT");
        }

        assertArchiveRejected(configuredMojo(baseDir, buildDirectory, classesDirectory), archivePath,
                "does not match Boot library entry");
    }

    @Test
    void rejectsEnabledCocoLibraryWithoutMavenGavDuringStandalonePrune() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("enabled-without-gav"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.AUDIT));
        Path archivePath = buildDirectory.resolve("demo.jar");
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            add(outputStream, "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar", "not-a-nested-jar");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-audit", "1.0.0-SNAPSHOT");
        }

        assertArchiveRejected(configuredMojo(baseDir, buildDirectory, classesDirectory), archivePath,
                "Cannot verify Maven GAV for Coco-named nested library");
    }

    private void writeManifest(Path classesDirectory, Set<CocoFeature> disabledFeatures) throws Exception {
        Path manifestPath = classesDirectory.resolve(CocoFeatureManifestLoader.MANIFEST_LOCATION);
        Files.createDirectories(manifestPath.getParent());
        var plan = StandardCocoFeatures.resolve(CocoFeatureSelection.ofDisabled(disabledFeatures));
        Files.writeString(manifestPath,
                CocoFeatureManifestLoader.write(StandardCocoFeatures.toManifest(plan, "test")),
                StandardCharsets.UTF_8);
    }

    private void writeLegacyDisabledWebManifest(Path classesDirectory) throws Exception {
        Path manifestPath = classesDirectory.resolve(CocoFeatureManifestLoader.MANIFEST_LOCATION);
        Files.createDirectories(manifestPath.getParent());
        Files.writeString(manifestPath, """
                {
                  "schemaVersion" : "1.0",
                  "generatedBy" : "legacy-test",
                  "features" : [ {
                    "id" : "web",
                    "artifactId" : "coco-feature-web",
                    "autoConfigurationClassName" : "io.github.coco.feature.web.CocoWebAutoConfiguration",
                    "defaultEnabled" : true,
                    "enabled" : false,
                    "dependencies" : [ ]
                  } ]
                }
                """, StandardCharsets.UTF_8);
    }

    private void writeExistingUnsafeMybatisManifest(Path classesDirectory) throws Exception {
        Path manifestPath = classesDirectory.resolve(CocoFeatureManifestLoader.MANIFEST_LOCATION);
        Files.createDirectories(manifestPath.getParent());
        Files.writeString(manifestPath, """
                {
                  "schemaVersion" : "1.1",
                  "generatedBy" : "existing-plugin",
                  "features" : [ {
                    "id" : "mybatis-plus",
                    "artifactId" : "coco-mybatis-plus",
                    "autoConfigurationClassName" : "io.github.coco.feature.mybatisplus.CocoMybatisPlusAutoConfiguration",
                    "defaultEnabled" : true,
                    "enabled" : false,
                    "dependencies" : [ ],
                    "pruneArtifactIds" : [
                      "coco-mybatis-plus",
                      "coco-feature-mybatis-plus",
                      "mybatis",
                      "mybatis-plus-core",
                      "mybatis-spring",
                      "freemarker"
                    ]
                  } ]
                }
                """, StandardCharsets.UTF_8);
    }

    private void writeLegacyManifestArchive(Path archivePath) throws Exception {
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            add(outputStream, "BOOT-INF/classpath.idx", """
                    - "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-feature-web-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar"
                    """);
            add(outputStream, "BOOT-INF/layers.idx", """
                    - "dependencies":
                      - "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-feature-web-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar"
                    """);
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-feature-web-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-feature-web", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-audit", "1.0.0-SNAPSHOT");
        }
    }

    private void writeThirdPartyCocoNamedArchive(Path archivePath) throws Exception {
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            add(outputStream, "BOOT-INF/classpath.idx", """
                    - "BOOT-INF/lib/coco-web-9.0.0.jar"
                    - "BOOT-INF/lib/coco-feature-web-9.0.0.jar"
                    """);
            add(outputStream, "BOOT-INF/layers.idx", """
                    - "dependencies":
                      - "BOOT-INF/lib/coco-web-9.0.0.jar"
                      - "BOOT-INF/lib/coco-feature-web-9.0.0.jar"
                    """);
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-web-9.0.0.jar",
                    "com.example", "coco-web", "9.0.0");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-feature-web-9.0.0.jar",
                    "com.example", "coco-feature-web", "9.0.0");
        }
    }

    private void writeOptionalExtensionArchive(Path archivePath) throws Exception {
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            add(outputStream, "BOOT-INF/classpath.idx", """
                    - "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-audit-jdbc-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-replay-redis-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-rate-limit-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-observability-1.0.0-SNAPSHOT.jar"
                    """);
            add(outputStream, "BOOT-INF/layers.idx", """
                    - "dependencies":
                      - "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-audit-jdbc-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-replay-redis-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-rate-limit-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-observability-1.0.0-SNAPSHOT.jar"
                    """);
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-audit-jdbc-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-audit-jdbc", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-replay-redis-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-replay-redis", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-rate-limit-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-rate-limit", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-observability-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-observability", "1.0.0-SNAPSHOT");
        }
    }

    private void writeArchive(Path archivePath) throws Exception {
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            add(outputStream, "BOOT-INF/classpath.idx", """
                    - "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-tenant-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-feature-tenant-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-data-permission-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-feature-data-permission-1.0.0-SNAPSHOT.jar"
                    """);
            add(outputStream, "BOOT-INF/layers.idx", """
                    - "dependencies":
                      - "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-tenant-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-feature-tenant-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-data-permission-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-feature-data-permission-1.0.0-SNAPSHOT.jar"
                    """);
            add(outputStream, "BOOT-INF/classes/application.yml", "spring.application.name=demo");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-audit", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-tenant-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-tenant", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-feature-tenant-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-feature-tenant", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-data-permission-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-data-permission", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-feature-data-permission-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-feature-data-permission", "1.0.0-SNAPSHOT");
        }
    }

    private void writeArchiveWithoutIndexes(Path archivePath) throws Exception {
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-audit", "1.0.0-SNAPSHOT");
        }
    }

    private void writeZip64BootArchive(Path archivePath) throws Exception {
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-audit", "1.0.0-SNAPSHOT");
            for (int index = 0; index < 65_531; index++) {
                add(outputStream, "BOOT-INF/classes/filler/" + index, "");
            }
        }
    }

    private static void prependExecutablePrefix(Path archivePath, byte[] prefix) throws Exception {
        byte[] archive = Files.readAllBytes(archivePath);
        byte[] prefixed = java.util.Arrays.copyOf(prefix, prefix.length + archive.length);
        System.arraycopy(archive, 0, prefixed, prefix.length, archive.length);
        Files.write(archivePath, prefixed);
        CocoExecutableArchive.relocateOffsets(archivePath, prefix.length);
    }

    private static BasicFileAttributes symbolicLinkAttributes(BasicFileAttributes delegate) {
        return new BasicFileAttributes() {
            @Override
            public java.nio.file.attribute.FileTime lastModifiedTime() {
                return delegate.lastModifiedTime();
            }

            @Override
            public java.nio.file.attribute.FileTime lastAccessTime() {
                return delegate.lastAccessTime();
            }

            @Override
            public java.nio.file.attribute.FileTime creationTime() {
                return delegate.creationTime();
            }

            @Override
            public boolean isRegularFile() {
                return false;
            }

            @Override
            public boolean isDirectory() {
                return false;
            }

            @Override
            public boolean isSymbolicLink() {
                return true;
            }

            @Override
            public boolean isOther() {
                return false;
            }

            @Override
            public long size() {
                return delegate.size();
            }

            @Override
            public Object fileKey() {
                return delegate.fileKey();
            }
        };
    }

    private static BasicFileAttributes otherAttributes(BasicFileAttributes delegate) {
        return new DelegatingBasicFileAttributes(delegate) {
            @Override
            public boolean isRegularFile() {
                return false;
            }

            @Override
            public boolean isOther() {
                return true;
            }
        };
    }

    private static BasicFileAttributes attributesWithSize(BasicFileAttributes delegate, long size) {
        return new DelegatingBasicFileAttributes(delegate) {
            @Override
            public long size() {
                return size;
            }
        };
    }

    private static boolean isRewrittenTemporaryArchive(Path path, Path archivePath) {
        Path normalized = path.toAbsolutePath().normalize();
        String fileName = normalized.getFileName().toString();
        return !normalized.equals(archivePath)
                && fileName.startsWith(archivePath.getFileName().toString())
                && fileName.endsWith(".tmp");
    }

    private void writeArchiveWithNestedLibrary(Path archivePath, String entryName, byte[] nestedLibrary)
            throws Exception {
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            add(outputStream, entryName, nestedLibrary);
        }
    }

    private byte[] nestedLibraryWithoutMavenMetadata() throws Exception {
        ByteArrayOutputStream nestedBytes = new ByteArrayOutputStream();
        try (JarOutputStream nested = new JarOutputStream(nestedBytes)) {
            add(nested, "com/example/Marker.class", new byte[] {1, 2, 3});
        }
        return nestedBytes.toByteArray();
    }

    private byte[] nestedLibraryWithManifestIdentity(String symbolicName, String implementationVersion)
            throws Exception {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Bundle-SymbolicName", symbolicName);
        attributes.put(Attributes.Name.IMPLEMENTATION_VERSION, implementationVersion);
        ByteArrayOutputStream nestedBytes = new ByteArrayOutputStream();
        try (JarOutputStream nested = new JarOutputStream(nestedBytes, manifest)) {
            add(nested, "com/example/Marker.class", new byte[] {1, 2, 3});
        }
        return nestedBytes.toByteArray();
    }

    private void writeAuditOnlyArchiveWithoutIndexes(Path archivePath) throws Exception {
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-audit", "1.0.0-SNAPSHOT");
        }
    }

    private void writeArchiveWithClasspathIndex(Path archivePath, byte[] indexContent) throws Exception {
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            add(outputStream, "BOOT-INF/classpath.idx", indexContent);
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-audit", "1.0.0-SNAPSHOT");
        }
    }

    private void writeArchiveWithLayersIndex(Path archivePath, byte[] indexContent) throws Exception {
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            add(outputStream, "BOOT-INF/layers.idx", indexContent);
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-audit", "1.0.0-SNAPSHOT");
        }
    }

    private void writeArchiveWithRawPomAndNestedEntries(Path archivePath, String pomProperties,
            Map<String, byte[]> nestedEntries) throws Exception {
        writeArchiveWithRawPomAndNestedEntries(archivePath,
                "META-INF/maven/io.github.patton174/coco-web/pom.properties",
                pomProperties, nestedEntries);
    }

    private void writeArchiveWithRawPomAndNestedEntries(Path archivePath, String pomPropertiesPath,
            String pomProperties, Map<String, byte[]> nestedEntries) throws Exception {
        ByteArrayOutputStream nestedBytes = new ByteArrayOutputStream();
        try (JarOutputStream nested = new JarOutputStream(nestedBytes)) {
            add(nested, pomPropertiesPath,
                    pomProperties.getBytes(StandardCharsets.ISO_8859_1));
            for (Map.Entry<String, byte[]> entry : nestedEntries.entrySet()) {
                add(nested, entry.getKey(), entry.getValue());
            }
        }
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            add(outputStream, "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar", nestedBytes.toByteArray());
        }
    }

    private void writeArchiveWithOuterEntries(Path archivePath, List<String> entryNames) throws Exception {
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
            for (String entryName : entryNames) {
                add(outputStream, entryName, new byte[] {1});
            }
        }
    }

    private void writeMybatisArchive(Path archivePath) throws Exception {
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            add(outputStream, "BOOT-INF/classpath.idx", """
                    - "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-mybatis-plus-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-feature-mybatis-plus-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/mybatis-3.5.19.jar"
                    - "BOOT-INF/lib/mybatis-extra-1.0.0.jar"
                    - "BOOT-INF/lib/mybatis-plus-core-3.5.16.jar"
                    - "BOOT-INF/lib/mybatis-plus-jsqlparser-common-3.5.16.jar"
                    - "BOOT-INF/lib/mybatis-plus-spring-3.5.16.jar"
                    - "BOOT-INF/lib/mybatis-plus-spring-boot-native-image-3.5.17.jar"
                    - "BOOT-INF/lib/mybatis-plus-spring-boot4-starter-3.5.16.jar"
                    - "BOOT-INF/lib/mybatis-spring-3.0.5.jar"
                    - "BOOT-INF/lib/freemarker-2.3.34.jar"
                    - "BOOT-INF/lib/spring-jdbc-7.0.0.jar"
                    """);
            add(outputStream, "BOOT-INF/layers.idx", """
                    - "dependencies":
                      - "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-mybatis-plus-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-feature-mybatis-plus-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/mybatis-3.5.19.jar"
                      - "BOOT-INF/lib/mybatis-extra-1.0.0.jar"
                      - "BOOT-INF/lib/mybatis-plus-core-3.5.16.jar"
                      - "BOOT-INF/lib/mybatis-plus-jsqlparser-common-3.5.16.jar"
                      - "BOOT-INF/lib/mybatis-plus-spring-3.5.16.jar"
                      - "BOOT-INF/lib/mybatis-plus-spring-boot-native-image-3.5.17.jar"
                      - "BOOT-INF/lib/mybatis-plus-spring-boot4-starter-3.5.16.jar"
                      - "BOOT-INF/lib/mybatis-spring-3.0.5.jar"
                      - "BOOT-INF/lib/freemarker-2.3.34.jar"
                      - "BOOT-INF/lib/spring-jdbc-7.0.0.jar"
                    """);
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-audit", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-mybatis-plus-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-mybatis-plus", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-feature-mybatis-plus-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-feature-mybatis-plus", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/mybatis-3.5.19.jar",
                    "org.mybatis", "mybatis", "3.5.19");
            addMavenArtifact(outputStream, "BOOT-INF/lib/mybatis-extra-1.0.0.jar",
                    "com.example", "mybatis-extra", "1.0.0");
            addMavenArtifact(outputStream, "BOOT-INF/lib/mybatis-plus-core-3.5.16.jar",
                    "com.baomidou", "mybatis-plus-core", "3.5.16");
            addMavenArtifact(outputStream, "BOOT-INF/lib/mybatis-plus-jsqlparser-common-3.5.16.jar",
                    "com.baomidou", "mybatis-plus-jsqlparser-common", "3.5.16");
            addMavenArtifact(outputStream, "BOOT-INF/lib/mybatis-plus-spring-3.5.16.jar",
                    "com.baomidou", "mybatis-plus-spring", "3.5.16");
            addMavenArtifact(outputStream, "BOOT-INF/lib/mybatis-plus-spring-boot-native-image-3.5.17.jar",
                    "com.baomidou", "mybatis-plus-spring-boot-native-image", "3.5.17");
            addMavenArtifact(outputStream, "BOOT-INF/lib/mybatis-plus-spring-boot4-starter-3.5.16.jar",
                    "com.baomidou", "mybatis-plus-spring-boot4-starter", "3.5.16");
            addMavenArtifact(outputStream, "BOOT-INF/lib/mybatis-spring-3.0.5.jar",
                    "org.mybatis", "mybatis-spring", "3.0.5");
            addMavenArtifact(outputStream, "BOOT-INF/lib/freemarker-2.3.34.jar",
                    "org.freemarker", "freemarker", "2.3.34");
            addMavenArtifact(outputStream, "BOOT-INF/lib/spring-jdbc-7.0.0.jar",
                    "org.springframework", "spring-jdbc", "7.0.0");
        }
    }

    private void writeArchiveWithStoredIndexes(Path archivePath) throws Exception {
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            addStored(outputStream, "BOOT-INF/classpath.idx", """
                    - "BOOT-INF/lib/coco-mybatis-plus-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-feature-mybatis-plus-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/mybatis-plus-core-RELEASE.jar"
                    - "BOOT-INF/lib/mybatis-plus-extension-v1.jar"
                    - "BOOT-INF/lib/mybatis-RELEASE.jar"
                    - "BOOT-INF/lib/mybatis-v1.jar"
                    - "BOOT-INF/lib/mybatis-extra-1.0.0.jar"
                    - "BOOT-INF/lib/spring-boot-4.1.0.jar"
                    """);
            addStored(outputStream, "BOOT-INF/layers.idx", """
                    - "dependencies":
                      - "BOOT-INF/lib/coco-mybatis-plus-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-feature-mybatis-plus-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/mybatis-plus-core-RELEASE.jar"
                      - "BOOT-INF/lib/mybatis-plus-extension-v1.jar"
                      - "BOOT-INF/lib/mybatis-RELEASE.jar"
                      - "BOOT-INF/lib/mybatis-v1.jar"
                      - "BOOT-INF/lib/mybatis-extra-1.0.0.jar"
                      - "BOOT-INF/lib/spring-boot-4.1.0.jar"
                    """);
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-mybatis-plus-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-mybatis-plus", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-feature-mybatis-plus-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-feature-mybatis-plus", "1.0.0-SNAPSHOT");
            add(outputStream, "BOOT-INF/lib/mybatis-plus-core-RELEASE.jar", "mybatis-plus-core");
            add(outputStream, "BOOT-INF/lib/mybatis-plus-extension-v1.jar", "mybatis-plus-extension");
            add(outputStream, "BOOT-INF/lib/mybatis-RELEASE.jar", "mybatis-release");
            add(outputStream, "BOOT-INF/lib/mybatis-v1.jar", "mybatis-v1");
            add(outputStream, "BOOT-INF/lib/mybatis-extra-1.0.0.jar", "mybatis-extra");
            addStored(outputStream, "BOOT-INF/lib/spring-boot-4.1.0.jar", "spring-boot");
        }
    }

    private void writeArchiveWithMismatchedIndexes(Path archivePath) throws Exception {
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            add(outputStream, "BOOT-INF/classpath.idx", """
                    - "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-tenant-1.0.0-SNAPSHOT.jar"
                    """);
            add(outputStream, "BOOT-INF/layers.idx", """
                    - "dependencies":
                      - "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-tenant-1.0.0-SNAPSHOT.jar"
                    """);
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-audit", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-tenant-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-tenant", "1.0.0-SNAPSHOT");
        }
    }

    private void writeSignedArchive(Path archivePath, String signatureEntry) throws Exception {
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            add(outputStream, signatureEntry, "signed");
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
        }
    }

    private void writeInvalidBootArchive(Path archivePath, InvalidBootArchive invalid) throws Exception {
        if (invalid == InvalidBootArchive.MISSING_MANIFEST) {
            try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(archivePath))) {
                addBootRuntimeEntries(outputStream);
                addMavenArtifact(outputStream, "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                        "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
            }
            return;
        }
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Main-Class", invalid == InvalidBootArchive.WRONG_MAIN_CLASS
                ? "com.example.Main"
                : "org.springframework.boot.loader.launch.JarLauncher");
        attributes.putValue("Start-Class", "com.example.DemoApplication");
        try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(archivePath), manifest)) {
            if (invalid != InvalidBootArchive.MISSING_LAUNCHER) {
                add(outputStream, "org/springframework/boot/loader/launch/JarLauncher.class", "launcher");
            }
            if (invalid != InvalidBootArchive.MISSING_CLASSES) {
                add(outputStream, "BOOT-INF/classes/com/example/DemoApplication.class", "demo");
            }
            if (invalid != InvalidBootArchive.MISSING_LIBRARIES) {
                addMavenArtifact(outputStream, "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                        "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
            }
        }
    }

    private void writeArchiveWithExtraEntry(Path archivePath, String entryName) throws Exception {
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            addMavenArtifact(outputStream, "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                    "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
            add(outputStream, entryName, "unsafe");
        }
    }

    private void writeDuplicateEntryArchive(Path archivePath) throws Exception {
        String first = "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar";
        String second = "BOOT-INF/lib/doco-web-1.0.0-SNAPSHOT.jar";
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            addMavenArtifact(outputStream, first,
                    "io.github.patton174", "coco-web", "1.0.0-SNAPSHOT");
            addMavenArtifact(outputStream, second, "com.example", "doco-web", "1.0.0-SNAPSHOT");
        }
        byte[] bytes = Files.readAllBytes(archivePath);
        replaceAscii(bytes, second, first);
        Files.write(archivePath, bytes);
    }

    private void writeIncompleteGavArchive(Path archivePath) throws Exception {
        ByteArrayOutputStream nestedBytes = new ByteArrayOutputStream();
        try (JarOutputStream nested = new JarOutputStream(nestedBytes)) {
            add(nested, "META-INF/maven/io.github.patton174/coco-web/pom.properties", """
                    groupId=io.github.patton174
                    artifactId=coco-web
                    """);
        }
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            outputStream.putNextEntry(new JarEntry("BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar"));
            outputStream.write(nestedBytes.toByteArray());
            outputStream.closeEntry();
        }
    }

    private CocoPackagePruneMojo configuredMojo(Path baseDir, Path buildDirectory,
            Path classesDirectory) throws Exception {
        return configuredMojo(buildDirectory, classesDirectory,
                project(baseDir, buildDirectory, classesDirectory));
    }

    private CocoPackagePruneMojo configuredMojo(Path buildDirectory,
            Path classesDirectory, MavenProject project) throws Exception {
        CocoPackagePruneMojo mojo = newMojo();
        set(mojo, "project", project);
        set(mojo, "classesDirectory", classesDirectory.toFile());
        set(mojo, "buildDirectory", buildDirectory.toFile());
        set(mojo, "finalName", "demo");
        return mojo;
    }

    private ArchivePublicationFixture archivePublicationFixture(String name) throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve(name));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeArchiveWithoutIndexes(archivePath);
        Path temporaryPath = buildDirectory.resolve("demo.jar.rewritten.tmp");
        writeAuditOnlyArchiveWithoutIndexes(temporaryPath);
        CocoPackagePruneMojo mojo = configuredMojo(baseDir, buildDirectory, classesDirectory);
        CocoPackagePruneMojo.BootArchiveView sourceView = new CocoPackagePruneMojo.BootArchiveView(
                List.of(
                        "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar"),
                Set.of());
        return new ArchivePublicationFixture(mojo, buildDirectory, archivePath, temporaryPath,
                sourceView, Set.of("BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar"));
    }

    private ArchiveExecutionFixture archiveExecutionFixture(String name) throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve(name));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeArchive(archivePath);
        MavenProject project = project(baseDir, buildDirectory, classesDirectory);
        return new ArchiveExecutionFixture(
                configuredMojo(buildDirectory, classesDirectory, project), project,
                buildDirectory, archivePath);
    }

    private void assertNoPublicationTemps(Path buildDirectory) throws Exception {
        try (var files = Files.list(buildDirectory)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.endsWith(".tmp"));
        }
    }

    private ProjectViewSnapshot primeProjectViews(MavenProject project) {
        List<Dependency> dependencies = new ArrayList<>();
        dependencies.add(dependency("com.example", "business-library", "1.0.0"));
        project.getModel().setDependencies(dependencies);
        project.setArtifacts(new LinkedHashSet<>());
        project.setDependencyArtifacts(new LinkedHashSet<>());
        return new ProjectViewSnapshot(project.getModel().getDependencies(),
                project.getArtifacts(), project.getDependencyArtifacts(),
                List.copyOf(project.getModel().getDependencies()));
    }

    private void assertProjectViewsUnchanged(MavenProject project, ProjectViewSnapshot expected) {
        assertThat(project.getModel().getDependencies()).isSameAs(expected.dependencies());
        assertThat(project.getArtifacts()).isSameAs(expected.artifacts());
        assertThat(project.getDependencyArtifacts()).isSameAs(expected.dependencyArtifacts());
        assertThat(project.getModel().getDependencies()).containsExactlyElementsOf(expected.dependencyValues());
    }

    private CocoPackagePruneMojo newMojo() throws Exception {
        CocoPackagePruneMojo mojo = new CocoPackagePruneMojo();
        set(mojo, "projectDependenciesResolver", projectDependenciesResolverReturning(request -> List.of()));
        set(mojo, "repositorySystemSession", repositorySystemSession());
        return mojo;
    }

    private void inspectPreflight(Path archivePath, CocoArchiveLimits limits) throws Exception {
        var plan = StandardCocoFeatures.resolve(CocoFeatureSelection.ofDisabled(Set.of(CocoFeature.WEB)));
        var manifest = StandardCocoFeatures.toManifest(plan, "resource-budget-test");
        try (JarFile archive = new JarFile(archivePath.toFile())) {
            CocoBootArchivePreflight.inspect(archive, manifest, "io.github.patton174",
                    "1.0.0-SNAPSHOT", Set.of("coco-web"), Set.of(), limits);
        }
    }

    private ArchiveReadMetrics consumeArchiveReads(Path archivePath,
            Set<CocoBootArchivePreflight.PrunableArtifact> resolvedArtifacts, long budgetBytes) throws Exception {
        var plan = StandardCocoFeatures.resolve(CocoFeatureSelection.ofDisabled(Set.of(CocoFeature.WEB)));
        var manifest = StandardCocoFeatures.toManifest(plan, "shared-budget-test");
        CocoArchiveIo.CumulativeBudget budget = new CocoArchiveIo.CumulativeBudget(
                budgetBytes, "Archive cumulative read bytes");
        try (JarFile archive = new JarFile(archivePath.toFile())) {
            CocoBootArchivePreflight.inspect(archive, manifest, "io.github.patton174",
                    "1.0.0-SNAPSHOT", Set.of("coco-web"), resolvedArtifacts,
                    CocoArchiveLimits.DEFAULT, budget);
            long afterPreflight = budget.consumed();
            CocoPackagePruneMojo.readUtf8(archive, archive.getJarEntry("BOOT-INF/classpath.idx"),
                    CocoArchiveLimits.DEFAULT, budget);
            long afterClasspath = budget.consumed();
            CocoPackagePruneMojo.readUtf8(archive, archive.getJarEntry("BOOT-INF/layers.idx"),
                    CocoArchiveLimits.DEFAULT, budget);
            return new ArchiveReadMetrics(afterPreflight, afterClasspath, budget.consumed());
        }
    }

    private void assertPreflightRejected(Path archivePath, CocoArchiveLimits limits,
            String expectedMessage) {
        assertThatThrownBy(() -> inspectPreflight(archivePath, limits))
                .isInstanceOf(IOException.class)
                .hasStackTraceContaining(expectedMessage);
    }

    private ArchiveMetrics outerArchiveMetrics(Path archivePath) throws Exception {
        int count = 0;
        long maximum = 0;
        long total = 0;
        int maximumNameBytes = 0;
        try (JarFile archive = new JarFile(archivePath.toFile())) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                count++;
                maximumNameBytes = Math.max(maximumNameBytes,
                        entry.getName().getBytes(StandardCharsets.UTF_8).length);
                if (!entry.isDirectory()) {
                    try (InputStream inputStream = archive.getInputStream(entry)) {
                        long bytes = countBytes(inputStream);
                        maximum = Math.max(maximum, bytes);
                        total += bytes;
                    }
                }
            }
        }
        return new ArchiveMetrics(count, maximum, total, maximumNameBytes);
    }

    private ArchiveMetrics nestedArchiveMetrics(Path archivePath, String libraryName) throws Exception {
        int count = 0;
        long maximum = 0;
        long total = 0;
        int maximumNameBytes = 0;
        try (JarFile archive = new JarFile(archivePath.toFile());
                ZipInputStream nested = new ZipInputStream(
                        archive.getInputStream(archive.getJarEntry(libraryName)))) {
            ZipEntry entry;
            while ((entry = nested.getNextEntry()) != null) {
                count++;
                maximumNameBytes = Math.max(maximumNameBytes,
                        entry.getName().getBytes(StandardCharsets.UTF_8).length);
                if (!entry.isDirectory()) {
                    long bytes = countBytes(nested);
                    maximum = Math.max(maximum, bytes);
                    total += bytes;
                }
            }
        }
        return new ArchiveMetrics(count, maximum, total, maximumNameBytes);
    }

    private long countBytes(InputStream inputStream) throws Exception {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = inputStream.read(buffer)) >= 0) {
            total += read;
        }
        return total;
    }

    private String validPomProperties() {
        return "groupId=io.github.patton174\n"
                + "artifactId=coco-web\n"
                + "version=1.0.0-SNAPSHOT\n";
    }

    private String paddedPomProperties(int byteLength) {
        String base = validPomProperties();
        int remaining = byteLength - base.getBytes(StandardCharsets.ISO_8859_1).length;
        assertThat(remaining).isGreaterThan(1);
        return base + "#" + "a".repeat(remaining - 1);
    }

    private CocoArchiveLimits withIndexBytes(CocoArchiveLimits limits, long indexBytes) {
        return new CocoArchiveLimits(
                limits.outerEntryBytes(), limits.outerTotalBytes(), limits.archiveReadBytes(),
                limits.resolvedArtifactsBytes(), limits.nestedEntryBytes(),
                limits.nestedLibraryBytes(), limits.nestedArchiveBytes(), limits.pomPropertiesBytes(),
                limits.manifestBytes(), indexBytes, limits.indexLineBytes(), limits.layerCount(),
                limits.entryCount(), limits.entryNameBytes(), limits.gavValueBytes(),
                limits.yamlNestingDepth(), limits.executablePrefixBytes());
    }

    private CocoArchiveLimits withOuterLimits(CocoArchiveLimits limits, long entryBytes,
            long totalBytes, int entryCount, int entryNameBytes) {
        return new CocoArchiveLimits(
                entryBytes, totalBytes, limits.archiveReadBytes(), limits.resolvedArtifactsBytes(),
                limits.nestedEntryBytes(), limits.nestedLibraryBytes(),
                limits.nestedArchiveBytes(), limits.pomPropertiesBytes(), limits.manifestBytes(),
                limits.indexBytes(), limits.indexLineBytes(), limits.layerCount(), entryCount,
                entryNameBytes, limits.gavValueBytes(), limits.yamlNestingDepth(),
                limits.executablePrefixBytes());
    }

    private CocoArchiveLimits withNestedLimits(CocoArchiveLimits limits, long entryBytes,
            long libraryBytes, long archiveBytes, long pomPropertiesBytes) {
        return new CocoArchiveLimits(
                limits.outerEntryBytes(), limits.outerTotalBytes(), limits.archiveReadBytes(),
                limits.resolvedArtifactsBytes(), entryBytes, libraryBytes,
                archiveBytes, pomPropertiesBytes, limits.manifestBytes(), limits.indexBytes(),
                limits.indexLineBytes(), limits.layerCount(), limits.entryCount(),
                limits.entryNameBytes(), limits.gavValueBytes(), limits.yamlNestingDepth(),
                limits.executablePrefixBytes());
    }

    private CocoArchiveLimits withManifestBytes(CocoArchiveLimits limits, long manifestBytes) {
        return new CocoArchiveLimits(
                limits.outerEntryBytes(), limits.outerTotalBytes(), limits.archiveReadBytes(),
                limits.resolvedArtifactsBytes(), limits.nestedEntryBytes(),
                limits.nestedLibraryBytes(), limits.nestedArchiveBytes(), limits.pomPropertiesBytes(),
                manifestBytes, limits.indexBytes(), limits.indexLineBytes(), limits.layerCount(),
                limits.entryCount(), limits.entryNameBytes(), limits.gavValueBytes(),
                limits.yamlNestingDepth(), limits.executablePrefixBytes());
    }

    private CocoArchiveLimits withParserLimits(CocoArchiveLimits limits,
            int lineBytes, int layerCount) {
        return new CocoArchiveLimits(
                limits.outerEntryBytes(), limits.outerTotalBytes(), limits.archiveReadBytes(),
                limits.resolvedArtifactsBytes(), limits.nestedEntryBytes(),
                limits.nestedLibraryBytes(), limits.nestedArchiveBytes(), limits.pomPropertiesBytes(),
                limits.manifestBytes(), limits.indexBytes(), lineBytes, layerCount, limits.entryCount(),
                limits.entryNameBytes(), limits.gavValueBytes(), limits.yamlNestingDepth(),
                limits.executablePrefixBytes());
    }

    private CocoArchiveLimits withResolvedArtifactsBytes(CocoArchiveLimits limits,
            long resolvedArtifactsBytes) {
        return new CocoArchiveLimits(
                limits.outerEntryBytes(), limits.outerTotalBytes(), limits.archiveReadBytes(),
                resolvedArtifactsBytes, limits.nestedEntryBytes(), limits.nestedLibraryBytes(),
                limits.nestedArchiveBytes(), limits.pomPropertiesBytes(), limits.manifestBytes(),
                limits.indexBytes(), limits.indexLineBytes(), limits.layerCount(), limits.entryCount(),
                limits.entryNameBytes(), limits.gavValueBytes(), limits.yamlNestingDepth(),
                limits.executablePrefixBytes());
    }

    private CocoArchiveLimits withExecutablePrefixBytes(CocoArchiveLimits limits,
            long executablePrefixBytes) {
        return new CocoArchiveLimits(
                limits.outerEntryBytes(), limits.outerTotalBytes(), limits.archiveReadBytes(),
                limits.resolvedArtifactsBytes(), limits.nestedEntryBytes(), limits.nestedLibraryBytes(),
                limits.nestedArchiveBytes(), limits.pomPropertiesBytes(), limits.manifestBytes(),
                limits.indexBytes(), limits.indexLineBytes(), limits.layerCount(), limits.entryCount(),
                limits.entryNameBytes(), limits.gavValueBytes(), limits.yamlNestingDepth(),
                executablePrefixBytes);
    }

    private CocoArchiveLimits withArchiveReadBytes(CocoArchiveLimits limits, long archiveReadBytes) {
        return new CocoArchiveLimits(
                limits.outerEntryBytes(), limits.outerTotalBytes(), archiveReadBytes,
                limits.resolvedArtifactsBytes(), limits.nestedEntryBytes(), limits.nestedLibraryBytes(),
                limits.nestedArchiveBytes(), limits.pomPropertiesBytes(), limits.manifestBytes(),
                limits.indexBytes(), limits.indexLineBytes(), limits.layerCount(), limits.entryCount(),
                limits.entryNameBytes(), limits.gavValueBytes(), limits.yamlNestingDepth(),
                limits.executablePrefixBytes());
    }

    private void assertArchiveRejected(CocoPackagePruneMojo mojo, Path archivePath,
            String expectedMessage) throws Exception {
        byte[] original = Files.readAllBytes(archivePath);
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasRootCauseInstanceOf(java.io.IOException.class)
                .hasStackTraceContaining(expectedMessage);
        assertThat(Files.readAllBytes(archivePath)).isEqualTo(original);
        assertThat(archivePath.getParent().resolve("coco-prune.original.jar")).doesNotExist();
        try (var files = Files.list(archivePath.getParent())) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.startsWith(archivePath.getFileName().toString())
                            && name.endsWith(".tmp"));
        }
    }

    private void replaceAscii(byte[] bytes, String source, String replacement) {
        byte[] sourceBytes = source.getBytes(StandardCharsets.US_ASCII);
        byte[] replacementBytes = replacement.getBytes(StandardCharsets.US_ASCII);
        assertThat(replacementBytes).hasSameSizeAs(sourceBytes);
        int replacements = 0;
        for (int index = 0; index <= bytes.length - sourceBytes.length; index++) {
            boolean matches = true;
            for (int offset = 0; offset < sourceBytes.length; offset++) {
                matches &= bytes[index + offset] == sourceBytes[offset];
            }
            if (matches) {
                System.arraycopy(replacementBytes, 0, bytes, index, replacementBytes.length);
                replacements++;
            }
        }
        assertThat(replacements).isGreaterThanOrEqualTo(2);
    }

    private enum InvalidBootArchive {
        MISSING_MANIFEST,
        WRONG_MAIN_CLASS,
        MISSING_LAUNCHER,
        MISSING_CLASSES,
        MISSING_LIBRARIES
    }

    private record ArchiveReadMetrics(long afterPreflight, long afterClasspath, long afterLayers) {
    }

    private record ArchivePublicationFixture(
            CocoPackagePruneMojo mojo,
            Path buildDirectory,
            Path archivePath,
            Path temporaryPath,
            CocoPackagePruneMojo.BootArchiveView sourceView,
            Set<String> prunedEntries) {
    }

    private record ArchiveExecutionFixture(
            CocoPackagePruneMojo mojo,
            MavenProject project,
            Path buildDirectory,
            Path archivePath) {
    }

    private record ProjectViewSnapshot(
            List<Dependency> dependencies,
            Set<org.apache.maven.artifact.Artifact> artifacts,
            Set<org.apache.maven.artifact.Artifact> dependencyArtifacts,
            List<Dependency> dependencyValues) {
    }

    private static class DelegatingBasicFileAttributes implements BasicFileAttributes {

        private final BasicFileAttributes delegate;

        private DelegatingBasicFileAttributes(BasicFileAttributes delegate) {
            this.delegate = delegate;
        }

        @Override
        public java.nio.file.attribute.FileTime lastModifiedTime() {
            return this.delegate.lastModifiedTime();
        }

        @Override
        public java.nio.file.attribute.FileTime lastAccessTime() {
            return this.delegate.lastAccessTime();
        }

        @Override
        public java.nio.file.attribute.FileTime creationTime() {
            return this.delegate.creationTime();
        }

        @Override
        public boolean isRegularFile() {
            return this.delegate.isRegularFile();
        }

        @Override
        public boolean isDirectory() {
            return this.delegate.isDirectory();
        }

        @Override
        public boolean isSymbolicLink() {
            return this.delegate.isSymbolicLink();
        }

        @Override
        public boolean isOther() {
            return this.delegate.isOther();
        }

        @Override
        public long size() {
            return this.delegate.size();
        }

        @Override
        public Object fileKey() {
            return this.delegate.fileKey();
        }
    }

    private static final class TestFileStore extends FileStore {

        private final String name;

        private TestFileStore(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public String type() {
            return "test";
        }

        @Override
        public boolean isReadOnly() {
            return false;
        }

        @Override
        public long getTotalSpace() {
            return 0;
        }

        @Override
        public long getUsableSpace() {
            return 0;
        }

        @Override
        public long getUnallocatedSpace() {
            return 0;
        }

        @Override
        public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
            return false;
        }

        @Override
        public boolean supportsFileAttributeView(String name) {
            return false;
        }

        @Override
        public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
            return null;
        }

        @Override
        public Object getAttribute(String attribute) {
            throw new UnsupportedOperationException(attribute);
        }
    }

    private record ArchiveMetrics(int entryCount, long maximumEntryBytes,
            long totalBytes, int maximumNameBytes) {
    }

    private JarOutputStream newBootArchive(Path archivePath) throws Exception {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Main-Class", "org.springframework.boot.loader.launch.JarLauncher");
        attributes.putValue("Start-Class", "com.example.DemoApplication");
        return new JarOutputStream(Files.newOutputStream(archivePath), manifest);
    }

    private void addBootRuntimeEntries(JarOutputStream outputStream) throws Exception {
        add(outputStream, "org/springframework/boot/loader/launch/JarLauncher.class", "launcher");
        add(outputStream, "BOOT-INF/classes/com/example/DemoApplication.class", "demo");
    }

    private void assertRunnableSpringBootArchive(Path archivePath) throws Exception {
        try (JarFile jarFile = new JarFile(archivePath.toFile())) {
            Manifest manifest = jarFile.getManifest();
            assertThat(manifest).isNotNull();
            Attributes attributes = manifest.getMainAttributes();
            assertThat(attributes.getValue("Main-Class"))
                    .isEqualTo("org.springframework.boot.loader.launch.JarLauncher");
            assertThat(attributes.getValue("Start-Class"))
                    .isEqualTo("com.example.DemoApplication");
            assertThat(jarFile.getEntry("org/springframework/boot/loader/launch/JarLauncher.class")).isNotNull();
            assertThat(jarFile.getEntry("BOOT-INF/classes/com/example/DemoApplication.class")).isNotNull();
            assertThat(jarFile.stream().map(JarEntry::getName)
                    .anyMatch(name -> name.startsWith("BOOT-INF/lib/") && name.endsWith(".jar"))).isTrue();
        }
    }

    private MavenProject project(Path baseDir, Path buildDirectory, Path classesDirectory) throws Exception {
        Model model = new Model();
        model.setGroupId("com.example");
        model.setArtifactId("demo");
        model.setVersion("1.0.0");
        Build build = new Build();
        build.setDirectory(buildDirectory.toString());
        build.setOutputDirectory(classesDirectory.toString());
        build.setFinalName("demo");
        model.setBuild(build);
        MavenProject project = new MavenProject(model);
        project.setFile(baseDir.resolve("pom.xml").toFile());
        Files.writeString(project.getFile().toPath(), "<project />", StandardCharsets.UTF_8);
        return project;
    }

    private Dependency dependency(String groupId, String artifactId, String version) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setVersion(version);
        return dependency;
    }

    private Set<org.apache.maven.artifact.Artifact> mybatisArchiveArtifacts() throws Exception {
        return new LinkedHashSet<>(List.of(
                resolvedArtifact("io.github.patton174", "coco-web", "1.0.0-SNAPSHOT"),
                resolvedArtifact("io.github.patton174", "coco-audit", "1.0.0-SNAPSHOT"),
                resolvedArtifact("io.github.patton174", "coco-mybatis-plus", "1.0.0-SNAPSHOT"),
                resolvedArtifact("io.github.patton174", "coco-feature-mybatis-plus", "1.0.0-SNAPSHOT"),
                resolvedArtifact("org.mybatis", "mybatis", "3.5.19"),
                resolvedArtifact("com.example", "mybatis-extra", "1.0.0"),
                resolvedArtifact("com.baomidou", "mybatis-plus-core", "3.5.16"),
                resolvedArtifact("com.baomidou", "mybatis-plus-jsqlparser-common", "3.5.16"),
                resolvedArtifact("com.baomidou", "mybatis-plus-spring", "3.5.16"),
                resolvedArtifact("com.baomidou", "mybatis-plus-spring-boot-native-image", "3.5.17"),
                resolvedArtifact("com.baomidou", "mybatis-plus-spring-boot4-starter", "3.5.16"),
                resolvedArtifact("org.mybatis", "mybatis-spring", "3.0.5"),
                resolvedArtifact("org.freemarker", "freemarker", "2.3.34"),
                resolvedArtifact("org.springframework", "spring-jdbc", "7.0.0")));
    }

    private org.apache.maven.artifact.Artifact resolvedArtifact(String groupId, String artifactId,
            String version) throws Exception {
        Path artifactFile = Files.createDirectories(this.tempDir.resolve("resolved-artifacts"))
                .resolve(groupId.replace('.', '/'))
                .resolve(artifactId)
                .resolve(version)
                .resolve(artifactId + "-" + version + ".jar");
        Files.createDirectories(artifactFile.getParent());
        Files.write(artifactFile, this.resolvedArtifactBytes.getOrDefault(
                artifactKey(groupId, artifactId, version), new byte[0]));
        DefaultArtifact artifact = new DefaultArtifact(groupId, artifactId, version, "compile", "jar", null,
                new DefaultArtifactHandler("jar"));
        artifact.setFile(artifactFile.toFile());
        artifact.setResolved(true);
        return artifact;
    }

    private void setArtifactFileOutsideRepository(org.apache.maven.artifact.Artifact artifact,
            String directoryName, byte[] content) throws Exception {
        Path artifactFile = Files.createDirectories(this.tempDir.resolve("outside-repository")
                .resolve(directoryName)).resolve(artifact.getFile().getName());
        Files.write(artifactFile, content);
        artifact.setFile(artifactFile.toFile());
    }

    private org.eclipse.aether.graph.Dependency resolvedDependency(String groupId, String artifactId,
            String version) throws Exception {
        org.eclipse.aether.artifact.Artifact artifact = new org.eclipse.aether.artifact.DefaultArtifact(
                groupId, artifactId, "jar", version)
                .setFile(resolvedArtifact(groupId, artifactId, version).getFile());
        return new org.eclipse.aether.graph.Dependency(artifact, "compile");
    }

    private List<org.eclipse.aether.graph.Dependency> resolvedDependencies(
            Set<org.apache.maven.artifact.Artifact> artifacts) {
        return artifacts.stream()
                .map(artifact -> new org.eclipse.aether.graph.Dependency(
                        new org.eclipse.aether.artifact.DefaultArtifact(
                                artifact.getGroupId(), artifact.getArtifactId(), "jar", artifact.getBaseVersion())
                                .setFile(artifact.getFile()),
                        "compile"))
                .toList();
    }

    private ProjectDependenciesResolver projectDependenciesResolverReturning(
            java.util.function.Function<DependencyResolutionRequest,
                    List<org.eclipse.aether.graph.Dependency>> resolution) {
        return request -> dependencyResolutionResult(resolution.apply(request));
    }

    private DependencyResolutionResult dependencyResolutionResult(
            List<org.eclipse.aether.graph.Dependency> resolvedDependencies) {
        return (DependencyResolutionResult) Proxy.newProxyInstance(
                DependencyResolutionResult.class.getClassLoader(),
                new Class<?>[] {DependencyResolutionResult.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getDependencies", "getResolvedDependencies" -> resolvedDependencies;
                    case "getUnresolvedDependencies", "getCollectionErrors", "getResolutionErrors" -> List.of();
                    case "getDependencyGraph" -> null;
                    default -> proxyObjectMethod(proxy, method.getName(), arguments);
                });
    }

    private RepositorySystemSession repositorySystemSession() {
        return (RepositorySystemSession) Proxy.newProxyInstance(
                RepositorySystemSession.class.getClassLoader(),
                new Class<?>[] {RepositorySystemSession.class},
                (proxy, method, arguments) -> proxyObjectMethod(proxy, method.getName(), arguments));
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

    private Set<String> entries(Path archivePath) throws Exception {
        try (JarFile jarFile = new JarFile(archivePath.toFile())) {
            return jarFile.stream().map(JarEntry::getName).collect(Collectors.toUnmodifiableSet());
        }
    }

    private Set<String> bootLibraries(Path archivePath) throws Exception {
        return entries(archivePath).stream()
                .filter(name -> name.startsWith("BOOT-INF/lib/") && name.endsWith(".jar"))
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> indexReferences(Path archivePath, String indexName) throws Exception {
        return readEntry(archivePath, indexName).lines()
                .map(this::bootLibraryReference)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String bootLibraryReference(String line) {
        int start = line.indexOf("BOOT-INF/lib/");
        if (start < 0) {
            return null;
        }
        int end = line.indexOf(".jar", start);
        return end < 0 ? null : line.substring(start, end + ".jar".length());
    }

    private Set<String> difference(Set<String> before, Set<String> after) {
        Set<String> difference = new LinkedHashSet<>(before);
        difference.removeAll(after);
        return Set.copyOf(difference);
    }

    private void assertIndexesMatchLibraries(Path archivePath) throws Exception {
        Set<String> libraries = bootLibraries(archivePath);
        assertThat(indexReferences(archivePath, "BOOT-INF/classpath.idx")).isEqualTo(libraries);
        assertThat(indexReferences(archivePath, "BOOT-INF/layers.idx")).isEqualTo(libraries);
    }

    private String readEntry(Path archivePath, String name) throws Exception {
        return new String(readEntryBytes(archivePath, name), StandardCharsets.UTF_8);
    }

    private byte[] readEntryBytes(Path archivePath, String name) throws Exception {
        try (JarFile jarFile = new JarFile(archivePath.toFile())) {
            try (var inputStream = jarFile.getInputStream(jarFile.getEntry(name))) {
                return inputStream.readAllBytes();
            }
        }
    }

    private int entryMethod(Path archivePath, String name) throws Exception {
        try (JarFile jarFile = new JarFile(archivePath.toFile())) {
            return jarFile.getEntry(name).getMethod();
        }
    }

    private void add(JarOutputStream outputStream, String name, String content) throws Exception {
        add(outputStream, name, content.getBytes(StandardCharsets.UTF_8));
    }

    private void add(JarOutputStream outputStream, String name, byte[] content) throws Exception {
        outputStream.putNextEntry(new JarEntry(name));
        outputStream.write(content);
        outputStream.closeEntry();
    }

    private void addMavenArtifact(JarOutputStream outputStream, String name,
            String groupId, String artifactId, String version) throws Exception {
        ByteArrayOutputStream nestedBytes = new ByteArrayOutputStream();
        try (JarOutputStream nested = new JarOutputStream(nestedBytes)) {
            add(nested, "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties", """
                    groupId=%s
                    artifactId=%s
                    version=%s
                    """.formatted(groupId, artifactId, version));
        }
        byte[] bytes = nestedBytes.toByteArray();
        this.resolvedArtifactBytes.put(artifactKey(groupId, artifactId, version), bytes);
        outputStream.putNextEntry(new JarEntry(name));
        outputStream.write(bytes);
        outputStream.closeEntry();
    }

    private String artifactKey(String groupId, String artifactId, String version) {
        return groupId + ":" + artifactId + ":" + version;
    }

    private void addStored(JarOutputStream outputStream, String name, String content) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(bytes);
        JarEntry entry = new JarEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(bytes.length);
        entry.setCompressedSize(bytes.length);
        entry.setCrc(crc.getValue());
        outputStream.putNextEntry(entry);
        outputStream.write(bytes);
        outputStream.closeEntry();
    }

    private void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
