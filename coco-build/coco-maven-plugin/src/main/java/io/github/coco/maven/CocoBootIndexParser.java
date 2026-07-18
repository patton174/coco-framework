package io.github.coco.maven;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.CollectionStartEvent;
import org.yaml.snakeyaml.events.DocumentStartEvent;
import org.yaml.snakeyaml.events.NodeEvent;
import org.yaml.snakeyaml.events.ScalarEvent;

/**
 * Strict parser for the two finite Spring Boot archive index formats.
 */
final class CocoBootIndexParser {

    private static final Set<String> LAYER_NAMES = Set.of(
            "dependencies", "spring-boot-loader", "snapshot-dependencies", "application");

    private static final Map<String, Set<String>> FIXED_LAYER_PATHS = Map.of(
            "spring-boot-loader", Set.of("org/"),
            "application", Set.of("BOOT-INF/classes/", "BOOT-INF/classpath.idx",
                    "BOOT-INF/layers.idx", "META-INF/"));

    private CocoBootIndexParser() {
    }

    static Set<String> parse(String indexName, String content, CocoArchiveLimits limits) throws IOException {
        validateLineLengths(indexName, content, limits.indexLineBytes());
        Yaml yaml = yaml(limits);
        try {
            validateEvents(indexName, yaml, content);
            Iterator<Object> documents = yaml.loadAll(content).iterator();
            if (!documents.hasNext()) {
                throw new IOException("Spring Boot index is empty: " + indexName + ".");
            }
            Object document = documents.next();
            if (documents.hasNext()) {
                throw new IOException("Spring Boot index must contain exactly one YAML document: "
                        + indexName + ".");
            }
            return "BOOT-INF/classpath.idx".equals(indexName)
                    ? parseClasspath(indexName, document)
                    : parseLayers(indexName, document, limits.layerCount());
        }
        catch (YAMLException ex) {
            IOException failure = new IOException("Invalid Spring Boot index YAML in " + indexName + ".");
            failure.addSuppressed(ex);
            throw failure;
        }
    }

    private static Yaml yaml(CocoArchiveLimits limits) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setAllowRecursiveKeys(false);
        options.setMergeOnCompose(false);
        options.setNestingDepthLimit(limits.yamlNestingDepth());
        options.setCodePointLimit((int) Math.min(Integer.MAX_VALUE, limits.indexBytes()));
        return new Yaml(new SafeConstructor(options));
    }

    private static void validateEvents(String indexName, Yaml yaml, String content) throws IOException {
        int documents = 0;
        for (var event : yaml.parse(new StringReader(content))) {
            if (event instanceof DocumentStartEvent && ++documents > 1) {
                throw new IOException("Spring Boot index must contain exactly one YAML document: "
                        + indexName + ".");
            }
            if (event instanceof AliasEvent) {
                throw new IOException("YAML aliases are forbidden in Spring Boot index " + indexName + ".");
            }
            if (event instanceof NodeEvent nodeEvent && nodeEvent.getAnchor() != null) {
                throw new IOException("YAML anchors are forbidden in Spring Boot index " + indexName + ".");
            }
            if (event instanceof ScalarEvent scalarEvent && scalarEvent.getTag() != null) {
                throw new IOException("Explicit YAML tags are forbidden in Spring Boot index " + indexName + ".");
            }
            if (event instanceof CollectionStartEvent collectionEvent && collectionEvent.getTag() != null) {
                throw new IOException("Explicit YAML tags are forbidden in Spring Boot index " + indexName + ".");
            }
        }
    }

    private static Set<String> parseClasspath(String indexName, Object document) throws IOException {
        if (!(document instanceof List<?> entries)) {
            throw wrongRoot(indexName, "a sequence of library paths");
        }
        Set<String> references = new LinkedHashSet<>();
        for (Object value : entries) {
            if (!(value instanceof String reference)) {
                throw new IOException("Spring Boot classpath index entries must be strings: " + indexName + ".");
            }
            addLibraryReference(indexName, references, reference);
        }
        return references;
    }

    private static Set<String> parseLayers(String indexName, Object document, int layerLimit) throws IOException {
        if (!(document instanceof List<?> layers)) {
            throw wrongRoot(indexName, "a sequence of single-entry layer mappings");
        }
        if (layers.size() > layerLimit) {
            throw new IOException("Spring Boot layers index exceeds layer limit " + layerLimit + ".");
        }
        Set<String> seenLayers = new LinkedHashSet<>();
        Set<String> references = new LinkedHashSet<>();
        for (Object value : layers) {
            if (!(value instanceof Map<?, ?> layer) || layer.size() != 1) {
                throw new IOException("Each Spring Boot layer must be a single-entry mapping: " + indexName + ".");
            }
            Map.Entry<?, ?> entry = layer.entrySet().iterator().next();
            if (!(entry.getKey() instanceof String layerName) || !LAYER_NAMES.contains(layerName)) {
                throw new IOException("Unsupported Spring Boot layer '" + entry.getKey()
                        + "' in " + indexName + ".");
            }
            if (!seenLayers.add(layerName)) {
                throw new IOException("Duplicate Spring Boot layer '" + layerName + "' in " + indexName + ".");
            }
            if (!(entry.getValue() instanceof List<?> paths)) {
                throw new IOException("Spring Boot layer '" + layerName + "' must contain a path sequence.");
            }
            for (Object path : paths) {
                if (!(path instanceof String reference)) {
                    throw new IOException("Spring Boot layer paths must be strings: " + indexName + ".");
                }
                if ("dependencies".equals(layerName) || "snapshot-dependencies".equals(layerName)) {
                    addLibraryReference(indexName, references, reference);
                }
                else if (!FIXED_LAYER_PATHS.get(layerName).contains(reference)) {
                    throw new IOException("Unsupported Spring Boot layer path '" + reference
                            + "' in layer '" + layerName + "' of " + indexName + ".");
                }
            }
        }
        return references;
    }

    private static void addLibraryReference(String indexName, Set<String> references, String reference)
            throws IOException {
        validateLibraryReference(reference);
        if (!references.add(reference)) {
            throw new IOException("Rewritten " + indexName
                    + " contains duplicate library reference '" + reference + "'.");
        }
    }

    private static void validateLibraryReference(String reference) throws IOException {
        String fileName = reference.startsWith("BOOT-INF/lib/")
                ? reference.substring("BOOT-INF/lib/".length())
                : "";
        if (fileName.isEmpty() || fileName.contains("/") || fileName.contains("\\")
                || !fileName.endsWith(".jar") || ".jar".equals(fileName)) {
            throw new IOException("Non-canonical Spring Boot library index path: '" + reference + "'.");
        }
    }

    private static void validateLineLengths(String indexName, String content, int lineLimit) throws IOException {
        for (String line : content.lines().toList()) {
            if (line.getBytes(StandardCharsets.UTF_8).length > lineLimit) {
                throw new IOException("Spring Boot index line exceeds byte limit " + lineLimit
                        + " in " + indexName + ".");
            }
        }
    }

    private static IOException wrongRoot(String indexName, String expected) {
        return new IOException("Spring Boot index " + indexName + " must have " + expected + ".");
    }
}
