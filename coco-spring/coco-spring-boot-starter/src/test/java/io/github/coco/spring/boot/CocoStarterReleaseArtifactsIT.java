package io.github.coco.spring.boot;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;

class CocoStarterReleaseArtifactsIT {

    @Test
    void releaseArchivesExistAndAreReadable() throws IOException {
        if (!Boolean.parseBoolean(System.getProperty("cocoStarterReleaseArtifactsRequired"))) {
            return;
        }

        assertReadableArchive(requiredArtifact("cocoStarterMainJar"));
        assertReadableArchive(requiredArtifact("cocoStarterSourcesJar"));
        assertReadableArchive(requiredArtifact("cocoStarterJavadocJar"));
    }

    private static Path requiredArtifact(String property) {
        Path artifact = Path.of(System.getProperty(property, ""));
        assertTrue(Files.isRegularFile(artifact), () -> property + " does not reference an artifact: " + artifact);
        return artifact;
    }

    private static void assertReadableArchive(Path archive) throws IOException {
        int fileCount = 0;
        try (JarFile jarFile = new JarFile(archive.toFile())) {
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    try (var inputStream = jarFile.getInputStream(entry)) {
                        inputStream.transferTo(OutputStream.nullOutputStream());
                    }
                    fileCount++;
                }
            }
        }
        assertTrue(fileCount > 0, () -> "release archive is empty: " + archive);
    }
}
