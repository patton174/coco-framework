package io.github.coco.spring.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CocoIdempotencyStarterDependencyContractTest {
    @Test
    void composesIdempotencyModule() throws Exception {
        String pom = Files.readString(Path.of(System.getProperty("basedir", ".")).toAbsolutePath().resolve("pom.xml"));
        assertThat(pom).contains("<artifactId>coco-idempotency</artifactId>");
    }
}
