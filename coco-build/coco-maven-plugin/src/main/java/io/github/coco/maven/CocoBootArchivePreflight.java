package io.github.coco.maven;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.model.CocoFeatureDefinition;
import io.github.coco.feature.model.CocoFeatureManifest;
import io.github.coco.feature.model.CocoFeatureManifestEntry;
import io.github.coco.feature.model.StandardCocoFeatures;

/**
 * Spring Boot 可执行归档的只读裁剪预检。
 *
 * @author patton174
 * @since 2.0.0
 */
final class CocoBootArchivePreflight {

    private static final Set<String> SUPPORTED_LAUNCHERS = Set.of(
            "org.springframework.boot.loader.JarLauncher",
            "org.springframework.boot.loader.launch.JarLauncher");

    private CocoBootArchivePreflight() {
    }

    static Result inspect(JarFile source, CocoFeatureManifest manifest, String featureGroupId,
            String expectedFeatureVersion, Set<String> pruneArtifactIds) throws IOException {
        validateManifestAndStructure(source);
        Map<String, CocoFeature> featureByArtifactId = featureByArtifactId(manifest);
        Set<String> pruneEntryNames = new LinkedHashSet<>();
        Set<String> cocoVersions = new LinkedHashSet<>();
        var entries = source.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (!isBootLibrary(entry)) {
                continue;
            }
            List<ArtifactCoordinates> coordinates = readCoordinates(source, entry);
            List<ArtifactCoordinates> cocoCoordinates = coordinates.stream()
                    .filter(coordinate -> featureGroupId.equals(coordinate.groupId()))
                    .filter(coordinate -> coordinate.artifactId().startsWith("coco-"))
                    .toList();
            if (looksLikeCocoLibrary(entry.getName()) && coordinates.isEmpty()) {
                throw new IOException("Cannot verify Maven GAV for Coco-named nested library '"
                        + entry.getName() + "'.");
            }
            cocoCoordinates.forEach(coordinate -> cocoVersions.add(coordinate.version()));
            for (ArtifactCoordinates coordinate : cocoCoordinates) {
                validateLibraryFileName(entry.getName(), coordinate);
                CocoFeature feature = featureByArtifactId.get(coordinate.artifactId());
                if (feature != null && pruneArtifactIds.contains(coordinate.artifactId())) {
                    CocoFeatureManifestEntry manifestEntry = manifest.features().stream()
                            .filter(candidate -> candidate.id().equals(feature.id()))
                            .findFirst()
                            .orElseThrow();
                    if (manifestEntry.enabled()) {
                        throw new IOException("Refusing to prune enabled Coco feature artifact "
                                + coordinate.gav() + ".");
                    }
                    pruneEntryNames.add(entry.getName());
                }
            }
        }
        validateCocoVersions(cocoVersions, expectedFeatureVersion);
        return new Result(Set.copyOf(pruneEntryNames));
    }

    private static void validateManifestAndStructure(JarFile source) throws IOException {
        Set<String> entryNames = new HashSet<>();
        boolean hasClasses = false;
        boolean hasLibraries = false;
        var entries = source.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            validateEntryName(entry.getName(), "outer archive");
            if (!entryNames.add(entry.getName())) {
                throw new IOException("Duplicate ZIP entry '" + entry.getName() + "' in outer archive.");
            }
            if (isSignatureMaterial(entry)) {
                throw new IOException("Refusing to rewrite signed archive containing '" + entry.getName() + "'.");
            }
            hasClasses |= !entry.isDirectory() && entry.getName().startsWith("BOOT-INF/classes/");
            hasLibraries |= isBootLibrary(entry);
        }

        Manifest jarManifest = source.getManifest();
        if (jarManifest == null) {
            throw new IOException("Archive is not an executable Spring Boot JAR: missing META-INF/MANIFEST.MF.");
        }
        Attributes attributes = jarManifest.getMainAttributes();
        String mainClass = attributes.getValue(Attributes.Name.MAIN_CLASS);
        if (!SUPPORTED_LAUNCHERS.contains(mainClass)) {
            throw new IOException("Archive is not an executable Spring Boot JAR: unsupported Main-Class '"
                    + mainClass + "'.");
        }
        String launcherEntry = mainClass.replace('.', '/') + ".class";
        if (source.getJarEntry(launcherEntry) == null) {
            throw new IOException("Archive is not an executable Spring Boot JAR: missing launcher '"
                    + launcherEntry + "'.");
        }
        if (!hasClasses) {
            throw new IOException("Archive is not an executable Spring Boot JAR: BOOT-INF/classes is empty.");
        }
        if (!hasLibraries) {
            throw new IOException("Archive is not an executable Spring Boot JAR: BOOT-INF/lib is empty.");
        }
    }

    private static List<ArtifactCoordinates> readCoordinates(JarFile source, JarEntry libraryEntry)
            throws IOException {
        List<ArtifactCoordinates> coordinates = new ArrayList<>();
        Set<String> entryNames = new HashSet<>();
        try (ZipInputStream nestedJar = new ZipInputStream(source.getInputStream(libraryEntry))) {
            ZipEntry nestedEntry;
            while ((nestedEntry = nestedJar.getNextEntry()) != null) {
                validateEntryName(nestedEntry.getName(), libraryEntry.getName());
                if (!entryNames.add(nestedEntry.getName())) {
                    throw new IOException("Duplicate ZIP entry '" + nestedEntry.getName()
                            + "' in nested library '" + libraryEntry.getName() + "'.");
                }
                if (nestedEntry.isDirectory() || !nestedEntry.getName().startsWith("META-INF/maven/")
                        || !nestedEntry.getName().endsWith("/pom.properties")) {
                    continue;
                }
                Properties properties = new Properties();
                properties.load(nestedJar);
                String groupId = requiredCoordinate(properties, "groupId", libraryEntry.getName());
                String artifactId = requiredCoordinate(properties, "artifactId", libraryEntry.getName());
                String version = requiredCoordinate(properties, "version", libraryEntry.getName());
                String expectedEntryName = "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties";
                if (!expectedEntryName.equals(nestedEntry.getName())) {
                    throw new IOException("Maven GAV path '" + nestedEntry.getName()
                            + "' does not match coordinates " + groupId + ":" + artifactId
                            + " in nested library '" + libraryEntry.getName() + "'.");
                }
                coordinates.add(new ArtifactCoordinates(groupId, artifactId, version));
            }
        }
        return List.copyOf(coordinates);
    }

    private static String requiredCoordinate(Properties properties, String name, String libraryEntryName)
            throws IOException {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IOException("Incomplete Maven GAV in nested library '" + libraryEntryName
                    + "': missing " + name + ".");
        }
        return value.trim();
    }

    private static void validateCocoVersions(Set<String> versions, String expectedVersion) throws IOException {
        if (versions.size() > 1) {
            throw new IOException("Nested Coco artifacts use inconsistent versions: " + versions + ".");
        }
        if (expectedVersion != null && !expectedVersion.isBlank()
                && versions.stream().anyMatch(version -> !expectedVersion.trim().equals(version))) {
            throw new IOException("Nested Coco artifact version " + versions.iterator().next()
                    + " does not match expected Coco version " + expectedVersion.trim() + ".");
        }
    }

    private static void validateLibraryFileName(String entryName, ArtifactCoordinates coordinates)
            throws IOException {
        String expectedName = "BOOT-INF/lib/" + coordinates.artifactId() + "-"
                + coordinates.version() + ".jar";
        if (!expectedName.equals(entryName)) {
            throw new IOException("Nested Coco artifact " + coordinates.gav()
                    + " does not match Boot library entry '" + entryName + "'.");
        }
    }

    private static Map<String, CocoFeature> featureByArtifactId(CocoFeatureManifest manifest) {
        Map<String, CocoFeature> features = new HashMap<>();
        Map<CocoFeature, CocoFeatureDefinition> definitions = StandardCocoFeatures.allByFeature();
        for (CocoFeatureManifestEntry entry : manifest.features()) {
            CocoFeature feature = CocoFeature.fromId(entry.id()).orElseThrow();
            StandardCocoFeatures.equivalentArtifactIds(definitions.get(feature))
                    .forEach(artifactId -> features.put(artifactId, feature));
        }
        return Map.copyOf(features);
    }

    private static boolean isSignatureMaterial(JarEntry entry) {
        if (entry.isDirectory()) {
            return false;
        }
        String name = entry.getName().toUpperCase(Locale.ROOT);
        if (!name.startsWith("META-INF/")) {
            return false;
        }
        String relative = name.substring("META-INF/".length());
        return !relative.contains("/")
                && (relative.endsWith(".SF") || relative.endsWith(".RSA")
                        || relative.endsWith(".DSA") || relative.endsWith(".EC")
                        || relative.startsWith("SIG-"));
    }

    private static boolean isBootLibrary(JarEntry entry) {
        return !entry.isDirectory() && entry.getName().startsWith("BOOT-INF/lib/")
                && entry.getName().endsWith(".jar");
    }

    private static boolean looksLikeCocoLibrary(String entryName) {
        if (!entryName.startsWith("BOOT-INF/lib/") || !entryName.endsWith(".jar")) {
            return false;
        }
        String fileName = entryName.substring("BOOT-INF/lib/".length());
        return fileName.startsWith("coco-");
    }

    private static void validateEntryName(String name, String archiveDescription) throws IOException {
        if (name == null || name.isEmpty() || name.indexOf('\\') >= 0 || name.startsWith("/")
                || (name.length() > 1 && Character.isLetter(name.charAt(0)) && name.charAt(1) == ':')) {
            throw new IOException("Unsafe ZIP entry name '" + name + "' in " + archiveDescription + ".");
        }
        String value = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IOException("Unsafe ZIP entry name '" + name + "' in " + archiveDescription + ".");
            }
        }
    }

    record Result(Set<String> pruneEntryNames) {
    }

    private record ArtifactCoordinates(String groupId, String artifactId, String version) {

        String gav() {
            return this.groupId + ":" + this.artifactId + ":" + this.version;
        }
    }
}
