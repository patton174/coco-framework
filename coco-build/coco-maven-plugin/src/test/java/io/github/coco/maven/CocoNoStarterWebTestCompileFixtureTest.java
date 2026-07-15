package io.github.coco.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

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

    private static final String PLUGIN_GROUP_ID = "io.github.patton174";
    private static final String PLUGIN_ARTIFACT_ID = "coco-maven-plugin";
    private static final String DISABLED_FEATURES =
            "audit,codegen,data-permission,mybatis-plus,openapi,security,tenant";
    private static final Pattern FEATURE_EXECUTION = Pattern.compile(
            "coco-maven-plugin:([^:\\s]+):features \\(coco-feature-assembly\\)");

    @TempDir
    Path tempDir;

    @Test
    void featureAssemblyRefreshesNoStarterClasspathAndPreservesTheStarterClasspath() throws Exception {
        Path repositoryRoot = Path.of("../..").toAbsolutePath().normalize();
        String projectVersion = requiredSystemProperty("coco.fixture.projectVersion");
        String revision = requiredSystemProperty("coco.fixture.revision");
        Path workspacePluginArtifact = Path.of(requiredSystemProperty("coco.fixture.pluginArtifact"));
        Path outerLocalRepository = Path.of(requiredSystemProperty("coco.fixture.outerLocalRepository"));
        assertThat(projectVersion).isEqualTo(revision);
        Path localRepository = this.tempDir.resolve("repository");
        Path settings = writeFixtureSettings(this.tempDir.resolve("settings.xml"), outerLocalRepository);
        installFixturePrerequisites(repositoryRoot, localRepository, settings, revision);
        installDumpPluginFixture(repositoryRoot, localRepository, settings);

        Path fixtureSource = repositoryRoot.resolve(
                "coco-build/coco-maven-plugin/src/test/resources/fixtures/no-starter-web-test-compile");
        Path fixture = this.tempDir.resolve("no-starter-fixture");
        copyDirectory(fixtureSource, fixture);
        configureFixtureVersions(fixture.resolve("pom.xml"), projectVersion, projectVersion);
        String output = runMaven(fixture, localRepository, settings,
                "-Drevision=" + revision,
                "-Dcoco.features.disabled=" + DISABLED_FEATURES,
                "test-compile");

        assertThat(output).contains("Coco feature manifest generated with 1 enabled features.");
        assertExecutedPluginMatchesWorkspace(output, projectVersion, workspacePluginArtifact, localRepository);
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
        configureFixtureVersions(starterFixture.resolve("pom.xml"), projectVersion, projectVersion);
        String starterOutput = runMaven(starterFixture, localRepository, settings,
                "-Drevision=" + revision,
                "-Pstarter",
                "test-compile");
        String starterBefore = Files.readString(starterFixture.resolve("target/project-before.txt"));
        String starterAfter = Files.readString(starterFixture.resolve("target/project-after.txt"));
        assertThat(starterOutput).contains("Coco feature manifest generated with 8 enabled features.");
        assertExecutedPluginMatchesWorkspace(starterOutput, projectVersion, workspacePluginArtifact, localRepository);
        assertThat(jarCount(starterBefore, "testClasspath=")).isEqualTo(109);
        assertThat(jarCount(starterAfter, "testClasspath=")).isEqualTo(109);
        assertThat(line(starterAfter, "dependencies="))
                .contains("coco-api", "coco-spring-boot-starter")
                .doesNotContain("coco-web");

        Path wrongVersionFixture = this.tempDir.resolve("wrong-version-fixture");
        copyDirectory(fixtureSource, wrongVersionFixture);
        String wrongVersion = "0.0.0-coco-fixture-missing";
        configureFixtureVersions(wrongVersionFixture.resolve("pom.xml"), projectVersion, wrongVersion);
        MavenResult wrongVersionResult = invokeMaven(wrongVersionFixture, localRepository, settings,
                "-Drevision=" + revision,
                "-Dcoco.features.disabled=" + DISABLED_FEATURES,
                "test-compile");
        assertThat(wrongVersionResult.finished()).as(wrongVersionResult.output()).isTrue();
        assertThat(wrongVersionResult.exitCode()).as(wrongVersionResult.output()).isNotZero();
        assertThat(wrongVersionResult.output())
                .contains(PLUGIN_GROUP_ID + ":" + PLUGIN_ARTIFACT_ID + ":" + wrongVersion);
    }

    private void installFixturePrerequisites(Path repositoryRoot, Path localRepository, Path settings, String revision)
            throws Exception {
        runMaven(repositoryRoot, localRepository, settings,
                "-Drevision=" + revision,
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

    private Path writeFixtureSettings(Path settings, Path outerLocalRepository) throws IOException {
        String cacheUrl = outerLocalRepository.toAbsolutePath().normalize().toUri().toASCIIString();
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

    private void assertExecutedPluginMatchesWorkspace(String output, String expectedVersion,
            Path workspacePluginArtifact, Path localRepository) throws Exception {
        List<String> executedVersions = FEATURE_EXECUTION.matcher(output).results()
                .map(result -> result.group(1))
                .distinct()
                .toList();
        assertThat(executedVersions).as(output).containsExactly(expectedVersion);

        String actualVersion = executedVersions.get(0);
        Path installedPluginArtifact = localRepository.resolve(Path.of(
                PLUGIN_GROUP_ID.replace('.', '/'), PLUGIN_ARTIFACT_ID, actualVersion,
                PLUGIN_ARTIFACT_ID + "-" + actualVersion + ".jar"));
        assertThat(workspacePluginArtifact).isRegularFile();
        assertThat(installedPluginArtifact).isRegularFile();
        byte[] workspaceHash = sha256(workspacePluginArtifact);
        byte[] installedHash = sha256(installedPluginArtifact);
        assertThat(installedHash).isEqualTo(workspaceHash);
        System.out.println("Verified executed plugin " + PLUGIN_GROUP_ID + ":" + PLUGIN_ARTIFACT_ID + ":"
                + actualVersion + " SHA-256=" + HexFormat.of().formatHex(installedHash));
    }

    private static byte[] sha256(Path file) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
    }

    private static void configureFixtureVersions(Path pom, String parentVersion, String pluginVersion)
            throws Exception {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = documentBuilderFactory.newDocumentBuilder().parse(pom.toFile());
        Element project = document.getDocumentElement();
        directChild(directChild(project, "parent"), "version").setTextContent(parentVersion);

        Element plugins = directChild(directChild(project, "build"), "plugins");
        Element cocoPlugin = null;
        for (Node node = plugins.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element plugin && "plugin".equals(plugin.getTagName())
                    && PLUGIN_ARTIFACT_ID.equals(directChild(plugin, "artifactId").getTextContent().trim())) {
                cocoPlugin = plugin;
                break;
            }
        }
        if (cocoPlugin == null) {
            throw new IllegalStateException("Fixture Coco Maven plugin declaration is missing: " + pom);
        }
        directChild(cocoPlugin, "version").setTextContent(pluginVersion);

        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(document), new StreamResult(pom.toFile()));
    }

    private static Element directChild(Element parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getTagName())) {
                return element;
            }
        }
        throw new IllegalStateException("Missing <" + name + "> below <" + parent.getTagName() + ">");
    }

    private String runMaven(Path workingDirectory, Path localRepository, Path settings, String... arguments)
            throws Exception {
        MavenResult result = invokeMaven(workingDirectory, localRepository, settings, arguments);
        assertThat(result.finished()).as(result.output()).isTrue();
        assertThat(result.exitCode()).as(result.output()).isZero();
        return result.output();
    }

    private MavenResult invokeMaven(Path workingDirectory, Path localRepository, Path settings, String... arguments)
            throws Exception {
        List<String> command = new ArrayList<>();
        command.add(mavenExecutable());
        command.add("-B");
        command.add("-ntp");
        command.add("-Dstyle.color=never");
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
        return new MavenResult(finished, process.exitValue(), output);
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

    private static String requiredSystemProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required system property: " + name);
        }
        return value.trim();
    }

    private record MavenResult(boolean finished, int exitCode, String output) {
    }
}
