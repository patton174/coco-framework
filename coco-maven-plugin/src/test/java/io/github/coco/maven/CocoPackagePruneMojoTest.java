package io.github.coco.maven;

import static org.assertj.core.api.Assertions.assertThat;

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
import io.github.coco.feature.registry.CocoFeatureManifestLoader;
import io.github.coco.feature.registry.CocoFeatureSelection;
import io.github.coco.feature.registry.StandardCocoFeatures;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
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
                        "BOOT-INF/lib/coco-feature-web-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-feature-audit-1.0.0-SNAPSHOT.jar")
                .doesNotContain(
                        "BOOT-INF/lib/coco-feature-tenant-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-feature-data-permission-1.0.0-SNAPSHOT.jar");
        assertThat(readEntry(archivePath, "BOOT-INF/classpath.idx"))
                .contains("coco-feature-web")
                .doesNotContain("coco-feature-tenant", "coco-feature-data-permission");
        assertThat(readEntry(archivePath, "BOOT-INF/layers.idx"))
                .contains("coco-feature-web")
                .doesNotContain("coco-feature-tenant", "coco-feature-data-permission");
        assertRunnableSpringBootArchive(archivePath);
        Path originalArchivePath = buildDirectory.resolve("coco-prune.original.jar");
        assertThat(originalArchivePath).isRegularFile();
        assertThat(entries(originalArchivePath))
                .contains(
                        "BOOT-INF/lib/coco-feature-tenant-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-feature-data-permission-1.0.0-SNAPSHOT.jar");
        assertThat(readEntry(originalArchivePath, "BOOT-INF/classpath.idx"))
                .contains("coco-feature-tenant", "coco-feature-data-permission");
    }

    @Test
    void removesDisabledFeatureTransitiveJarsFromSpringBootArchive() throws Exception {
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
                        "BOOT-INF/lib/coco-feature-web-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/coco-feature-audit-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/mybatis-extra-1.0.0.jar",
                        "BOOT-INF/lib/spring-jdbc-7.0.0.jar")
                .doesNotContain(
                        "BOOT-INF/lib/coco-feature-mybatis-plus-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/mybatis-3.5.19.jar",
                        "BOOT-INF/lib/mybatis-plus-core-3.5.16.jar",
                        "BOOT-INF/lib/mybatis-plus-jsqlparser-common-3.5.16.jar",
                        "BOOT-INF/lib/mybatis-plus-spring-3.5.16.jar",
                        "BOOT-INF/lib/mybatis-plus-spring-boot4-starter-3.5.16.jar",
                        "BOOT-INF/lib/mybatis-spring-3.0.5.jar");
        assertThat(readEntry(archivePath, "BOOT-INF/classpath.idx"))
                .contains("coco-feature-audit", "mybatis-extra", "spring-jdbc")
                .doesNotContain(
                        "coco-feature-mybatis-plus",
                        "mybatis-3.5.19",
                        "mybatis-plus-core",
                        "mybatis-plus-jsqlparser-common",
                        "mybatis-plus-spring-boot4-starter",
                        "mybatis-spring");
        assertThat(readEntry(archivePath, "BOOT-INF/layers.idx"))
                .contains("coco-feature-audit", "mybatis-extra", "spring-jdbc")
                .doesNotContain(
                        "coco-feature-mybatis-plus",
                        "mybatis-3.5.19",
                        "mybatis-plus-core",
                        "mybatis-plus-jsqlparser-common",
                        "mybatis-plus-spring-boot4-starter",
                        "mybatis-spring");
        assertRunnableSpringBootArchive(archivePath);
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
                .contains("mybatis-extra-1.0.0.jar")
                .doesNotContain(
                        "coco-feature-mybatis-plus",
                        "mybatis-plus-core",
                        "mybatis-plus-extension",
                        "mybatis-RELEASE.jar",
                        "mybatis-v1.jar");
        assertThat(entries(archivePath))
                .doesNotContain(
                        "BOOT-INF/lib/coco-feature-mybatis-plus-1.0.0-SNAPSHOT.jar",
                        "BOOT-INF/lib/mybatis-plus-core-RELEASE.jar",
                        "BOOT-INF/lib/mybatis-plus-extension-v1.jar",
                        "BOOT-INF/lib/mybatis-RELEASE.jar",
                        "BOOT-INF/lib/mybatis-v1.jar")
                .contains(
                        "BOOT-INF/lib/spring-boot-4.1.0.jar",
                        "BOOT-INF/lib/mybatis-extra-1.0.0.jar");
        assertThat(entryMethod(archivePath, "BOOT-INF/lib/spring-boot-4.1.0.jar"))
                .isEqualTo(ZipEntry.STORED);
        assertRunnableSpringBootArchive(archivePath);
    }

    private void writeManifest(Path classesDirectory, Set<CocoFeature> disabledFeatures) throws Exception {
        Path manifestPath = classesDirectory.resolve(CocoFeatureManifestLoader.MANIFEST_LOCATION);
        Files.createDirectories(manifestPath.getParent());
        var plan = StandardCocoFeatures.resolve(CocoFeatureSelection.ofDisabled(disabledFeatures));
        Files.writeString(manifestPath,
                CocoFeatureManifestLoader.write(StandardCocoFeatures.toManifest(plan, "test")),
                StandardCharsets.UTF_8);
    }

    private void writeArchive(Path archivePath) throws Exception {
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            add(outputStream, "BOOT-INF/classpath.idx", """
                    - "BOOT-INF/lib/coco-feature-web-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-feature-tenant-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-feature-data-permission-1.0.0-SNAPSHOT.jar"
                    """);
            add(outputStream, "BOOT-INF/layers.idx", """
                    - "dependencies":
                      - "BOOT-INF/lib/coco-feature-web-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-feature-tenant-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-feature-data-permission-1.0.0-SNAPSHOT.jar"
                    """);
            add(outputStream, "BOOT-INF/classes/application.yml", "spring.application.name=demo");
            add(outputStream, "BOOT-INF/lib/coco-feature-web-1.0.0-SNAPSHOT.jar", "web");
            add(outputStream, "BOOT-INF/lib/coco-feature-audit-1.0.0-SNAPSHOT.jar", "audit");
            add(outputStream, "BOOT-INF/lib/coco-feature-tenant-1.0.0-SNAPSHOT.jar", "tenant");
            add(outputStream, "BOOT-INF/lib/coco-feature-data-permission-1.0.0-SNAPSHOT.jar", "data-permission");
        }
    }

    private void writeMybatisArchive(Path archivePath) throws Exception {
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            add(outputStream, "BOOT-INF/classpath.idx", """
                    - "BOOT-INF/lib/coco-feature-web-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-feature-audit-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/coco-feature-mybatis-plus-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/mybatis-3.5.19.jar"
                    - "BOOT-INF/lib/mybatis-extra-1.0.0.jar"
                    - "BOOT-INF/lib/mybatis-plus-core-3.5.16.jar"
                    - "BOOT-INF/lib/mybatis-plus-jsqlparser-common-3.5.16.jar"
                    - "BOOT-INF/lib/mybatis-plus-spring-3.5.16.jar"
                    - "BOOT-INF/lib/mybatis-plus-spring-boot4-starter-3.5.16.jar"
                    - "BOOT-INF/lib/mybatis-spring-3.0.5.jar"
                    - "BOOT-INF/lib/spring-jdbc-7.0.0.jar"
                    """);
            add(outputStream, "BOOT-INF/layers.idx", """
                    - "dependencies":
                      - "BOOT-INF/lib/coco-feature-web-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-feature-audit-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/coco-feature-mybatis-plus-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/mybatis-3.5.19.jar"
                      - "BOOT-INF/lib/mybatis-extra-1.0.0.jar"
                      - "BOOT-INF/lib/mybatis-plus-core-3.5.16.jar"
                      - "BOOT-INF/lib/mybatis-plus-jsqlparser-common-3.5.16.jar"
                      - "BOOT-INF/lib/mybatis-plus-spring-3.5.16.jar"
                      - "BOOT-INF/lib/mybatis-plus-spring-boot4-starter-3.5.16.jar"
                      - "BOOT-INF/lib/mybatis-spring-3.0.5.jar"
                      - "BOOT-INF/lib/spring-jdbc-7.0.0.jar"
                    """);
            add(outputStream, "BOOT-INF/lib/coco-feature-web-1.0.0-SNAPSHOT.jar", "web");
            add(outputStream, "BOOT-INF/lib/coco-feature-audit-1.0.0-SNAPSHOT.jar", "audit");
            add(outputStream, "BOOT-INF/lib/coco-feature-mybatis-plus-1.0.0-SNAPSHOT.jar", "mybatis-plus");
            add(outputStream, "BOOT-INF/lib/mybatis-3.5.19.jar", "mybatis");
            add(outputStream, "BOOT-INF/lib/mybatis-extra-1.0.0.jar", "mybatis-extra");
            add(outputStream, "BOOT-INF/lib/mybatis-plus-core-3.5.16.jar", "mybatis-plus-core");
            add(outputStream, "BOOT-INF/lib/mybatis-plus-jsqlparser-common-3.5.16.jar", "mybatis-jsqlparser-common");
            add(outputStream, "BOOT-INF/lib/mybatis-plus-spring-3.5.16.jar", "mybatis-plus-spring");
            add(outputStream, "BOOT-INF/lib/mybatis-plus-spring-boot4-starter-3.5.16.jar", "mybatis-starter");
            add(outputStream, "BOOT-INF/lib/mybatis-spring-3.0.5.jar", "mybatis-spring");
            add(outputStream, "BOOT-INF/lib/spring-jdbc-7.0.0.jar", "spring-jdbc");
        }
    }

    private void writeArchiveWithStoredIndexes(Path archivePath) throws Exception {
        try (JarOutputStream outputStream = newBootArchive(archivePath)) {
            addBootRuntimeEntries(outputStream);
            addStored(outputStream, "BOOT-INF/classpath.idx", """
                    - "BOOT-INF/lib/coco-feature-mybatis-plus-1.0.0-SNAPSHOT.jar"
                    - "BOOT-INF/lib/mybatis-plus-core-RELEASE.jar"
                    - "BOOT-INF/lib/mybatis-plus-extension-v1.jar"
                    - "BOOT-INF/lib/mybatis-RELEASE.jar"
                    - "BOOT-INF/lib/mybatis-v1.jar"
                    - "BOOT-INF/lib/mybatis-extra-1.0.0.jar"
                    """);
            addStored(outputStream, "BOOT-INF/layers.idx", """
                    - "dependencies":
                      - "BOOT-INF/lib/coco-feature-mybatis-plus-1.0.0-SNAPSHOT.jar"
                      - "BOOT-INF/lib/mybatis-plus-core-RELEASE.jar"
                      - "BOOT-INF/lib/mybatis-plus-extension-v1.jar"
                      - "BOOT-INF/lib/mybatis-RELEASE.jar"
                      - "BOOT-INF/lib/mybatis-v1.jar"
                      - "BOOT-INF/lib/mybatis-extra-1.0.0.jar"
                    """);
            add(outputStream, "BOOT-INF/lib/coco-feature-mybatis-plus-1.0.0-SNAPSHOT.jar", "mybatis-plus");
            add(outputStream, "BOOT-INF/lib/mybatis-plus-core-RELEASE.jar", "mybatis-plus-core");
            add(outputStream, "BOOT-INF/lib/mybatis-plus-extension-v1.jar", "mybatis-plus-extension");
            add(outputStream, "BOOT-INF/lib/mybatis-RELEASE.jar", "mybatis-release");
            add(outputStream, "BOOT-INF/lib/mybatis-v1.jar", "mybatis-v1");
            add(outputStream, "BOOT-INF/lib/mybatis-extra-1.0.0.jar", "mybatis-extra");
            addStored(outputStream, "BOOT-INF/lib/spring-boot-4.1.0.jar", "spring-boot");
        }
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
