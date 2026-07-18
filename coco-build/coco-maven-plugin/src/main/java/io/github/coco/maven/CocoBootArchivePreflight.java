package io.github.coco.maven;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private static final Set<String> MAVEN_COORDINATE_KEYS = Set.of("groupId", "artifactId", "version");

    private static final Attributes.Name BUNDLE_SYMBOLIC_NAME = new Attributes.Name("Bundle-SymbolicName");

    private CocoBootArchivePreflight() {
    }

    static Result inspect(JarFile source, CocoFeatureManifest manifest, String featureGroupId,
            String expectedFeatureVersion, Set<String> pruneArtifactIds,
            Set<PrunableArtifact> resolvedArtifacts) throws IOException {
        return inspect(source, manifest, featureGroupId, expectedFeatureVersion,
                pruneArtifactIds, resolvedArtifacts, CocoArchiveLimits.DEFAULT);
    }

    static Result inspect(JarFile source, CocoFeatureManifest manifest, String featureGroupId,
            String expectedFeatureVersion, Set<String> pruneArtifactIds,
            Set<PrunableArtifact> resolvedArtifacts, CocoArchiveLimits limits) throws IOException {
        return inspect(source, manifest, featureGroupId, expectedFeatureVersion, pruneArtifactIds,
                resolvedArtifacts, limits, new CocoArchiveIo.CumulativeBudget(
                        limits.archiveReadBytes(), "Archive cumulative read bytes"));
    }

    static Result inspect(JarFile source, CocoFeatureManifest manifest, String featureGroupId,
            String expectedFeatureVersion, Set<String> pruneArtifactIds,
            Set<PrunableArtifact> resolvedArtifacts, CocoArchiveLimits limits,
            CocoArchiveIo.CumulativeBudget readBudget) throws IOException {
        validateManifestAndStructure(source, limits, readBudget);
        Map<String, CocoFeature> featureByArtifactId = featureByArtifactId(manifest);
        Map<String, PrunableArtifact> resolvedByEntryName = new HashMap<>();
        for (PrunableArtifact artifact : resolvedArtifacts) {
            PrunableArtifact existing = resolvedByEntryName.putIfAbsent(artifact.entryName(), artifact);
            if (existing != null && !existing.equals(artifact)) {
                throw new IOException("Multiple resolved Maven artifacts map to nested library '"
                        + artifact.entryName() + "': " + existing.gav() + " and " + artifact.gav() + ".");
            }
        }
        Set<String> pruneEntryNames = new LinkedHashSet<>();
        Set<String> cocoVersions = new LinkedHashSet<>();
        long nestedArchiveBytes = 0;
        var entries = source.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (!isBootLibrary(entry)) {
                continue;
            }
            NestedLibraryMetadata metadata = readCoordinates(source, entry, limits, readBudget);
            nestedArchiveBytes = CocoArchiveIo.addBounded(nestedArchiveBytes, metadata.uncompressedBytes(),
                    limits.nestedArchiveBytes(), "Nested archive uncompressed bytes");
            List<ArtifactCoordinates> coordinates = metadata.coordinates();
            PrunableArtifact resolvedArtifact = resolvedByEntryName.get(entry.getName());
            if (resolvedArtifact != null) {
                String actualSha256;
                try (InputStream inputStream = source.getInputStream(entry)) {
                    actualSha256 = CocoArchiveIo.sha256Bounded(inputStream, limits.outerEntryBytes(),
                            "Nested library '" + entry.getName() + "' SHA-256 input", readBudget);
                }
                if (!resolvedArtifact.sha256().equals(actualSha256)) {
                    throw new IOException("Resolved Maven artifact SHA-256 does not match prunable nested library '"
                            + entry.getName() + "' for " + resolvedArtifact.gav() + ".");
                }
                if (!resolvedArtifact.matches(metadata)) {
                    String identitySource = coordinates.isEmpty() ? "artifact identity" : "Maven metadata";
                    throw new IOException("Nested " + identitySource + " does not match resolved Maven GAV "
                            + resolvedArtifact.gav() + " for prunable nested library '" + entry.getName() + "'.");
                }
                pruneEntryNames.add(entry.getName());
            }
            List<ArtifactCoordinates> cocoCoordinates = coordinates.stream()
                    .filter(coordinate -> featureGroupId.equals(coordinate.groupId()))
                    .filter(coordinate -> coordinate.artifactId().startsWith("coco-"))
                    .toList();
            if (looksLikeCocoLibrary(entry.getName()) && coordinates.isEmpty() && resolvedArtifact == null) {
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

    private static void validateManifestAndStructure(JarFile source, CocoArchiveLimits limits,
            CocoArchiveIo.CumulativeBudget readBudget) throws IOException {
        ArchiveEntryNames entryNames = new ArchiveEntryNames("outer archive", limits.entryNameBytes());
        boolean hasClasses = false;
        boolean hasLibraries = false;
        int entryCount = 0;
        long totalBytes = 0;
        var entries = source.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (++entryCount > limits.entryCount()) {
                throw new IOException("Outer archive exceeds ZIP entry count limit " + limits.entryCount() + ".");
            }
            entryNames.add(entry.getName(), entry.isDirectory());
            if (isSignatureMaterial(entry)) {
                throw new IOException("Refusing to rewrite signed archive containing '" + entry.getName() + "'.");
            }
            hasClasses |= !entry.isDirectory() && entry.getName().startsWith("BOOT-INF/classes/");
            hasLibraries |= isBootLibrary(entry);
            if (!entry.isDirectory()) {
                try (InputStream inputStream = source.getInputStream(entry)) {
                    long entryBytes = CocoArchiveIo.drainBounded(inputStream, limits.outerEntryBytes(),
                            "Outer ZIP entry '" + entry.getName() + "'", readBudget);
                    totalBytes = CocoArchiveIo.addBounded(totalBytes, entryBytes, limits.outerTotalBytes(),
                            "Outer archive uncompressed bytes");
                }
            }
        }

        JarEntry manifestEntry = source.getJarEntry(JarFile.MANIFEST_NAME);
        if (manifestEntry == null || manifestEntry.isDirectory()) {
            throw new IOException("Archive is not an executable Spring Boot JAR: missing META-INF/MANIFEST.MF.");
        }
        Manifest jarManifest;
        try (InputStream inputStream = source.getInputStream(manifestEntry)) {
            byte[] manifestBytes = CocoArchiveIo.readBounded(inputStream, limits.manifestBytes(),
                    "Executable JAR manifest", readBudget);
            jarManifest = new Manifest(new ByteArrayInputStream(manifestBytes));
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

    private static NestedLibraryMetadata readCoordinates(JarFile source, JarEntry libraryEntry,
            CocoArchiveLimits limits, CocoArchiveIo.CumulativeBudget readBudget)
            throws IOException {
        List<ArtifactCoordinates> coordinates = new ArrayList<>();
        ArchiveEntryNames entryNames = new ArchiveEntryNames(
                "nested library '" + libraryEntry.getName() + "'", limits.entryNameBytes());
        NestedManifestIdentity manifestIdentity = null;
        int entryCount = 0;
        long totalBytes = 0;
        try (InputStream libraryInput = CocoArchiveIo.budgeted(source.getInputStream(libraryEntry), readBudget);
                ZipInputStream nestedJar = new ZipInputStream(libraryInput)) {
            ZipEntry nestedEntry;
            while ((nestedEntry = nestedJar.getNextEntry()) != null) {
                if (++entryCount > limits.entryCount()) {
                    throw new IOException("Nested library '" + libraryEntry.getName()
                            + "' exceeds ZIP entry count limit " + limits.entryCount() + ".");
                }
                entryNames.add(nestedEntry.getName(), nestedEntry.isDirectory());
                boolean pomProperties = !nestedEntry.isDirectory()
                        && nestedEntry.getName().startsWith("META-INF/maven/")
                        && nestedEntry.getName().endsWith("/pom.properties");
                boolean manifest = !nestedEntry.isDirectory()
                        && JarFile.MANIFEST_NAME.equals(nestedEntry.getName());
                long entryBytes;
                byte[] content = null;
                if (nestedEntry.isDirectory()) {
                    entryBytes = 0;
                }
                else if (pomProperties) {
                    content = CocoArchiveIo.readBounded(nestedJar,
                            Math.min(limits.nestedEntryBytes(), limits.pomPropertiesBytes()),
                            "Maven pom.properties '" + nestedEntry.getName() + "'", readBudget);
                    entryBytes = content.length;
                }
                else if (manifest) {
                    content = CocoArchiveIo.readBounded(nestedJar,
                            Math.min(limits.nestedEntryBytes(), limits.manifestBytes()),
                            "Nested JAR manifest '" + libraryEntry.getName() + "'", readBudget);
                    entryBytes = content.length;
                }
                else {
                    entryBytes = CocoArchiveIo.drainBounded(nestedJar, limits.nestedEntryBytes(),
                            "Nested ZIP entry '" + nestedEntry.getName() + "'", readBudget);
                }
                totalBytes = CocoArchiveIo.addBounded(totalBytes, entryBytes, limits.nestedLibraryBytes(),
                        "Nested library '" + libraryEntry.getName() + "' uncompressed bytes");
                if (manifest) {
                    manifestIdentity = parseManifestIdentity(content);
                }
                if (!pomProperties) {
                    continue;
                }
                ArtifactCoordinates parsed = parseCoordinates(content, libraryEntry.getName(), limits);
                String groupId = parsed.groupId();
                String artifactId = parsed.artifactId();
                String version = parsed.version();
                String expectedEntryName = "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties";
                if (!expectedEntryName.equals(nestedEntry.getName())) {
                    throw new IOException("Maven GAV path '" + nestedEntry.getName()
                            + "' does not match coordinates " + groupId + ":" + artifactId
                            + " in nested library '" + libraryEntry.getName() + "'.");
                }
                coordinates.add(parsed);
            }
        }
        return new NestedLibraryMetadata(List.copyOf(coordinates), manifestIdentity, totalBytes);
    }

    private static NestedManifestIdentity parseManifestIdentity(byte[] content) throws IOException {
        Manifest manifest = new Manifest(new ByteArrayInputStream(content));
        Attributes attributes = manifest.getMainAttributes();
        String symbolicName = attributes.getValue(BUNDLE_SYMBOLIC_NAME);
        String implementationVersion = attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        if (symbolicName == null || implementationVersion == null) {
            return null;
        }
        return new NestedManifestIdentity(symbolicName, implementationVersion);
    }

    private static ArtifactCoordinates parseCoordinates(byte[] content, String libraryEntryName,
            CocoArchiveLimits limits) throws IOException {
        Map<String, String> coordinates = new LinkedHashMap<>();
        String properties = new String(content, StandardCharsets.ISO_8859_1);
        for (String line : properties.lines().toList()) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0 || line.endsWith("\\")) {
                throw new IOException("Malformed Maven pom.properties in nested library '"
                        + libraryEntryName + "'.");
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            if (!MAVEN_COORDINATE_KEYS.contains(key)) {
                throw new IOException("Unexpected Maven GAV key '" + key
                        + "' in nested library '" + libraryEntryName + "'.");
            }
            if (coordinates.putIfAbsent(key,
                    requiredCoordinate(value, key, libraryEntryName, limits.gavValueBytes())) != null) {
                throw new IOException("Duplicate Maven GAV key '" + key
                        + "' in nested library '" + libraryEntryName + "'.");
            }
        }
        if (!coordinates.keySet().equals(MAVEN_COORDINATE_KEYS)) {
            Set<String> missing = new LinkedHashSet<>(MAVEN_COORDINATE_KEYS);
            missing.removeAll(coordinates.keySet());
            throw new IOException("Incomplete Maven GAV in nested library '" + libraryEntryName
                    + "': missing " + missing + ".");
        }
        return new ArtifactCoordinates(
                coordinates.get("groupId"), coordinates.get("artifactId"), coordinates.get("version"));
    }

    private static String requiredCoordinate(String value, String name, String libraryEntryName, int byteLimit)
            throws IOException {
        if (value.isBlank()) {
            throw new IOException("Incomplete Maven GAV in nested library '" + libraryEntryName
                    + "': missing " + name + ".");
        }
        if (!value.equals(value.trim()) || value.getBytes(StandardCharsets.UTF_8).length > byteLimit
                || value.codePoints().anyMatch(Character::isISOControl)
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
            throw new IOException("Invalid Maven GAV value for " + name
                    + " in nested library '" + libraryEntryName + "'.");
        }
        return value;
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

    private static void validateEntryName(String name, String archiveDescription, int byteLimit) throws IOException {
        if (name == null || name.isEmpty() || name.indexOf('\\') >= 0 || name.startsWith("/")
                || (name.length() > 1 && Character.isLetter(name.charAt(0)) && name.charAt(1) == ':')) {
            throw new IOException("Unsafe ZIP entry name '" + name + "' in " + archiveDescription + ".");
        }
        if (name.getBytes(StandardCharsets.UTF_8).length > byteLimit) {
            throw new IOException("ZIP entry name exceeds byte limit " + byteLimit
                    + " in " + archiveDescription + ".");
        }
        String value = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IOException("Unsafe ZIP entry name '" + name + "' in " + archiveDescription + ".");
            }
        }
    }

    private static final class ArchiveEntryNames {

        private final String archiveDescription;

        private final int entryNameByteLimit;

        private final Map<String, String> canonicalNames = new HashMap<>();

        private final Set<String> files = new LinkedHashSet<>();

        private final Set<String> directories = new LinkedHashSet<>();

        private ArchiveEntryNames(String archiveDescription, int entryNameByteLimit) {
            this.archiveDescription = archiveDescription;
            this.entryNameByteLimit = entryNameByteLimit;
        }

        private void add(String name, boolean directory) throws IOException {
            validateEntryName(name, this.archiveDescription, this.entryNameByteLimit);
            String normalized = Normalizer.normalize(name, Normalizer.Form.NFC);
            if (!name.equals(normalized)) {
                throw new IOException("Non-NFC ZIP entry name '" + name + "' in "
                        + this.archiveDescription + ".");
            }
            String canonicalName = normalized.toLowerCase(Locale.ROOT);
            String existing = this.canonicalNames.putIfAbsent(canonicalName, name);
            if (existing != null) {
                if (existing.equals(name)) {
                    throw new IOException("Duplicate ZIP entry '" + name + "' in "
                            + this.archiveDescription + ".");
                }
                throw new IOException("Case-folded ZIP entry collision between '" + existing
                        + "' and '" + name + "' in " + this.archiveDescription + ".");
            }

            String path = canonicalName.endsWith("/")
                    ? canonicalName.substring(0, canonicalName.length() - 1)
                    : canonicalName;
            if (directory) {
                if (this.files.contains(path)) {
                    throw fileDirectoryConflict(name);
                }
                this.directories.add(path);
            }
            else {
                if (this.directories.contains(path)) {
                    throw fileDirectoryConflict(name);
                }
                this.files.add(path);
            }
            int separator = path.lastIndexOf('/');
            while (separator > 0) {
                String parent = path.substring(0, separator);
                if (this.files.contains(parent)) {
                    throw fileDirectoryConflict(name);
                }
                this.directories.add(parent);
                separator = parent.lastIndexOf('/');
            }
        }

        private IOException fileDirectoryConflict(String name) {
            return new IOException("ZIP file/directory conflict at '" + name + "' in "
                    + this.archiveDescription + ".");
        }
    }

    record Result(Set<String> pruneEntryNames) {
    }

    record PrunableArtifact(String entryName, String groupId, String artifactId, String version,
            String sha256, boolean mavenRepositoryLayout) {

        private boolean matches(NestedLibraryMetadata metadata) {
            if (!metadata.coordinates().isEmpty()) {
                return metadata.coordinates().stream().anyMatch(this::matches);
            }
            NestedManifestIdentity manifestIdentity = metadata.manifestIdentity();
            return this.mavenRepositoryLayout || manifestIdentity != null
                    && (this.groupId + "." + this.artifactId).equals(manifestIdentity.symbolicName())
                    && this.version.equals(manifestIdentity.implementationVersion());
        }

        private boolean matches(ArtifactCoordinates coordinates) {
            return this.groupId.equals(coordinates.groupId())
                    && this.artifactId.equals(coordinates.artifactId())
                    && this.version.equals(coordinates.version());
        }

        private String gav() {
            return this.groupId + ":" + this.artifactId + ":" + this.version;
        }
    }

    private record ArtifactCoordinates(String groupId, String artifactId, String version) {

        String gav() {
            return this.groupId + ":" + this.artifactId + ":" + this.version;
        }
    }

    private record NestedManifestIdentity(String symbolicName, String implementationVersion) {
    }

    private record NestedLibraryMetadata(List<ArtifactCoordinates> coordinates,
            NestedManifestIdentity manifestIdentity, long uncompressedBytes) {
    }
}
