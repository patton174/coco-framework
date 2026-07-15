package io.github.coco.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.tools.ToolProvider;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.model.CocoFeatureManifestLoader;
import io.github.coco.feature.model.CocoFeaturePlan;
import io.github.coco.feature.model.CocoFeatureSelection;
import io.github.coco.feature.model.StandardCocoFeatures;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.RepositorySystemSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.loader.tools.Library;
import org.springframework.boot.loader.tools.LibraryScope;
import org.springframework.boot.loader.tools.Repackager;

/**
 * 功能依赖闭包的真实 Spring Boot 可执行包回归。
 *
 * @author patton174
 * @since 2.0.0
 */
class CocoFeatureBootRuntimeFixtureTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvedTransitiveDependencyIsPackagedAndLoadableAtBootRuntime() throws Exception {
        Path featureJar = emptyJar(this.tempDir.resolve("coco-web-1.0.0-SNAPSHOT.jar"));
        Path transitiveJar = compileJar("com.example.fixture.TransitiveMarker", """
                package com.example.fixture;

                public final class TransitiveMarker {
                    public static String message() {
                        return "transitive";
                    }
                }
                """, this.tempDir.resolve("feature-runtime-3.2.1.jar"));
        Path applicationJar = compileJar("com.example.fixture.BootFixtureApplication", """
                package com.example.fixture;

                public final class BootFixtureApplication {
                    public static void main(String[] args) throws Exception {
                        Class<?> marker = Class.forName("com.example.fixture.TransitiveMarker");
                        Object message = marker.getMethod("message").invoke(null);
                        System.out.println("BOOT_FIXTURE_OK:" + message);
                    }
                }
                """, this.tempDir.resolve("fixture-app.jar"));

        MavenProject project = project();
        CocoFeaturesMojo mojo = mojo(project, featureJar, transitiveJar);
        mojo.applyFeatureDependencies(planWithOnly(CocoFeature.WEB));

        Repackager repackager = new Repackager(applicationJar.toFile());
        repackager.setMainClass("com.example.fixture.BootFixtureApplication");
        repackager.setBackupSource(false);
        repackager.repackage(callback -> {
            for (Artifact artifact : project.getArtifacts()) {
                callback.library(new Library(artifact.getFile(), LibraryScope.RUNTIME));
            }
        });

        try (JarFile bootJar = new JarFile(applicationJar.toFile())) {
            Set<String> entries = bootJar.stream()
                    .map(JarEntry::getName)
                    .collect(java.util.stream.Collectors.toSet());
            assertThat(entries).contains(
                    "BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar",
                    "BOOT-INF/lib/feature-runtime-3.2.1.jar");
        }

        Process process = new ProcessBuilder(javaExecutable(), "-jar", applicationJar.toString())
                .redirectErrorStream(true)
                .start();
        boolean completed = process.waitFor(20, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
        }
        assertThat(completed).as("Spring Boot fixture process completed").isTrue();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(output).contains("BOOT_FIXTURE_OK:transitive");
    }

    @Test
    void pruneKeepsFullyExecutableBootLaunchScriptAndRuntime() throws Exception {
        Path applicationJar = compileJar("com.example.fixture.PrunedBootApplication", """
                package com.example.fixture;

                public final class PrunedBootApplication {
                    public static void main(String[] args) {
                        System.out.println("PRUNED_BOOT_FIXTURE_OK");
                    }
                }
                """, this.tempDir.resolve("target/demo.jar"));
        Path webJar = cocoFeatureJar("coco-web", this.tempDir.resolve("coco-web-1.0.0-SNAPSHOT.jar"));
        Path auditJar = cocoFeatureJar("coco-audit", this.tempDir.resolve("coco-audit-1.0.0-SNAPSHOT.jar"));
        Repackager repackager = new Repackager(applicationJar.toFile());
        repackager.setMainClass("com.example.fixture.PrunedBootApplication");
        repackager.setBackupSource(false);
        repackager.repackage(callback -> {
            callback.library(new Library(webJar.toFile(), LibraryScope.RUNTIME));
            callback.library(new Library(auditJar.toFile(), LibraryScope.RUNTIME));
        });

        byte[] launchScript = ("#!/bin/sh\n# PK magic decoy: " + (char) 3 + (char) 4 + "\nexec \""
                + javaExecutable() + "\" -jar \"$0\" \"$@\"\n").getBytes(StandardCharsets.ISO_8859_1);
        Files.write(applicationJar, concat(launchScript, Files.readAllBytes(applicationJar)));
        CocoExecutableArchive.relocateOffsets(applicationJar, launchScript.length);
        setExecutablePermissionsWhenSupported(applicationJar);

        Path classesDirectory = Files.createDirectories(this.tempDir.resolve("target/classes"));
        writeManifest(classesDirectory, Set.of(CocoFeature.WEB));
        MavenProject project = project();
        CocoPackagePruneMojo mojo = new CocoPackagePruneMojo();
        set(mojo, "project", project);
        set(mojo, "classesDirectory", classesDirectory.toFile());
        set(mojo, "buildDirectory", this.tempDir.resolve("target").toFile());
        set(mojo, "finalName", "demo");

        mojo.execute();

        byte[] pruned = Files.readAllBytes(applicationJar);
        assertThat(pruned).startsWith(launchScript);
        try (JarFile jarFile = new JarFile(applicationJar.toFile())) {
            assertThat(jarFile.getEntry("BOOT-INF/lib/coco-web-1.0.0-SNAPSHOT.jar")).isNull();
            assertThat(jarFile.getEntry("BOOT-INF/lib/coco-audit-1.0.0-SNAPSHOT.jar")).isNotNull();
        }
        assertProcessSucceeds(new ProcessBuilder(javaExecutable(), "-jar", applicationJar.toString()),
                "PRUNED_BOOT_FIXTURE_OK");
        if (Files.getFileAttributeView(applicationJar, PosixFileAttributeView.class) != null) {
            assertThat(Files.getPosixFilePermissions(applicationJar)).contains(PosixFilePermission.OWNER_EXECUTE);
            assertProcessSucceeds(new ProcessBuilder(applicationJar.toString()), "PRUNED_BOOT_FIXTURE_OK");
        }
    }

    private CocoFeaturesMojo mojo(MavenProject project, Path featureJar, Path transitiveJar) throws Exception {
        CocoFeaturesMojo mojo = new CocoFeaturesMojo();
        set(mojo, "project", project);
        set(mojo, "featureGroupId", "io.github.patton174");
        set(mojo, "featureVersion", "1.0.0-SNAPSHOT");
        set(mojo, "projectDependenciesResolver", projectDependenciesResolver(featureJar, transitiveJar));
        set(mojo, "repositorySystemSession", repositorySystemSession());
        return mojo;
    }

    private ProjectDependenciesResolver projectDependenciesResolver(Path featureJar, Path transitiveJar) {
        return request -> {
            org.eclipse.aether.graph.Dependency direct = new org.eclipse.aether.graph.Dependency(
                    new org.eclipse.aether.artifact.DefaultArtifact(
                            "io.github.patton174", "coco-web", "jar", "1.0.0-SNAPSHOT")
                            .setFile(featureJar.toFile()),
                    Artifact.SCOPE_COMPILE);
            org.eclipse.aether.graph.Dependency transitive = new org.eclipse.aether.graph.Dependency(
                    new org.eclipse.aether.artifact.DefaultArtifact(
                            "com.example", "feature-runtime", "jar", "3.2.1")
                            .setFile(transitiveJar.toFile()),
                    Artifact.SCOPE_RUNTIME);
            return dependencyResolutionResult(List.of(direct, transitive));
        };
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
                    default -> objectMethod(proxy, method.getName(), arguments);
                });
    }

    private RepositorySystemSession repositorySystemSession() {
        return (RepositorySystemSession) Proxy.newProxyInstance(
                RepositorySystemSession.class.getClassLoader(), new Class<?>[] {RepositorySystemSession.class},
                (proxy, method, arguments) -> objectMethod(proxy, method.getName(), arguments));
    }

    private Object objectMethod(Object proxy, String methodName, Object[] arguments) {
        return switch (methodName) {
            case "toString" -> proxy.getClass().getInterfaces()[0].getSimpleName() + "Proxy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            default -> throw new UnsupportedOperationException(methodName);
        };
    }

    private MavenProject project() {
        Model model = new Model();
        model.setGroupId("com.example");
        model.setArtifactId("boot-fixture");
        model.setVersion("99.7.3");
        Build build = new Build();
        build.setDirectory(this.tempDir.resolve("target").toString());
        build.setOutputDirectory(this.tempDir.resolve("target/classes").toString());
        model.setBuild(build);
        build.setFinalName("demo");
        return new MavenProject(model);
    }

    private void writeManifest(Path classesDirectory, Set<CocoFeature> disabledFeatures) throws Exception {
        Path manifest = classesDirectory.resolve(CocoFeatureManifestLoader.MANIFEST_LOCATION);
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest,
                CocoFeatureManifestLoader.write(StandardCocoFeatures.toManifest(
                        StandardCocoFeatures.resolve(CocoFeatureSelection.ofDisabled(disabledFeatures)), "test")),
                StandardCharsets.UTF_8);
    }

    private CocoFeaturePlan planWithOnly(CocoFeature feature) {
        EnumSet<CocoFeature> disabled = EnumSet.allOf(CocoFeature.class);
        disabled.remove(feature);
        return new CocoFeaturePlan(Set.of(feature), disabled, StandardCocoFeatures.all());
    }

    private Path compileJar(String className, String source, Path jarPath) throws Exception {
        Files.createDirectories(jarPath.getParent());
        Path sourceRoot = Files.createDirectories(this.tempDir.resolve(jarPath.getFileName() + "-src"));
        Path classes = Files.createDirectories(this.tempDir.resolve(jarPath.getFileName() + "-classes"));
        Path sourceFile = sourceRoot.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        int result = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "-d", classes.toString(), sourceFile.toString());
        assertThat(result).isZero();
        writeClassesJar(classes, jarPath);
        return jarPath;
    }

    private void writeClassesJar(Path classes, Path jarPath) throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath), manifest);
                var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String name = classes.relativize(file).toString().replace('\\', '/');
                output.putNextEntry(new JarEntry(name));
                Files.copy(file, output);
                output.closeEntry();
            }
        }
    }

    private Path emptyJar(Path jarPath) throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            // Valid empty feature JAR.
        }
        return jarPath;
    }

    private Path cocoFeatureJar(String artifactId, Path jarPath) throws Exception {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            JarEntry entry = new JarEntry("META-INF/maven/io.github.patton174/" + artifactId + "/pom.properties");
            output.putNextEntry(entry);
            output.write(("groupId=io.github.patton174\nartifactId=" + artifactId
                    + "\nversion=1.0.0-SNAPSHOT\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jarPath;
    }

    private void setExecutablePermissionsWhenSupported(Path archivePath) throws Exception {
        if (Files.getFileAttributeView(archivePath, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(archivePath, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE));
        }
    }

    private void assertProcessSucceeds(ProcessBuilder processBuilder, String marker) throws Exception {
        Process process = processBuilder.redirectErrorStream(true).start();
        boolean completed = process.waitFor(20, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(completed).as(output).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(output).contains(marker);
    }

    private byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private String javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
