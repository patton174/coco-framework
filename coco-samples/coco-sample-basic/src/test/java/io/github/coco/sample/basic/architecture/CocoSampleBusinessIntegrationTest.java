package io.github.coco.sample.basic.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Coco 示例项目业务接入形态测试。
 * <p>
 * 验证示例项目按照真实业务项目方式接入发布后的 Coco Parent 与 Starter，而不是依赖源码仓库中的模块相对路径。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-sample-basic}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoSampleBusinessIntegrationTest {

    /**
     * <p>
     * 示例项目应该继承已发布的 Coco Parent，不应该通过 {@code relativePath} 绑定源码仓库中的父 POM。
     * </p>
     * @throws Exception POM 解析失败时抛出
     */
    @Test
    void usesPublishedCocoParent() throws Exception {
        Element parent = directChild(readPom().getDocumentElement(), "parent");

        assertEquals("io.github.patton174", childText(parent, "groupId"));
        assertEquals("coco-parent", childText(parent, "artifactId"));
        assertEquals("1.0.0-SNAPSHOT", childText(parent, "version"));
        assertTrue(childText(parent, "relativePath").isBlank());
    }

    /**
     * <p>
     * 示例项目的运行期依赖只声明 Coco Starter，功能模块依赖由 Coco 构建插件根据配置装配。
     * </p>
     * @throws Exception POM 解析失败时抛出
     */
    @Test
    void declaresOnlyCocoStarterAsRuntimeDependency() throws Exception {
        Element dependencies = directChild(readPom().getDocumentElement(), "dependencies");

        assertEquals(List.of("io.github.patton174:coco-spring-boot-starter"), runtimeDependencies(dependencies));
    }

    /**
     * <p>
     * 示例项目应该通过业务配置文件关闭能力，确保构建期和运行期读取同一份业务配置。
     * </p>
     * @throws Exception 配置文件解析失败时抛出
     */
    @Test
    void declaresFeatureSelectionInApplicationConfiguration() throws Exception {
        Path applicationYaml = sampleDirectory().resolve("src/main/resources/application.yml");
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("sample", new FileSystemResource(applicationYaml));

        List<String> disabledFeatures = new ArrayList<>();
        for (PropertySource<?> source : sources) {
            addIfPresent(disabledFeatures, source.getProperty("coco.features.disabled[0]"));
            addIfPresent(disabledFeatures, source.getProperty("coco.features.disabled[1]"));
        }

        assertEquals(List.of("tenant", "data-permission"), disabledFeatures);
    }

    /**
     * <p>
     * 示例主代码不应该把测试探针作为业务接口暴露。
     * </p>
     * @throws Exception 源码扫描失败时抛出
     */
    @Test
    void doesNotExposeFrameworkAccessLogProbeAsBusinessApi() throws Exception {
        Path mainSourceDirectory = sampleDirectory().resolve("src/main/java");
        try (Stream<Path> files = Files.walk(mainSourceDirectory)) {
            List<String> offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(CocoSampleBusinessIntegrationTest::containsAccessLogProbe)
                    .map(path -> mainSourceDirectory.relativize(path).toString())
                    .toList();

            assertEquals(List.of(), offenders);
        }
    }

    /**
     * <p>
     * 示例主代码应该按真实业务项目分层组织，避免继续使用含义过粗的 {@code business} 和 {@code web} 包。
     * </p>
     * @throws Exception 源码扫描失败时抛出
     */
    @Test
    void followsLayeredBusinessPackageStructure() throws Exception {
        assertEquals(Set.of(
                "io.github.coco.sample.basic",
                "io.github.coco.sample.basic.application.order",
                "io.github.coco.sample.basic.domain.order",
                "io.github.coco.sample.basic.infrastructure.order",
                "io.github.coco.sample.basic.interfaces.rest"), mainPackages());
    }

    private static Document readPom() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder()
                .parse(sampleDirectory().resolve("pom.xml").toFile());
        document.getDocumentElement().normalize();
        return document;
    }

    private static List<String> runtimeDependencies(Element dependencies) {
        List<String> coordinates = new ArrayList<>();
        NodeList children = dependencies.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element dependency && "dependency".equals(dependency.getLocalName())
                    && !"test".equals(childText(dependency, "scope"))) {
                coordinates.add(childText(dependency, "groupId") + ":" + childText(dependency, "artifactId"));
            }
        }
        return coordinates;
    }

    private static Element directChild(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element child && name.equals(child.getLocalName())) {
                return child;
            }
        }
        throw new IllegalStateException("Missing XML element: " + name);
    }

    private static String childText(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element child && name.equals(child.getLocalName())) {
                return child.getTextContent().trim();
            }
        }
        return "";
    }

    private static void addIfPresent(List<String> values, Object value) {
        if (value != null) {
            values.add(Objects.toString(value));
        }
    }

    private static boolean containsAccessLogProbe(Path path) {
        try {
            String content = Files.readString(path);
            return content.contains("CocoAccessLogRecorder") || content.contains("/access-log");
        }
        catch (Exception ex) {
            throw new IllegalStateException("Cannot read Java source: " + path, ex);
        }
    }

    private static Set<String> mainPackages() throws Exception {
        Path mainSourceDirectory = sampleDirectory().resolve("src/main/java");
        Set<String> packages = new TreeSet<>();
        try (Stream<Path> files = Files.walk(mainSourceDirectory)) {
            for (Path path : files.filter(sourcePath -> sourcePath.toString().endsWith(".java")).toList()) {
                String content = Files.readString(path);
                content.lines()
                        .filter(line -> line.startsWith("package "))
                        .map(line -> line.substring("package ".length(), line.length() - 1))
                        .findFirst()
                        .ifPresent(packages::add);
            }
        }
        return packages;
    }

    private static Path sampleDirectory() {
        Path basedir = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
        if (Files.exists(basedir.resolve("src/main/resources/application.yml"))) {
            return basedir;
        }
        Path nestedSample = basedir.resolve("coco-samples/coco-sample-basic");
        if (Files.exists(nestedSample.resolve("src/main/resources/application.yml"))) {
            return nestedSample;
        }
        throw new IllegalStateException("Cannot locate coco-sample-basic directory from " + basedir);
    }
}
