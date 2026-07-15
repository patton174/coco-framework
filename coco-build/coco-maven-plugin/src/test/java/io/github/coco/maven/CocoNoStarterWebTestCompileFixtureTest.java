package io.github.coco.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 无 Starter Web 功能真实 Maven 生命周期测试。
 * <p>
 * 验证业务项目只声明 {@code coco-api} 时，{@code coco:features} 在 {@code process-classes}
 * 注入的 Web 功能完整编译闭包仍会被后续 {@code test-compile} 使用。
 * </p>
 * @author patton174
 * @since 1.0.0
 */
class CocoNoStarterWebTestCompileFixtureTest {

    private static final String DISABLED_FEATURES =
            "audit,codegen,data-permission,mybatis-plus,openapi,security,tenant";

    @TempDir
    Path tempDir;

    @Test
    void featureAssemblyRefreshesNoStarterClasspathAndPreservesTheStarterClasspath() throws Exception {
        Path repositoryRoot = Path.of("../..").toAbsolutePath().normalize();
        Path localRepository = this.tempDir.resolve("repository");
        Path settings = writeFixtureSettings(this.tempDir.resolve("settings.xml"));
        installFixturePrerequisites(repositoryRoot, localRepository, settings);
        installDumpPluginFixture(repositoryRoot, localRepository, settings);

        Path fixtureSource = repositoryRoot.resolve(
                "coco-build/coco-maven-plugin/src/test/resources/fixtures/no-starter-web-test-compile");
        Path fixture = this.tempDir.resolve("no-starter-fixture");
        copyDirectory(fixtureSource, fixture);
        String output = runMaven(fixture, localRepository, settings,
                "-Dcoco.features.disabled=" + DISABLED_FEATURES, "test-compile");

        assertThat(output).contains("Coco feature manifest generated with 1 enabled features.");
        String before = Files.readString(fixture.resolve("target/project-before.txt"));
        String after = Files.readString(fixture.resolve("target/project-after.txt"));
        assertThat(before)
                .contains("coco-api")
                .doesNotContain("coco-web", "spring-web");
        assertThat(after)
                .contains("coco-api", "coco-web", "spring-web")
                .contains("dependencies=", "artifacts=", "dependencyArtifacts=",
                        "compileClasspath=", "testClasspath=");
        assertThat(fixture.resolve(
                "target/test-classes/io/github/coco/fixture/WebFeatureClasspathProbe.class")).isRegularFile();

        Path starterFixture = this.tempDir.resolve("starter-fixture");
        copyDirectory(fixtureSource, starterFixture);
        String starterOutput = runMaven(starterFixture, localRepository, settings, "-Pstarter", "test-compile");
        String starterBefore = Files.readString(starterFixture.resolve("target/project-before.txt"));
        String starterAfter = Files.readString(starterFixture.resolve("target/project-after.txt"));
        assertThat(starterOutput).contains("Coco feature manifest generated with 8 enabled features.");
        assertThat(jarCount(starterBefore, "testClasspath=")).isEqualTo(109);
        assertThat(jarCount(starterAfter, "testClasspath=")).isEqualTo(109);
        assertThat(line(starterAfter, "dependencies="))
                .contains("coco-api", "coco-spring-boot-starter")
                .doesNotContain("coco-web");
    }

    private void installFixturePrerequisites(Path repositoryRoot, Path localRepository, Path settings)
            throws Exception {
        runMaven(repositoryRoot, localRepository, settings,
                "-Dmaven.test.skip=true",
                "-Dgpg.skip=true",
                "-pl",
                ":coco-parent,:coco-dependencies,:coco-maven-plugin,:coco-spring-boot-starter",
                "-am",
                "install");
    }

    private void installDumpPluginFixture(Path repositoryRoot, Path localRepository, Path settings)
            throws Exception {
        Path source = repositoryRoot.resolve(
                "coco-build/coco-maven-plugin/src/test/resources/fixtures/project-view-dump-plugin");
        Path fixture = this.tempDir.resolve("project-view-dump-plugin");
        copyDirectory(source, fixture);
        runMaven(fixture, localRepository, settings, "-DskipTests", "install");
    }

    private Path writeFixtureSettings(Path settings) throws IOException {
        String cacheUrl = Path.of(System.getProperty("user.home"), ".m2", "repository")
                .toUri().toASCIIString();
        Files.writeString(settings, """
                <settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0
                                              https://maven.apache.org/xsd/settings-1.2.0.xsd">
                  <profiles>
                    <profile>
                      <id>fixture-repositories</id>
                      <repositories>
                        <repository>
                          <id>fixture-local-cache</id>
                          <url>%s</url>
                          <releases><enabled>true</enabled></releases>
                          <snapshots><enabled>true</enabled><updatePolicy>never</updatePolicy></snapshots>
                        </repository>
                        <repository>
                          <id>central</id>
                          <url>https://repo.maven.apache.org/maven2</url>
                          <releases><enabled>true</enabled></releases>
                          <snapshots><enabled>false</enabled></snapshots>
                        </repository>
                      </repositories>
                      <pluginRepositories>
                        <pluginRepository>
                          <id>fixture-local-cache</id>
                          <url>%s</url>
                          <releases><enabled>true</enabled></releases>
                          <snapshots><enabled>true</enabled><updatePolicy>never</updatePolicy></snapshots>
                        </pluginRepository>
                        <pluginRepository>
                          <id>central</id>
                          <url>https://repo.maven.apache.org/maven2</url>
                          <releases><enabled>true</enabled></releases>
                          <snapshots><enabled>false</enabled></snapshots>
                        </pluginRepository>
                      </pluginRepositories>
                    </profile>
                  </profiles>
                  <activeProfiles>
                    <activeProfile>fixture-repositories</activeProfile>
                  </activeProfiles>
                </settings>
                """.formatted(cacheUrl, cacheUrl), StandardCharsets.UTF_8);
        return settings;
    }

    private String runMaven(Path workingDirectory, Path localRepository, Path settings, String... arguments)
            throws Exception {
        List<String> command = new ArrayList<>();
        command.add(mavenExecutable());
        command.add("-B");
        command.add("-ntp");
        command.add("-s");
        command.add(settings.toString());
        command.add("-Dmaven.repo.local=" + localRepository);
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();
        CompletableFuture<byte[]> outputFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return process.getInputStream().readAllBytes();
            }
            catch (IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        });
        boolean finished = process.waitFor(600, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
        String output = new String(outputFuture.get(30, TimeUnit.SECONDS), StandardCharsets.UTF_8);
        assertThat(finished).as(output).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        return output;
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relativePath = source.relativize(path);
                if (relativePath.getNameCount() > 0
                        && ("target".equals(relativePath.getName(0).toString())
                                || ".flattened-pom.xml".equals(relativePath.toString()))) {
                    continue;
                }
                Path destination = target.resolve(relativePath);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                }
                else {
                    Files.copy(path, destination);
                }
            }
        }
    }

    private static long jarCount(String dump, String prefix) {
        String classpath = line(dump, prefix);
        return classpath.split("\\.jar", -1).length - 1L;
    }

    private static String line(String dump, String prefix) {
        return dump.lines()
                .filter(candidate -> candidate.startsWith(prefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing dump line: " + prefix));
    }

    private static String mavenExecutable() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? "mvn.cmd"
                : "mvn";
    }
}
