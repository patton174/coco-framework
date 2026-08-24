package io.github.coco.fixture.archive;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Verifies the reactor-built Spring Boot archive after feature assembly and pruning.
 */
class FeatureArchiveSmokeIT {

    private static final String ARCHIVE_PATH_PROPERTY = "coco.feature.archive.path";

    private static final String LIBRARY_PREFIX = "BOOT-INF/lib/";

    private static final Pattern INDEX_LIBRARY = Pattern.compile("BOOT-INF/lib/[^\"\\r\\n]+\\.jar");

    private static final Set<String> ENABLED_FEATURES = Set.of("web", "audit", "security", "openapi", "storage");

    private static final Set<String> DISABLED_FEATURES = Set.of(
            "mybatis-plus", "tenant", "data-permission", "rate-limit", "idempotency", "codegen");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void verifiesCurrentReactorArchiveManifestAndPrunedLibraries() throws Exception {
        Path archivePath = archivePath();

        try (JarFile archive = new JarFile(archivePath.toFile())) {
            JsonNode manifest = readJson(archive, "META-INF/coco/features.json");
            Map<String, Boolean> featureStates = featureStates(manifest);
            assertThat(featureStates).containsKeys(ENABLED_FEATURES.toArray(String[]::new));
            assertThat(featureStates).containsKeys(DISABLED_FEATURES.toArray(String[]::new));
            ENABLED_FEATURES.forEach(feature -> assertThat(featureStates.get(feature)).isTrue());
            DISABLED_FEATURES.forEach(feature -> assertThat(featureStates.get(feature)).isFalse());

            Set<String> archiveLibraries = archiveLibraries(archive);
            assertThat(archiveLibraries).anyMatch(name -> name.startsWith("coco-feature-web-"));
            assertThat(archiveLibraries).anyMatch(name -> name.startsWith("coco-feature-audit-"));
            assertThat(archiveLibraries).anyMatch(name -> name.startsWith("coco-feature-security-"));
            assertThat(archiveLibraries).anyMatch(name -> name.startsWith("coco-feature-openapi-"));
            assertThat(archiveLibraries).anyMatch(name -> name.startsWith("coco-storage-"));
            assertNoDisabledFeatureLibraries(archiveLibraries, "archive");

            Set<String> classpathLibraries = indexLibraries(archive, "BOOT-INF/classpath.idx");
            Set<String> layerLibraries = indexLibraries(archive, "BOOT-INF/layers.idx");
            assertNoDisabledFeatureLibraries(classpathLibraries, "BOOT-INF/classpath.idx");
            assertNoDisabledFeatureLibraries(layerLibraries, "BOOT-INF/layers.idx");
            assertThat(classpathLibraries).isEqualTo(archiveLibraries);
            assertThat(layerLibraries).isEqualTo(archiveLibraries);
        }
    }

    private Path archivePath() {
        String configuredPath = System.getProperty(ARCHIVE_PATH_PROPERTY);
        assertThat(configuredPath)
                .as("Failsafe system property %s", ARCHIVE_PATH_PROPERTY)
                .isNotBlank();

        Path archivePath = Path.of(configuredPath).toAbsolutePath().normalize();
        assertThat(Files.isRegularFile(archivePath))
                .as("reactor-built primary Spring Boot archive %s", archivePath)
                .isTrue();
        return archivePath;
    }

    private JsonNode readJson(JarFile archive, String entryName) throws IOException {
        var entry = archive.getJarEntry(entryName);
        assertThat(entry).as("archive entry %s", entryName).isNotNull();
        try (var stream = archive.getInputStream(entry)) {
            return this.objectMapper.readTree(stream);
        }
    }

    private Map<String, Boolean> featureStates(JsonNode manifest) {
        Map<String, Boolean> states = new java.util.HashMap<>();
        for (JsonNode feature : manifest.path("features")) {
            states.put(feature.path("id").asText(), feature.path("enabled").asBoolean());
        }
        return states;
    }

    private Set<String> archiveLibraries(JarFile archive) {
        Set<String> libraries = new TreeSet<>();
        archive.stream()
                .map(entry -> entry.getName())
                .filter(name -> name.startsWith(LIBRARY_PREFIX) && name.endsWith(".jar"))
                .map(name -> name.substring(LIBRARY_PREFIX.length()))
                .forEach(libraries::add);
        return libraries;
    }

    private Set<String> indexLibraries(JarFile archive, String entryName) throws IOException {
        var entry = archive.getJarEntry(entryName);
        assertThat(entry).as("archive index %s", entryName).isNotNull();
        try (var stream = archive.getInputStream(entry)) {
            String index = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = INDEX_LIBRARY.matcher(index);
            Set<String> libraries = new TreeSet<>();
            while (matcher.find()) {
                libraries.add(matcher.group().substring(LIBRARY_PREFIX.length()));
            }
            return libraries;
        }
    }

    private static void assertNoDisabledFeatureLibraries(Set<String> libraries, String source) {
        assertThat(libraries).as("disabled feature libraries in %s", source)
                .noneMatch(FeatureArchiveSmokeIT::isDisabledFeatureLibrary);
    }

    private static boolean isDisabledFeatureLibrary(String library) {
        return library.startsWith("coco-feature-codegen-")
                || library.startsWith("coco-mybatis-plus-")
                || library.startsWith("coco-feature-mybatis-plus-")
                || library.startsWith("coco-feature-tenant-")
                || library.startsWith("coco-tenant-")
                || library.startsWith("coco-feature-data-permission-")
                || library.startsWith("coco-data-permission-")
                || library.startsWith("coco-rate-limit-")
                || library.startsWith("coco-idempotency-")
                || library.startsWith("mybatis-")
                || library.startsWith("mybatis-plus-")
                || library.startsWith("freemarker-");
    }
}
