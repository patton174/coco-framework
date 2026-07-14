package io.github.coco.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class CocoTestCompatibilityArtifactIT {

    private static final String SUPPORT_CLASS = "io/github/coco/test/CocoTestSupport.class";

    private static final String PACKAGE_INFO_CLASS = "io/github/coco/test/package-info.class";

    private static final String FACADE_MARKER = "META-INF/coco/compatibility-facade.txt";

    private static final Set<String> MAIN_FACADE_FILES = Set.of(
            "META-INF/MANIFEST.MF",
            "META-INF/maven/io.github.patton174/coco-test/pom.xml",
            "META-INF/maven/io.github.patton174/coco-test/pom.properties");

    @Test
    void compatibilityJarIsSourceFreeAndCanonicalJarOwnsImplementation() throws IOException {
        Set<String> compatibilityFiles = archiveFiles(requiredArtifact("cocoTestCompatibilityJar"));
        Set<String> canonicalFiles = archiveFiles(requiredArtifact("cocoTestCanonicalJar"));

        assertEquals(MAIN_FACADE_FILES, compatibilityFiles);
        assertTrue(canonicalFiles.contains(SUPPORT_CLASS));
        assertTrue(canonicalFiles.contains(PACKAGE_INFO_CLASS));
    }

    @Test
    void releaseArchivesContainOnlyFacadePublishingMetadata() throws IOException {
        if (!Boolean.parseBoolean(System.getProperty("cocoTestReleaseArtifactsRequired"))) {
            return;
        }

        assertReleaseFacadeArchive(requiredArtifact("cocoTestReleaseSourcesJar"));
        assertReleaseFacadeArchive(requiredArtifact("cocoTestReleaseJavadocJar"));
    }

    private static void assertReleaseFacadeArchive(Path archive) throws IOException {
        Set<String> files = archiveFiles(archive);

        assertTrue(files.contains(FACADE_MARKER));
        assertFalse(files.stream().anyMatch(CocoTestCompatibilityArtifactIT::isImplementationContent));
        assertTrue(files.stream().allMatch(CocoTestCompatibilityArtifactIT::isFacadePublishingMetadata),
                () -> "unexpected release facade content in " + archive + ": " + files);
    }

    private static boolean isImplementationContent(String name) {
        return name.endsWith(".class")
                || name.endsWith(".java")
                || name.endsWith("AutoConfiguration.imports")
                || name.endsWith("spring.factories")
                || name.endsWith("additional-spring-configuration-metadata.json");
    }

    private static boolean isFacadePublishingMetadata(String name) {
        return "META-INF/MANIFEST.MF".equals(name)
                || FACADE_MARKER.equals(name)
                || name.startsWith("META-INF/maven/");
    }

    private static Path requiredArtifact(String property) {
        Path artifact = Path.of(System.getProperty(property, ""));
        assertTrue(Files.isRegularFile(artifact), () -> property + " does not reference an artifact: " + artifact);
        return artifact;
    }

    private static Set<String> archiveFiles(Path archive) throws IOException {
        try (JarFile jarFile = new JarFile(archive.toFile())) {
            return jarFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(entry -> entry.getName())
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }
}
