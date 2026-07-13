package io.github.coco.spring.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Coco Spring 依赖切换兼容性测试。
 * <p>
 * 验证内部消费者使用 canonical 坐标，同时保留 2.x 已发布 facade 的 reactor、BOM 和传递依赖契约。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-spring-boot-starter}</li>
 * </ul>
 * @author patton174
 * @since 2.0.0
 */
class CocoSpringDependencyCutoverTest {

    private static final String AUTOCONFIGURE_ARTIFACT = "coco-spring-boot-autoconfigure";

    private static final Path COMPATIBILITY_ROOT = Path.of("coco-build", "coco-compatibility");

    private static final Path EXTERNAL_CONSUMERS_ROOT = Path.of(
            "coco-support", "coco-tools", "compatibility-consumers");

    private static final Path CANONICAL_CONSUMER_POM = EXTERNAL_CONSUMERS_ROOT.resolve(
            Path.of("fixtures", "canonical", "pom.xml"));

    private static final Path LEGACY_CONSUMER_POM = EXTERNAL_CONSUMERS_ROOT.resolve(
            Path.of("fixtures", "legacy-2x", "pom.xml"));

    private static final Path PUBLIC_FQCN_CONSUMER_SOURCE = Path.of(
            "src", "main", "java", "io", "github", "coco", "consumer", "PublicFqcnConsumer.java");

    private static final List<Facade> FACADES = List.of(
            new Facade(
                    "coco-config",
                    AUTOCONFIGURE_ARTIFACT,
                    Path.of("coco-spring", "coco-spring-boot-autoconfigure"),
                    Path.of("src", "test", "java", "io", "github", "coco", "config",
                            "CocoConfigFacadeFqcnCompileContract.java")),
            new Facade(
                    "coco-feature-runtime",
                    AUTOCONFIGURE_ARTIFACT,
                    Path.of("coco-spring", "coco-spring-boot-autoconfigure"),
                    Path.of("src", "test", "java", "io", "github", "coco", "feature", "runtime",
                            "CocoFeatureRuntimeFacadeFqcnCompileContract.java")),
            new Facade(
                    "coco-feature-web",
                    "coco-web",
                    Path.of("coco-features", "coco-web"),
                    compatibilityCompileProbe("web", "CocoFeatureWebCompatibilityCompileProbe.java")),
            new Facade(
                    "coco-feature-mybatis-plus",
                    "coco-mybatis-plus",
                    Path.of("coco-features", "coco-mybatis-plus"),
                    compatibilityCompileProbe("mybatisplus",
                            "CocoFeatureMybatisPlusCompatibilityCompileProbe.java")),
            new Facade(
                    "coco-feature-audit",
                    "coco-audit",
                    Path.of("coco-features", "coco-audit"),
                    compatibilityCompileProbe("audit", "CocoFeatureAuditCompatibilityCompileProbe.java")),
            new Facade(
                    "coco-feature-security",
                    "coco-security",
                    Path.of("coco-features", "coco-security"),
                    compatibilityCompileProbe("security", "CocoFeatureSecurityCompatibilityCompileProbe.java")),
            new Facade(
                    "coco-feature-tenant",
                    "coco-tenant",
                    Path.of("coco-features", "coco-tenant"),
                    compatibilityCompileProbe("tenant", "CocoFeatureTenantCompatibilityCompileProbe.java")),
            new Facade(
                    "coco-feature-data-permission",
                    "coco-data-permission",
                    Path.of("coco-features", "coco-data-permission"),
                    compatibilityCompileProbe("datapermission",
                            "CocoFeatureDataPermissionCompatibilityCompileProbe.java")),
            new Facade(
                    "coco-feature-openapi",
                    "coco-openapi",
                    Path.of("coco-features", "coco-openapi"),
                    compatibilityCompileProbe("openapi", "CocoFeatureOpenApiCompatibilityCompileProbe.java")),
            new Facade(
                    "coco-test",
                    "coco-test-support",
                    Path.of("coco-support", "coco-test-support"),
                    Path.of("src", "test", "java", "io", "github", "coco", "test",
                            "CocoTestFacadeFqcnCompileContract.java")));

    private static final Set<String> FACADE_ARTIFACTS = FACADES.stream()
            .map(Facade::artifactId)
            .collect(Collectors.toUnmodifiableSet());

    private static final Set<String> CANONICAL_ARTIFACTS = FACADES.stream()
            .map(Facade::canonicalArtifactId)
            .collect(Collectors.toUnmodifiableSet());

    private static final List<Path> AUTOCONFIGURE_CONSUMERS = List.of(
            Path.of("coco-spring", "coco-spring-boot-starter", "pom.xml"),
            Path.of("coco-features", "coco-audit", "pom.xml"),
            Path.of("coco-features", "coco-feature-codegen", "pom.xml"),
            Path.of("coco-features", "coco-data-permission", "pom.xml"),
            Path.of("coco-features", "coco-mybatis-plus", "pom.xml"),
            Path.of("coco-features", "coco-openapi", "pom.xml"),
            Path.of("coco-features", "coco-security", "pom.xml"),
            Path.of("coco-features", "coco-tenant", "pom.xml"),
            Path.of("coco-features", "coco-web", "pom.xml"));

    @Test
    void keepsPublishedFacadesInReactorsAndDependencyManagement() throws Exception {
        Path projectRoot = projectRoot();
        Path rootPom = projectRoot.resolve("pom.xml");
        Path bomPom = projectRoot.resolve("coco-build/coco-dependencies/pom.xml");

        Set<Path> rootModules = reactorModulePaths(rootPom);
        for (Facade facade : FACADES) {
            assertThat(rootModules)
                    .as("reactor module for %s", facade.artifactId())
                    .contains(projectRoot.resolve(facade.modulePath()).normalize());
        }
        assertThat(dependencyManagementArtifactIds(readPom(rootPom)))
                .containsAll(FACADE_ARTIFACTS)
                .containsAll(CANONICAL_ARTIFACTS);
        assertThat(dependencyManagementArtifactIds(readPom(bomPom)))
                .containsAll(FACADE_ARTIFACTS)
                .containsAll(CANONICAL_ARTIFACTS);
    }

    @Test
    void keepsFacadesSourceFreeWithSingleCanonicalDependency() throws Exception {
        Path projectRoot = projectRoot();

        for (Facade facade : FACADES) {
            Path module = projectRoot.resolve(facade.modulePath());
            Path pom = module.resolve("pom.xml");
            assertThat(pom).isRegularFile();
            assertThat(directDependencyArtifactIds(readPom(pom)))
                    .as("direct dependencies in %s", facade.modulePath())
                    .isEqualTo(Set.of(facade.canonicalArtifactId()));
            assertThat(regularFiles(module.resolve("src/main")))
                    .as("published sources and registration resources in %s", facade.modulePath())
                    .isEmpty();
            assertThat(module.resolve(facade.compileProbe())).isRegularFile();
            assertThat(projectRoot.resolve(facade.canonicalModulePath()).resolve("pom.xml"))
                    .as("canonical module for %s", facade.artifactId())
                    .isRegularFile();
        }
    }

    @Test
    void rewiresInternalConsumersAwayFromPublishedFacades() throws Exception {
        Path projectRoot = projectRoot();

        for (Path relativePom : AUTOCONFIGURE_CONSUMERS) {
            Path pom = projectRoot.resolve(relativePom);
            assertThat(pom).isRegularFile();
            assertThat(directDependencyArtifactIds(readPom(pom)))
                    .as("direct dependencies in %s", relativePom)
                    .contains(AUTOCONFIGURE_ARTIFACT)
                    .doesNotContainAnyElementsOf(FACADE_ARTIFACTS);
        }

        Set<Path> facadePoms = FACADES.stream()
                .map(Facade::modulePath)
                .map(projectRoot::resolve)
                .map(path -> path.resolve("pom.xml").normalize())
                .collect(Collectors.toUnmodifiableSet());
        Path externalConsumersRoot = projectRoot.resolve(EXTERNAL_CONSUMERS_ROOT).normalize();
        for (Path pom : sourcePoms(projectRoot)) {
            if (!facadePoms.contains(pom) && !pom.startsWith(externalConsumersRoot)) {
                assertThat(directDependencyArtifactIds(readPom(pom)))
                        .as("direct dependencies in %s", projectRoot.relativize(pom))
                        .doesNotContainAnyElementsOf(FACADE_ARTIFACTS);
            }
        }

    }

    @Test
    void separatesCanonicalAndLegacyExternalConsumerCoordinates() throws Exception {
        Path projectRoot = projectRoot();
        Path canonicalPom = projectRoot.resolve(CANONICAL_CONSUMER_POM);
        Path legacyPom = projectRoot.resolve(LEGACY_CONSUMER_POM);

        assertThat(directDependencyArtifactIds(readPom(canonicalPom)))
                .as("canonical external consumer dependencies")
                .isEqualTo(CANONICAL_ARTIFACTS)
                .doesNotContainAnyElementsOf(FACADE_ARTIFACTS);
        assertThat(directDependencyArtifactIds(readPom(legacyPom)))
                .as("legacy 2.x external consumer dependencies")
                .isEqualTo(FACADE_ARTIFACTS)
                .doesNotContainAnyElementsOf(CANONICAL_ARTIFACTS);

        Path canonicalSource = canonicalPom.getParent().resolve(PUBLIC_FQCN_CONSUMER_SOURCE);
        Path legacySource = legacyPom.getParent().resolve(PUBLIC_FQCN_CONSUMER_SOURCE);
        assertThat(canonicalSource).isRegularFile();
        assertThat(legacySource).isRegularFile();
        assertThat(Files.readString(canonicalSource)).isEqualTo(Files.readString(legacySource));
    }

    private static Path compatibilityCompileProbe(String packageName, String className) {
        return Path.of("src", "test", "java", "io", "github", "coco", "compatibility", packageName,
                className);
    }

    private static Path projectRoot() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isDirectory(candidate.resolve("coco-spring"))
                    && Files.isDirectory(candidate.resolve("coco-features"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Unable to locate the Coco Framework repository root");
    }

    private static Set<Path> reactorModulePaths(Path pom) throws Exception {
        Element modules = directChild(readPom(pom).getDocumentElement(), "modules");
        if (modules == null) {
            return Set.of();
        }
        return directChildren(modules, "module").stream()
                .map(Node::getTextContent)
                .map(String::trim)
                .map(pom.getParent()::resolve)
                .map(Path::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static List<Path> sourcePoms(Path projectRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> "pom.xml".equals(path.getFileName().toString()))
                    .filter(path -> isSourcePath(projectRoot.relativize(path)))
                    .map(Path::normalize)
                    .sorted()
                    .toList();
        }
    }

    private static boolean isSourcePath(Path relativePath) {
        for (Path segment : relativePath) {
            String name = segment.toString();
            if ("target".equals(name) || name.startsWith(".")) {
                return false;
            }
        }
        return true;
    }

    private static List<Path> regularFiles(Path root) throws IOException {
        if (Files.notExists(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private static Document readPom(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        factory.setXIncludeAware(false);
        try (InputStream input = Files.newInputStream(pom)) {
            return factory.newDocumentBuilder().parse(input);
        }
    }

    private static Set<String> dependencyManagementArtifactIds(Document document) {
        Element dependencyManagement = directChild(document.getDocumentElement(), "dependencyManagement");
        return dependencyManagement == null
                ? Set.of()
                : dependencyArtifactIds(directChild(dependencyManagement, "dependencies"));
    }

    private static Set<String> directDependencyArtifactIds(Document document) {
        return dependencyArtifactIds(directChild(document.getDocumentElement(), "dependencies"));
    }

    private static Set<String> dependencyArtifactIds(Element dependencies) {
        if (dependencies == null) {
            return Set.of();
        }
        Set<String> artifactIds = new LinkedHashSet<>();
        for (Element dependency : directChildren(dependencies, "dependency")) {
            Element artifactId = directChild(dependency, "artifactId");
            if (artifactId != null) {
                artifactIds.add(artifactId.getTextContent().trim());
            }
        }
        return Set.copyOf(artifactIds);
    }

    private static List<Element> directChildren(Element parent, String localName) {
        List<Element> elements = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && localName.equals(element.getLocalName())) {
                elements.add(element);
            }
        }
        return List.copyOf(elements);
    }

    private static Element directChild(Element parent, String localName) {
        return directChildren(parent, localName).stream().findFirst().orElse(null);
    }

    private record Facade(String artifactId, String canonicalArtifactId, Path canonicalModulePath,
            Path compileProbe) {

        private Path modulePath() {
            return COMPATIBILITY_ROOT.resolve(this.artifactId);
        }

    }
}
