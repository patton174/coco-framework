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
 * 验证内部消费者直接依赖自动配置模块，同时保留 2.x 已发布 facade 的 reactor、BOM 和传递依赖契约。
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

    private static final List<Facade> FACADES = List.of(
            new Facade(
                    "coco-config",
                    Path.of("coco-spring", "coco-config"),
                    Path.of("src", "test", "java", "io", "github", "coco", "config",
                            "CocoConfigFacadeFqcnCompileContract.java")),
            new Facade(
                    "coco-feature-runtime",
                    Path.of("coco-features", "coco-feature-runtime"),
                    Path.of("src", "test", "java", "io", "github", "coco", "feature", "runtime",
                            "CocoFeatureRuntimeFacadeFqcnCompileContract.java")));

    private static final Set<String> FACADE_ARTIFACTS = FACADES.stream()
            .map(Facade::artifactId)
            .collect(Collectors.toUnmodifiableSet());

    private static final List<Path> AUTOCONFIGURE_CONSUMERS = List.of(
            Path.of("coco-spring", "coco-spring-boot-starter", "pom.xml"),
            Path.of("coco-features", "coco-feature-data-permission", "pom.xml"),
            Path.of("coco-features", "coco-feature-mybatis-plus", "pom.xml"),
            Path.of("coco-features", "coco-feature-openapi", "pom.xml"),
            Path.of("coco-features", "coco-feature-security", "pom.xml"),
            Path.of("coco-features", "coco-feature-tenant", "pom.xml"),
            Path.of("coco-features", "coco-feature-web", "pom.xml"));

    @Test
    void keepsPublishedFacadesInReactorsAndDependencyManagement() throws Exception {
        Path projectRoot = projectRoot();
        Path rootPom = projectRoot.resolve("pom.xml");
        Path featuresPom = projectRoot.resolve("coco-features/pom.xml");
        Path bomPom = projectRoot.resolve("coco-build/coco-dependencies/pom.xml");

        assertThat(reactorModulePaths(rootPom))
                .contains(projectRoot.resolve("coco-spring/coco-config").normalize());
        assertThat(reactorModulePaths(featuresPom))
                .contains(projectRoot.resolve("coco-features/coco-feature-runtime").normalize());
        assertThat(dependencyManagementArtifactIds(readPom(rootPom))).containsAll(FACADE_ARTIFACTS);
        assertThat(dependencyManagementArtifactIds(readPom(bomPom))).containsAll(FACADE_ARTIFACTS);
    }

    @Test
    void keepsFacadesSourceFreeWithSingleAutoconfigureDependency() throws Exception {
        Path projectRoot = projectRoot();

        for (Facade facade : FACADES) {
            Path module = projectRoot.resolve(facade.modulePath());
            Path pom = module.resolve("pom.xml");
            assertThat(pom).isRegularFile();
            assertThat(directDependencyArtifactIds(readPom(pom)))
                    .as("direct dependencies in %s", facade.modulePath())
                    .isEqualTo(Set.of(AUTOCONFIGURE_ARTIFACT));
            assertThat(regularFiles(module.resolve("src/main")))
                    .as("published sources and registration resources in %s", facade.modulePath())
                    .isEmpty();
            assertThat(module.resolve(facade.compileProbe())).isRegularFile();
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

    private record Facade(String artifactId, Path modulePath, Path compileProbe) {
    }
}
