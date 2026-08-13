package io.github.coco.feature.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 本地文件系统对象存储参考实现。
 * <p>所有路径逐级以 {@link LinkOption#NOFOLLOW_LINKS} 检查；在 Windows 上 reparse point 按符号链接同样拒绝。
 * 不能提供抗恶意并发替换目录的文件描述符级保证，因此根目录应由受信任进程独占。</p>
 */
public final class LocalCocoObjectStorage implements CocoObjectStorage {
    private static final String INTERNAL = ".coco-storage";
    private static final String DATA = "objects";
    private static final String META = "metadata";
    private static final String TEMP = "tmp";
    private static final String TOKEN_PREFIX = "v1:";
    private final Path root;
    private final Path dataRoot;
    private final Path metadataRoot;
    private final Path tempRoot;
    private final boolean overwrite;
    private final long maxObjectSize;
    private final int listMaxSize;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LocalCocoObjectStorage(CocoStorageProperties.Local properties) {
        try {
            this.root = Path.of(properties.getRoot()).toAbsolutePath().normalize();
            Files.createDirectories(this.root);
            requireSafeDirectory(this.root);
            Path internal = safeChild(this.root, INTERNAL);
            this.dataRoot = safeChild(internal, DATA); this.metadataRoot = safeChild(internal, META); this.tempRoot = safeChild(internal, TEMP);
            Files.createDirectories(this.dataRoot); Files.createDirectories(this.metadataRoot); Files.createDirectories(this.tempRoot);
            requireSafeDirectory(this.dataRoot); requireSafeDirectory(this.metadataRoot); requireSafeDirectory(this.tempRoot);
        } catch (IOException exception) { throw new IllegalStateException("cannot initialize local object storage", exception); }
        this.overwrite = properties.isOverwrite(); this.maxObjectSize = properties.getMaxObjectSize(); this.listMaxSize = properties.getListMaxSize();
        if (maxObjectSize < 0 || listMaxSize < 1 || listMaxSize > 1000) throw new IllegalArgumentException("invalid storage limits");
    }

    @Override public CocoObjectStat put(CocoObjectWriteRequest request) throws IOException {
        CocoObjectKey.validate(request.key());
        if (request.contentLength() != null && request.contentLength() > maxObjectSize) throw new IOException("object exceeds maximum size");
        Path data = dataPath(request.key()); Path meta = metadataPath(request.key()); ensureParent(data, dataRoot); ensureParent(meta, metadataRoot);
        if (!overwrite && Files.exists(data, LinkOption.NOFOLLOW_LINKS)) throw new IOException("object already exists");
        Path temp = Files.createTempFile(tempRoot, "write-", ".tmp"); long length = 0;
        try {
            try (var output = Files.newOutputStream(temp, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[8192]; int read;
                while ((read = request.inputStream().read(buffer)) != -1) {
                    length += read;
                    if (length > maxObjectSize) throw new IOException("object exceeds maximum size");
                    output.write(buffer, 0, read);
                }
            }
            if (request.contentLength() != null && request.contentLength() != length) throw new IOException("content length mismatch");
            CocoObjectMetadata metadata = new CocoObjectMetadata(request.key(), length, request.contentType(), request.metadata(), Instant.now());
            move(temp, data, overwrite); writeMetadata(meta, metadata); return CocoObjectStat.found(metadata);
        } catch (IOException | RuntimeException exception) { Files.deleteIfExists(temp); throw exception; }
    }

    @Override public Optional<CocoObjectReadResult> get(String key) throws IOException {
        CocoObjectStat stat = stat(key); if (!stat.found()) return Optional.empty();
        Path data = dataPath(key); ensureSafeParents(data, dataRoot); ensureSafeExistingFile(data); return Optional.of(new CocoObjectReadResult(stat.metadata(), Files.newInputStream(data, LinkOption.NOFOLLOW_LINKS)));
    }
    @Override public CocoObjectStat stat(String key) throws IOException {
        CocoObjectKey.validate(key); Path data = dataPath(key); Path meta = metadataPath(key);
        ensureSafeParents(data, dataRoot); ensureSafeParents(meta, metadataRoot);
        if (!Files.isRegularFile(data, LinkOption.NOFOLLOW_LINKS) || !Files.isRegularFile(meta, LinkOption.NOFOLLOW_LINKS)) return CocoObjectStat.notFound(key);
        ensureSafeExistingFile(data); ensureSafeExistingFile(meta); StoredMetadata stored = objectMapper.readValue(meta.toFile(), StoredMetadata.class);
        CocoObjectMetadata value = new CocoObjectMetadata(stored.key(), stored.contentLength(), stored.contentType(), stored.metadata(), Instant.ofEpochMilli(stored.lastModifiedEpochMillis()));
        if (!key.equals(value.key()) || value.contentLength() != Files.size(data)) throw new IOException("object metadata is inconsistent");
        return CocoObjectStat.found(value);
    }
    @Override public boolean delete(String key) throws IOException {
        CocoObjectKey.validate(key); Path data = dataPath(key); Path meta = metadataPath(key); ensureSafeParents(data, dataRoot); ensureSafeParents(meta, metadataRoot); boolean existed = Files.deleteIfExists(data); Files.deleteIfExists(meta); return existed;
    }
    @Override public CocoObjectListResult list(String prefix, int limit, String continuationToken) throws IOException {
        if (prefix != null && !prefix.isEmpty()) CocoObjectKey.validate(prefix.endsWith("/") ? prefix + "x" : prefix);
        if (limit < 1 || limit > listMaxSize) throw new IllegalArgumentException("invalid list limit"); String after = decodeToken(continuationToken);
        List<String> keys;
        try (Stream<Path> paths = Files.walk(metadataRoot)) {
            keys = paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).map(this::keyFromMetadataPath).filter(key -> prefix == null || key.startsWith(prefix)).filter(key -> after == null || key.compareTo(after) > 0).sorted().toList();
        }
        List<CocoObjectStat> values = new ArrayList<>(); for (String key : keys) { if (values.size() == limit) break; CocoObjectStat value = stat(key); if (value.found()) values.add(value); }
        String next = keys.size() > values.size() && !values.isEmpty() ? encodeToken(values.get(values.size() - 1).metadata().key()) : null;
        return new CocoObjectListResult(values, next);
    }
    private Path dataPath(String key) throws IOException { return objectPath(dataRoot, key); }
    private Path metadataPath(String key) throws IOException { return objectPath(metadataRoot, key + ".json"); }
    private Path objectPath(Path base, String key) throws IOException { Path value = base.resolve(key).normalize(); if (!value.startsWith(base)) throw new IOException("object path escapes root"); return value; }
    private static Path safeChild(Path parent, String name) throws IOException { Path value = parent.resolve(name); if (Files.exists(value, LinkOption.NOFOLLOW_LINKS)) requireSafeDirectory(value); return value; }
    private static void requireSafeDirectory(Path directory) throws IOException { if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("storage directory is unsafe"); }
    private void ensureParent(Path file, Path base) throws IOException { Path parent = file.getParent(); List<Path> chain = new ArrayList<>(); while (!parent.equals(base)) { chain.add(parent); parent = parent.getParent(); } requireSafeDirectory(base); for (int i = chain.size() - 1; i >= 0; i--) { Path directory = chain.get(i); if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) requireSafeDirectory(directory); else { Files.createDirectory(directory); requireSafeDirectory(directory); } } }
    private static void ensureSafeParents(Path file, Path base) throws IOException { Path parent = file.getParent(); while (true) { requireSafeDirectory(parent); if (parent.equals(base)) return; parent = parent.getParent(); } }
    private static void ensureSafeExistingFile(Path file) throws IOException { if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("object file is unsafe"); }
    private void writeMetadata(Path destination, CocoObjectMetadata metadata) throws IOException { Path temp = Files.createTempFile(tempRoot, "metadata-", ".tmp"); try { objectMapper.writeValue(temp.toFile(), new StoredMetadata(metadata.key(), metadata.contentLength(), metadata.contentType(), metadata.metadata(), metadata.lastModified().toEpochMilli())); move(temp, destination, true); } catch (IOException | RuntimeException exception) { Files.deleteIfExists(temp); throw exception; } }
    private static void move(Path source, Path destination, boolean replace) throws IOException { try { if (replace) Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); else Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE); } catch (AtomicMoveNotSupportedException exception) { if (replace) Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING); else Files.move(source, destination); } }
    private String keyFromMetadataPath(Path path) { return metadataRoot.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/").replaceFirst("\\.json$", ""); }
    private static String encodeToken(String key) { return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8)); }
    private static String decodeToken(String token) { if (token == null || token.isEmpty()) return null; if (!token.startsWith(TOKEN_PREFIX)) throw new IllegalArgumentException("invalid continuation token"); try { String key = new String(Base64.getUrlDecoder().decode(token.substring(TOKEN_PREFIX.length())), StandardCharsets.UTF_8); CocoObjectKey.validate(key); return key; } catch (IllegalArgumentException exception) { throw new IllegalArgumentException("invalid continuation token", exception); } }
    private record StoredMetadata(String key, long contentLength, String contentType, java.util.Map<String, String> metadata,
            long lastModifiedEpochMillis) { }
}
