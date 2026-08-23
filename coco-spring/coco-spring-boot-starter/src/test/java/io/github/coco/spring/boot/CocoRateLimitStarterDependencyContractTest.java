package io.github.coco.spring.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class CocoRateLimitStarterDependencyContractTest {

    @Test
    void composesRateLimitModule() throws Exception {
        Path starterPom = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().resolve("pom.xml");

        assertThat(directDependencyArtifactIds(starterPom)).contains("coco-rate-limit");
    }

    private Set<String> directDependencyArtifactIds(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document document;
        try (InputStream input = Files.newInputStream(pom)) {
            document = factory.newDocumentBuilder().parse(input);
        }
        NodeList artifactIds = (NodeList) XPathFactory.newInstance().newXPath().evaluate(
                "/*[local-name()='project']/*[local-name()='dependencies']/*[local-name()='dependency']"
                        + "/*[local-name()='artifactId']/text()",
                document, XPathConstants.NODESET);
        Set<String> dependencies = new LinkedHashSet<>();
        for (int index = 0; index < artifactIds.getLength(); index++) {
            dependencies.add(((Node) artifactIds.item(index)).getTextContent());
        }
        return dependencies;
    }
}
