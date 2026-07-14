package io.github.coco.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.model.CocoFeatureManifestLoader;
import io.github.coco.feature.model.CocoFeatureSelection;
import io.github.coco.feature.model.StandardCocoFeatures;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
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

    @Test
    void removesDisabledFeatureJarsFromSpringBootArchive() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("project"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.TENANT, CocoFeature.DATA_PERMISSION));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeArchive(archivePath);

        CocoPackagePruneMojo mojo = new CocoPackagePruneMojo();
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

        CocoPackagePruneMojo mojo = new CocoPackagePruneMojo();
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

        CocoPackagePruneMojo mojo = new CocoPackagePruneMojo();
        set(mojo, "project", project);
        set(mojo, "classesDirectory", classesDirectory.toFile());
        set(mojo, "buildDirectory", buildDirectory.toFile());
        set(mojo, "finalName", "demo");

        mojo.execute();

        assertThat(entries(archivePath))
                .contains(
                        "BOOT-INF/lib/mybatis-3.5.19.jar",
                        "BOOT-INF/lib/mybatis-plus-core-3.5.16.jar",
                        "BOOT-INF/lib/mybatis-plus-jsqlparser-common-3.5.16.jar",
                        "BOOT-INF/lib/mybatis-plus-spring-3.5.16.jar",
                        "BOOT-INF/lib/mybatis-plus-spring-boot-native-image-3.5.17.jar",
                        "BOOT-INF/lib/mybatis-plus-spring-boot4-starter-3.5.16.jar",
                        "BOOT-INF/lib/mybatis-spring-3.0.5.jar",
                        "BOOT-INF/lib/freemarker-2.3.34.jar")
                .doesNotContain(
                        "BOOT-INF/lib/coco-mybatis-plus-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-feature-mybatis-plus-1.0.0-SNAPSHOT.jar");
        assertThat(readEntry(archivePath, "BOOT-INF/classpath.idx"))
                .contains("mybatis-3.5.19", "mybatis-plus-core", "mybatis-spring", "freemarker-2.3.34")
                .doesNotContain("coco-mybatis-plus", "coco-feature-mybatis-plus");
        assertThat(readEntry(archivePath, "BOOT-INF/layers.idx"))
                .contains("mybatis-3.5.19", "mybatis-plus-core", "mybatis-spring", "freemarker-2.3.34")
                .doesNotContain("coco-mybatis-plus", "coco-feature-mybatis-plus");
        assertRunnableSpringBootArchive(archivePath);
    }

    @Test
    void rejectsManifestPruneIdsOutsideStandardCocoDefinitions() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("existing-unsafe-manifest"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeExistingUnsafeMybatisManifest(classesDirectory);
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeMybatisArchive(archivePath);

        CocoPackagePruneMojo mojo = new CocoPackagePruneMojo();
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

        CocoPackagePruneMojo mojo = new CocoPackagePruneMojo();
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
    void rewritesStoredSpringBootIndexesWhenContentChanges() throws Exception {
        Path baseDir = Files.createDirectories(this.tempDir.resolve("stored-index"));
        Path buildDirectory = Files.createDirectories(baseDir.resolve("target"));
        Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.MYBATIS_PLUS));
        Path archivePath = buildDirectory.resolve("demo.jar");
        writeArchiveWithStoredIndexes(archivePath);

        CocoPackagePruneMojo mojo = new CocoPackagePruneMojo();
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

        CocoPackagePruneMojo mojo = new CocoPackagePruneMojo();
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

    private void writeArchive(Path archivePath) throws Exception {
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            add(outputStream, "BOOT-INF/classpath.idx", """
                    - "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-tenant-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-feature-tenant-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-data-permission-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-feature-data-permission-1.0.0-SNAPSHOT.jar"
                    """);
            add(outputStream, "BOOT-INF/layers.idx", """
                    - "dependencies":
                      - "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar"
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
            add(outputStream, "BOOT-INF/lib/mybatis-3.5.19.jar", "mybatis");
            add(outputStream, "BOOT-INF/lib/mybatis-extra-1.0.0.jar", "mybatis-extra");
            add(outputStream, "BOOT-INF/lib/mybatis-plus-core-3.5.16.jar", "mybatis-plus-core");
            add(outputStream, "BOOT-INF/lib/mybatis-plus-jsqlparser-common-3.5.16.jar", "mybatis-jsqlparser-common");
            add(outputStream, "BOOT-INF/lib/mybatis-plus-spring-3.5.16.jar", "mybatis-plus-spring");
            add(outputStream, "BOOT-INF/lib/mybatis-plus-spring-boot-native-image-3.5.17.jar", "mybatis-native-image");
            add(outputStream, "BOOT-INF/lib/mybatis-plus-spring-boot4-starter-3.5.16.jar", "mybatis-starter");
            add(outputStream, "BOOT-INF/lib/mybatis-spring-3.0.5.jar", "mybatis-spring");
            add(outputStream, "BOOT-INF/lib/freemarker-2.3.34.jar", "freemarker");
            add(outputStream, "BOOT-INF/lib/spring-jdbc-7.0.0.jar", "spring-jdbc");
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
        CocoPackagePruneMojo mojo = new CocoPackagePruneMojo();
        set(mojo, "project", project(baseDir, buildDirectory, classesDirectory));
        set(mojo, "classesDirectory", classesDirectory.toFile());
        set(mojo, "buildDirectory", buildDirectory.toFile());
        set(mojo, "finalName", "demo");
        return mojo;
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

    private Set<String> entries(Path archivePath) throws Exception {
        try (JarFile jarFile = new JarFile(archivePath.toFile())) {
            return jarFile.stream().map(JarEntry::getName).collect(Collectors.toUnmodifiableSet());
        }
    }

    private String readEntry(Path archivePath, String name) throws Exception {
        try (JarFile jarFile = new JarFile(archivePath.toFile())) {
            try (var inputStream = jarFile.getInputStream(jarFile.getEntry(name))) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    private int entryMethod(Path archivePath, String name) throws Exception {
        try (JarFile jarFile = new JarFile(archivePath.toFile())) {
            return jarFile.getEntry(name).getMethod();
        }
    }

    private void add(JarOutputStream outputStream, String name, String content) throws Exception {
        outputStream.putNextEntry(new JarEntry(name));
        outputStream.write(content.getBytes(StandardCharsets.UTF_8));
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
        outputStream.putNextEntry(new JarEntry(name));
        outputStream.write(nestedBytes.toByteArray());
        outputStream.closeEntry();
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
