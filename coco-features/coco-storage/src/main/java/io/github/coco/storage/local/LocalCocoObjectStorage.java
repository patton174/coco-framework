package io.github.coco.storage.local;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import io.github.coco.storage.CocoObjectMetadata;
import io.github.coco.storage.CocoObjectPutRequest;
import io.github.coco.storage.CocoObjectResource;
import io.github.coco.storage.CocoObjectStorage;
import io.github.coco.storage.CocoStorageErrorCode;
import io.github.coco.storage.CocoStorageException;
import io.github.coco.storage.CocoStorageOverwritePolicy;
import io.github.coco.storage.CocoStorageProperties;
import org.springframework.core.io.AbstractResource;

/**
 * 安全流式本地对象存储参考实现。
 * <p>
 * 对象键只参与 manifest 内容和 SHA-256 映射，不参与本地路径。框架内部路径、根目录和 shard 目录都使用
 * {@link LinkOption#NOFOLLOW_LINKS} 属性校验，拒绝链接、Windows junction 等 reparse point 和非预期目录。
 * </p>
 * <p>
 * 本实现防御 Web 输入和配置误指向链接路径，不防御已拥有存储根目录写权限的本地恶意进程。
 * </p>
 */
public final class LocalCocoObjectStorage implements CocoObjectStorage, AutoCloseable {

    private static final int BUFFER_SIZE = 8192;

    private static final int LOCK_STRIPES = 256;

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private static final String MANIFEST_DIRECTORY = "manifests";

    private static final String BLOB_DIRECTORY = "blobs";

    private static final String ORPHAN_MARKER_SUFFIX = ".orphan";

    private static final Pattern BLOB_ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private static final Pattern SHARD = Pattern.compile("[0-9a-f]{2}");

    private final Path root;

    private final long maxSizeBytes;

    private final Set<String> allowedContentTypes;

    private final Set<String> allowedExtensions;

    private final CocoStorageOverwritePolicy defaultOverwritePolicy;

    private final Duration orphanGracePeriod;

    private final Clock clock;

    private final LocalStorageTestHook testHook;

    private final ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];

    private final ConcurrentHashMap<String, AtomicInteger> blobLeases = new ConcurrentHashMap<>();

    private final AtomicBoolean closed = new AtomicBoolean();

    private final ScheduledExecutorService cleanupExecutor;

    /**
     * 创建本地对象存储。
     * <p>
     * 构造时会执行一次孤儿恢复；后台回收由 {@code coco.storage.local.gc-interval} 控制，关闭时会再执行一次。
     * </p>
     * @param properties 已绑定的存储配置
     */
    public LocalCocoObjectStorage(CocoStorageProperties properties) {
        this(properties, Clock.systemUTC(), LocalStorageTestHook.NONE);
    }

    LocalCocoObjectStorage(CocoStorageProperties properties, Clock clock, LocalStorageTestHook testHook) {
        CocoStorageProperties checked = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.testHook = Objects.requireNonNull(testHook, "testHook must not be null");
        if (checked.getMaxSizeBytes() <= 0) {
            throw new CocoStorageException(CocoStorageErrorCode.INVALID_CONFIGURATION);
        }
        this.orphanGracePeriod = nonNegative(checked.getLocal().getOrphanGracePeriod(), "orphanGracePeriod");
        Duration gcInterval = nonNegative(checked.getLocal().getGcInterval(), "gcInterval");
        this.root = prepareRoot(checked.getLocal().getRoot());
        internalDirectory(MANIFEST_DIRECTORY);
        internalDirectory(BLOB_DIRECTORY);
        this.maxSizeBytes = checked.getMaxSizeBytes();
        this.allowedContentTypes = checked.getAllowedContentTypes().stream()
                .map(LocalCocoObjectStorage::normalizeContentType).collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.allowedExtensions = checked.getAllowedExtensions().stream()
                .map(LocalCocoObjectStorage::normalizeExtension).collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.defaultOverwritePolicy = checked.getOverwritePolicy();
        for (int index = 0; index < this.locks.length; index++) {
            this.locks[index] = new ReentrantLock();
        }
        collectGarbage();
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "coco-storage-gc");
            thread.setDaemon(true);
            return thread;
        });
        if (!gcInterval.isZero()) {
            long intervalMillis = Math.max(1L, gcInterval.toMillis());
            this.cleanupExecutor.scheduleWithFixedDelay(this::collectGarbageQuietly, intervalMillis, intervalMillis,
                    TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public CocoObjectMetadata put(CocoObjectPutRequest request) {
        ensureOpen();
        CocoObjectPutRequest checked = Objects.requireNonNull(request, "request must not be null");
        String key = normalizeKey(checked.key());
        String keyHash = keyHash(key);
        return withKeyLock(keyHash, () -> putLocked(key, keyHash, checked));
    }

    @Override
    public CocoObjectResource open(String key) {
        ensureOpen();
        String normalizedKey = normalizeKey(key);
        String keyHash = keyHash(normalizedKey);
        return withKeyLock(keyHash, () -> {
            StoredObject stored = readStoredObject(normalizedKey, keyHash);
            BlobLease lease = acquireLease(stored.blobId());
            return new CocoObjectResource(stored.metadata(), new LocalObjectResource(stored.blob(), stored.metadata(), lease));
        });
    }

    @Override
    public CocoObjectMetadata stat(String key) {
        ensureOpen();
        String normalizedKey = normalizeKey(key);
        String keyHash = keyHash(normalizedKey);
        return withKeyLock(keyHash, () -> readStoredObject(normalizedKey, keyHash).metadata());
    }

    @Override
    public boolean exists(String key) {
        ensureOpen();
        String normalizedKey = normalizeKey(key);
        String keyHash = keyHash(normalizedKey);
        return withKeyLock(keyHash, () -> findStoredObject(normalizedKey, keyHash) != null);
    }

    @Override
    public boolean delete(String key) {
        ensureOpen();
        String normalizedKey = normalizeKey(key);
        String keyHash = keyHash(normalizedKey);
        return withKeyLock(keyHash, () -> deleteLocked(normalizedKey, keyHash));
    }

    /**
     * 回收超过宽限期且未被当前 manifest 或读取快照引用的内部 blob、临时文件。
     * <p>
     * 回收会按固定顺序获取所有条带锁，因此不会与同 key 的写入、删除或 manifest 发布交叉。
     * </p>
     * @return 删除的内部文件数量
     */
    int collectGarbage() {
        return withAllLocks(this::collectGarbageLocked);
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }
        this.cleanupExecutor.shutdownNow();
        collectGarbageQuietly();
    }

    private CocoObjectMetadata putLocked(String key, String keyHash, CocoObjectPutRequest request) {
        String contentType = normalizeContentType(request.contentType());
        validateUpload(key, contentType, request.contentLength());
        CocoStorageOverwritePolicy overwritePolicy = request.overwritePolicy() == null
                ? this.defaultOverwritePolicy : request.overwritePolicy();
        StoredObject previous = findStoredObject(key, keyHash);
        if (overwritePolicy == CocoStorageOverwritePolicy.REJECT && previous != null) {
            throw new CocoStorageException(CocoStorageErrorCode.OBJECT_ALREADY_EXISTS, key);
        }

        Path temporaryBlob = null;
        Path blob = null;
        Path temporaryManifest = null;
        boolean published = false;
        try {
            Path blobs = internalDirectory(BLOB_DIRECTORY);
            temporaryBlob = Files.createTempFile(blobs, ".coco-", ".tmp");
            WriteResult result = streamToTemporaryBlob(request.content(), request.contentLength(), temporaryBlob);
            String blobId = UUID.randomUUID().toString();
            blob = blobPath(blobId);
            move(temporaryBlob, blob, false);
            temporaryBlob = null;
            Instant lastModified = Instant.ofEpochMilli(Files.getLastModifiedTime(blob, LinkOption.NOFOLLOW_LINKS).toMillis());
            CocoObjectMetadata metadata = new CocoObjectMetadata(key, result.size(), contentType, result.sha256(), lastModified);
            Path manifest = manifestPath(keyHash);
            temporaryManifest = Files.createTempFile(manifest.getParent(), ".coco-", ".tmp");
            writeManifest(temporaryManifest, metadata, blobId);
            publishManifest(temporaryManifest, manifest, key, overwritePolicy);
            published = true;
            clearOrphanMarkerQuietly(blobId);
            if (previous != null && !previous.blobId().equals(blobId)) {
                markOrphanQuietly(previous.blobId());
            }
            return metadata;
        }
        catch (CocoStorageException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw new CocoStorageException(CocoStorageErrorCode.STORAGE_IO_FAILURE, exception);
        }
        finally {
            deleteQuietly(temporaryBlob);
            deleteQuietly(temporaryManifest);
            if (!published) {
                deleteQuietly(blob);
            }
        }
    }

    private boolean deleteLocked(String key, String keyHash) {
        StoredObject stored = findStoredObject(key, keyHash);
        if (stored == null) {
            return false;
        }
        this.testHook.afterDeleteManifestObserved(key);
        try {
            Files.deleteIfExists(manifestPath(keyHash));
            markOrphanQuietly(stored.blobId());
            return true;
        }
        catch (IOException exception) {
            throw new CocoStorageException(CocoStorageErrorCode.STORAGE_IO_FAILURE, exception);
        }
    }

    private WriteResult streamToTemporaryBlob(InputStream input, Long declaredLength, Path temporaryBlob) throws IOException {
        MessageDigest digest = sha256();
        long total = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (OutputStream output = Files.newOutputStream(temporaryBlob, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            for (int read; (read = input.read(buffer)) != -1;) {
                if (read == 0) {
                    continue;
                }
                if (total > this.maxSizeBytes - read) {
                    throw new CocoStorageException(CocoStorageErrorCode.CONTENT_TOO_LARGE, this.maxSizeBytes);
                }
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                total += read;
            }
        }
        if (declaredLength != null && declaredLength.longValue() != total) {
            throw new CocoStorageException(CocoStorageErrorCode.CONTENT_LENGTH_MISMATCH, declaredLength, total);
        }
        return new WriteResult(total, HexFormat.of().formatHex(digest.digest()));
    }

    private void validateUpload(String key, String contentType, Long declaredLength) {
        if (declaredLength != null && declaredLength > this.maxSizeBytes) {
            throw new CocoStorageException(CocoStorageErrorCode.CONTENT_TOO_LARGE, this.maxSizeBytes);
        }
        if (!this.allowedContentTypes.isEmpty() && !matchesContentType(contentType)) {
            throw new CocoStorageException(CocoStorageErrorCode.CONTENT_TYPE_NOT_ALLOWED, contentType);
        }
        String extension = extension(key);
        if (!this.allowedExtensions.isEmpty() && !this.allowedExtensions.contains(extension)) {
            throw new CocoStorageException(CocoStorageErrorCode.EXTENSION_NOT_ALLOWED, extension);
        }
    }

    private boolean matchesContentType(String contentType) {
        if (this.allowedContentTypes.contains(contentType) || this.allowedContentTypes.contains("*/*")) {
            return true;
        }
        int slash = contentType.indexOf('/');
        return slash > 0 && this.allowedContentTypes.contains(contentType.substring(0, slash) + "/*");
    }

    private StoredObject findStoredObject(String key, String keyHash) {
        Path manifest = manifestPath(keyHash);
        if (!Files.exists(manifest, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        return readManifest(key, manifest);
    }

    private StoredObject readStoredObject(String key, String keyHash) {
        StoredObject stored = findStoredObject(key, keyHash);
        if (stored == null) {
            throw new CocoStorageException(CocoStorageErrorCode.OBJECT_NOT_FOUND, key);
        }
        return stored;
    }

    private StoredObject readManifest(String key, Path manifest) {
        requireSafeRegularFile(manifest, CocoStorageErrorCode.INVALID_KEY);
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(manifest, LinkOption.NOFOLLOW_LINKS)) {
            values.load(input);
            if (!key.equals(values.getProperty("key"))) {
                throw new CocoStorageException(CocoStorageErrorCode.CORRUPT_METADATA, key);
            }
            String blobId = values.getProperty("blob");
            if (blobId == null || !BLOB_ID.matcher(blobId).matches()) {
                throw new CocoStorageException(CocoStorageErrorCode.CORRUPT_METADATA, key);
            }
            CocoObjectMetadata metadata = new CocoObjectMetadata(key,
                    Long.parseLong(requiredValue(values, "size", key)), requiredValue(values, "contentType", key),
                    requiredValue(values, "sha256", key),
                    Instant.ofEpochMilli(Long.parseLong(requiredValue(values, "lastModifiedEpochMillis", key))));
            Path blob = blobPath(blobId);
            requireSafeRegularFile(blob, CocoStorageErrorCode.OBJECT_NOT_FOUND);
            return new StoredObject(metadata, blobId, blob);
        }
        catch (CocoStorageException exception) {
            throw exception;
        }
        catch (IOException | IllegalArgumentException exception) {
            throw new CocoStorageException(CocoStorageErrorCode.CORRUPT_METADATA, exception, key);
        }
    }

    private Path manifestPath(String keyHash) {
        if (!SHA256.matcher(keyHash).matches()) {
            throw new CocoStorageException(CocoStorageErrorCode.INVALID_KEY, keyHash);
        }
        Path firstShard = fixedShardDirectory(internalDirectory(MANIFEST_DIRECTORY), keyHash.substring(0, 2));
        Path secondShard = fixedShardDirectory(firstShard, keyHash.substring(2, 4));
        Path manifest = secondShard.resolve(keyHash + ".properties").normalize();
        requireContained(manifest, secondShard, CocoStorageErrorCode.INVALID_KEY);
        return manifest;
    }

    private Path blobPath(String blobId) {
        if (!BLOB_ID.matcher(blobId).matches()) {
            throw new CocoStorageException(CocoStorageErrorCode.INVALID_KEY, blobId);
        }
        Path blobs = internalDirectory(BLOB_DIRECTORY);
        Path blob = blobs.resolve(blobId + ".bin").normalize();
        requireContained(blob, blobs, CocoStorageErrorCode.INVALID_KEY);
        return blob;
    }

    private Path orphanMarkerPath(String blobId) {
        if (!BLOB_ID.matcher(blobId).matches()) {
            throw new CocoStorageException(CocoStorageErrorCode.INVALID_KEY, blobId);
        }
        Path blobs = internalDirectory(BLOB_DIRECTORY);
        Path marker = blobs.resolve(blobId + ORPHAN_MARKER_SUFFIX).normalize();
        requireContained(marker, blobs, CocoStorageErrorCode.INVALID_KEY);
        return marker;
    }

    private Path internalDirectory(String name) {
        Path verifiedRoot = requireSafeDirectory(this.root, this.root, CocoStorageErrorCode.INVALID_ROOT);
        Path directory = verifiedRoot.resolve(name).normalize();
        requireContained(directory, verifiedRoot, CocoStorageErrorCode.INVALID_ROOT);
        return createSafeDirectory(directory, verifiedRoot);
    }

    private static Path fixedShardDirectory(Path parent, String shard) {
        if (!SHARD.matcher(shard).matches()) {
            throw new CocoStorageException(CocoStorageErrorCode.INVALID_KEY, shard);
        }
        Path directory = parent.resolve(shard).normalize();
        requireContained(directory, parent, CocoStorageErrorCode.INVALID_KEY);
        return createSafeDirectory(directory, parent);
    }

    private static Path prepareRoot(Path configuredRoot) {
        if (configuredRoot == null) {
            throw new CocoStorageException(CocoStorageErrorCode.INVALID_ROOT);
        }
        Path root = configuredRoot.toAbsolutePath().normalize();
        try {
            ensureSafeExistingDirectories(root, root);
            Files.createDirectories(root);
            ensureSafeExistingDirectories(root, root);
            return requireSafeDirectory(root, root, CocoStorageErrorCode.INVALID_ROOT);
        }
        catch (CocoStorageException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw new CocoStorageException(CocoStorageErrorCode.INVALID_ROOT, exception, root);
        }
    }

    private static Path createSafeDirectory(Path directory, Path expectedRoot) {
        try {
            ensureSafeExistingDirectories(directory, expectedRoot);
            Files.createDirectories(directory);
            ensureSafeExistingDirectories(directory, expectedRoot);
            return requireSafeDirectory(directory, expectedRoot, CocoStorageErrorCode.INVALID_ROOT);
        }
        catch (CocoStorageException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw new CocoStorageException(CocoStorageErrorCode.STORAGE_IO_FAILURE, exception, directory);
        }
    }

    private static Path requireSafeDirectory(Path directory, Path expectedRoot, CocoStorageErrorCode errorCode) {
        BasicFileAttributes attributes = attributes(directory, errorCode);
        if (attributes.isSymbolicLink() || attributes.isOther() || !attributes.isDirectory()) {
            throw new CocoStorageException(errorCode, directory);
        }
        try {
            Path real = directory.toRealPath();
            // Compare real paths so platform aliases such as macOS /var and /private/var
            // do not make a directory appear outside its configured root. The directory
            // itself was already checked with NOFOLLOW_LINKS above, preserving link checks.
            Path realExpectedRoot = expectedRoot.toRealPath();
            requireContained(real, realExpectedRoot, errorCode);
            return real;
        }
        catch (IOException exception) {
            throw new CocoStorageException(errorCode, exception, directory);
        }
    }

    private static void requireSafeRegularFile(Path file, CocoStorageErrorCode errorCode) {
        BasicFileAttributes attributes = attributes(file, errorCode);
        if (attributes.isSymbolicLink() || attributes.isOther() || !attributes.isRegularFile()) {
            throw new CocoStorageException(errorCode, file);
        }
    }

    private static BasicFileAttributes attributes(Path path, CocoStorageErrorCode errorCode) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }
        catch (IOException exception) {
            throw new CocoStorageException(errorCode, exception, path);
        }
    }

    private static void ensureSafeExistingDirectories(Path path, Path expectedRoot) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path absoluteExpectedRoot = expectedRoot.toAbsolutePath().normalize();
        requireContained(absolute, absoluteExpectedRoot, CocoStorageErrorCode.INVALID_ROOT);
        Path current = absoluteExpectedRoot;
        for (Path part : absoluteExpectedRoot.relativize(absolute)) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                BasicFileAttributes attributes = Files.readAttributes(current, BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                if (attributes.isSymbolicLink() || attributes.isOther() || !attributes.isDirectory()) {
                    throw new CocoStorageException(CocoStorageErrorCode.INVALID_ROOT, current);
                }
            }
        }
    }

    private static void requireContained(Path candidate, Path expectedRoot, CocoStorageErrorCode errorCode) {
        if (!candidate.toAbsolutePath().normalize().startsWith(expectedRoot.toAbsolutePath().normalize())) {
            throw new CocoStorageException(errorCode, candidate);
        }
    }

    private int collectGarbageLocked() {
        Instant now = this.clock.instant();
        Instant cutoff = now.minus(this.orphanGracePeriod);
        Set<String> referencedBlobs = new HashSet<>();
        Set<String> blobIds = new HashSet<>();
        Set<String> markerIds = new HashSet<>();
        int deleted = collectManifestState(internalDirectory(MANIFEST_DIRECTORY), 0, referencedBlobs, cutoff);
        Path blobs = internalDirectory(BLOB_DIRECTORY);
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(blobs)) {
            for (Path entry : entries) {
                BasicFileAttributes attributes = attributes(entry, CocoStorageErrorCode.INVALID_ROOT);
                String name = entry.getFileName().toString();
                if (attributes.isSymbolicLink() || attributes.isOther() || !attributes.isRegularFile()) {
                    throw new CocoStorageException(CocoStorageErrorCode.INVALID_ROOT, entry);
                }
                boolean temporary = name.startsWith(".coco-") && name.endsWith(".tmp");
                if (temporary && olderThan(entry, cutoff)) {
                    Files.deleteIfExists(entry);
                    deleted++;
                }
                else if (name.endsWith(".bin")) {
                    blobIds.add(blobIdFromFileName(name, ".bin"));
                }
                else if (name.endsWith(ORPHAN_MARKER_SUFFIX)) {
                    markerIds.add(blobIdFromFileName(name, ORPHAN_MARKER_SUFFIX));
                }
                else if (!temporary) {
                    throw new CocoStorageException(CocoStorageErrorCode.CORRUPT_METADATA, entry);
                }
            }
            for (String blobId : blobIds) {
                if (referencedBlobs.contains(blobId)) {
                    clearOrphanMarkerQuietly(blobId);
                    continue;
                }
                java.util.Optional<Instant> orphanedAt = orphanedAtOrRecover(blobId, now);
                if (hasLease(blobId) || orphanedAt.isEmpty() || orphanedAt.get().isAfter(cutoff)) {
                    continue;
                }
                Files.deleteIfExists(blobPath(blobId));
                clearOrphanMarkerQuietly(blobId);
                deleted++;
            }
            for (String markerId : markerIds) {
                if (!blobIds.contains(markerId)) {
                    clearOrphanMarkerQuietly(markerId);
                }
            }
            return deleted;
        }
        catch (IOException exception) {
            throw new CocoStorageException(CocoStorageErrorCode.STORAGE_IO_FAILURE, exception, blobs);
        }
    }

    private int collectManifestState(Path directory, int depth, Set<String> referencedBlobs, Instant cutoff) {
        int deleted = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                BasicFileAttributes attributes = attributes(entry, CocoStorageErrorCode.INVALID_ROOT);
                String name = entry.getFileName().toString();
                if (attributes.isSymbolicLink() || attributes.isOther()) {
                    throw new CocoStorageException(CocoStorageErrorCode.INVALID_ROOT, entry);
                }
                if (attributes.isDirectory()) {
                    if (depth >= 2 || !SHARD.matcher(name).matches()) {
                        throw new CocoStorageException(CocoStorageErrorCode.CORRUPT_METADATA, entry);
                    }
                    deleted += collectManifestState(requireSafeDirectory(entry, this.root, CocoStorageErrorCode.INVALID_ROOT),
                            depth + 1, referencedBlobs, cutoff);
                }
                else if (attributes.isRegularFile()) {
                    if (name.startsWith(".coco-") && name.endsWith(".tmp")) {
                        if (olderThan(entry, cutoff)) {
                            Files.deleteIfExists(entry);
                            deleted++;
                        }
                    }
                    else if (depth == 2 && name.endsWith(".properties")
                            && SHA256.matcher(name.substring(0, name.length() - ".properties".length())).matches()) {
                        referencedBlobs.add(readBlobIdForGc(entry));
                    }
                    else {
                        throw new CocoStorageException(CocoStorageErrorCode.CORRUPT_METADATA, entry);
                    }
                }
                else {
                    throw new CocoStorageException(CocoStorageErrorCode.INVALID_ROOT, entry);
                }
            }
            return deleted;
        }
        catch (IOException exception) {
            throw new CocoStorageException(CocoStorageErrorCode.STORAGE_IO_FAILURE, exception, directory);
        }
    }

    private static String readBlobIdForGc(Path manifest) {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(manifest, LinkOption.NOFOLLOW_LINKS)) {
            values.load(input);
            String blobId = values.getProperty("blob");
            if (blobId == null || !BLOB_ID.matcher(blobId).matches()) {
                throw new CocoStorageException(CocoStorageErrorCode.CORRUPT_METADATA, manifest);
            }
            return blobId;
        }
        catch (CocoStorageException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw new CocoStorageException(CocoStorageErrorCode.CORRUPT_METADATA, exception, manifest);
        }
    }

    private static boolean olderThan(Path path, Instant cutoff) throws IOException {
        return !Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant().isAfter(cutoff);
    }

    private static String blobIdFromFileName(String name, String suffix) {
        String blobId = name.substring(0, name.length() - suffix.length());
        if (!BLOB_ID.matcher(blobId).matches()) {
            throw new CocoStorageException(CocoStorageErrorCode.CORRUPT_METADATA, name);
        }
        return blobId;
    }

    private java.util.Optional<Instant> orphanedAtOrRecover(String blobId, Instant now) {
        java.util.Optional<Instant> orphanedAt = readOrphanedAt(blobId);
        if (orphanedAt.isPresent()) {
            return orphanedAt;
        }
        try {
            writeOrphanMarker(blobId, now);
            return java.util.Optional.of(now);
        }
        catch (IOException ignored) {
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<Instant> readOrphanedAt(String blobId) {
        Path marker = orphanMarkerPath(blobId);
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            return java.util.Optional.empty();
        }
        requireSafeRegularFile(marker, CocoStorageErrorCode.CORRUPT_METADATA);
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(marker, LinkOption.NOFOLLOW_LINKS)) {
            values.load(input);
            if (!blobId.equals(values.getProperty("blob"))) {
                return java.util.Optional.empty();
            }
            long epochMillis = Long.parseLong(values.getProperty("orphanedAtEpochMillis", ""));
            if (epochMillis < 0) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(Instant.ofEpochMilli(epochMillis));
        }
        catch (IOException | IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
    }

    private void markOrphanQuietly(String blobId) {
        try {
            writeOrphanMarker(blobId, this.clock.instant());
        }
        catch (IOException | RuntimeException ignored) {
            // Manifest publication/deletion has already succeeded. GC conservatively recreates a missing marker later.
        }
    }

    private void writeOrphanMarker(String blobId, Instant orphanedAt) throws IOException {
        Path marker = orphanMarkerPath(blobId);
        Path temporaryMarker = Files.createTempFile(marker.getParent(), ".coco-", ".tmp");
        try {
            Properties values = new Properties();
            values.setProperty("blob", blobId);
            values.setProperty("orphanedAtEpochMillis", Long.toString(orphanedAt.toEpochMilli()));
            try (OutputStream output = Files.newOutputStream(temporaryMarker, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                values.store(output, null);
            }
            move(temporaryMarker, marker, true);
            temporaryMarker = null;
        }
        finally {
            deleteQuietly(temporaryMarker);
        }
    }

    private void clearOrphanMarkerQuietly(String blobId) {
        try {
            Path marker = orphanMarkerPath(blobId);
            if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
                requireSafeRegularFile(marker, CocoStorageErrorCode.CORRUPT_METADATA);
                Files.deleteIfExists(marker);
            }
        }
        catch (IOException | RuntimeException ignored) {
            // A stale marker is harmless because a referenced blob is always retained and a later GC pass retries cleanup.
        }
    }

    private BlobLease acquireLease(String blobId) {
        this.blobLeases.compute(blobId, (ignored, count) -> {
            AtomicInteger target = count == null ? new AtomicInteger() : count;
            target.incrementAndGet();
            return target;
        });
        return new BlobLease(blobId);
    }

    private boolean hasLease(String blobId) {
        AtomicInteger count = this.blobLeases.get(blobId);
        return count != null && count.get() > 0;
    }

    private void releaseLease(String blobId) {
        this.blobLeases.computeIfPresent(blobId, (ignored, count) -> count.decrementAndGet() == 0 ? null : count);
    }

    private void ensureOpen() {
        if (this.closed.get()) {
            throw new IllegalStateException("Coco local object storage is closed");
        }
    }

    private <T> T withKeyLock(String keyHash, Supplier<T> action) {
        ReentrantLock lock = this.locks[Integer.parseInt(keyHash.substring(0, 2), 16) % LOCK_STRIPES];
        lock.lock();
        try {
            return action.get();
        }
        finally {
            lock.unlock();
        }
    }

    private <T> T withAllLocks(Supplier<T> action) {
        for (ReentrantLock lock : this.locks) {
            lock.lock();
        }
        try {
            return action.get();
        }
        finally {
            for (int index = this.locks.length - 1; index >= 0; index--) {
                this.locks[index].unlock();
            }
        }
    }

    private void collectGarbageQuietly() {
        try {
            collectGarbage();
        }
        catch (RuntimeException ignored) {
            // A later periodic or shutdown pass retries recovery without interrupting application threads.
        }
    }

    private static Duration nonNegative(Duration value, String name) {
        Duration checked = value == null ? Duration.ZERO : value;
        if (checked.isNegative()) {
            throw new CocoStorageException(CocoStorageErrorCode.INVALID_CONFIGURATION, name);
        }
        return checked;
    }

    private static String keyHash(String key) {
        return HexFormat.of().formatHex(sha256().digest(key.getBytes(StandardCharsets.UTF_8)));
    }

    private static String normalizeKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank() || rawKey.indexOf('\\') >= 0 || rawKey.startsWith("/")
                || rawKey.startsWith("//") || rawKey.matches("^[A-Za-z]:.*")) {
            throw new CocoStorageException(CocoStorageErrorCode.INVALID_KEY, rawKey);
        }
        String[] segments = rawKey.split("/", -1);
        for (String segment : segments) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment) || containsUnsafeFileNameCharacter(segment)) {
                throw new CocoStorageException(CocoStorageErrorCode.INVALID_KEY, rawKey);
            }
        }
        return String.join("/", segments);
    }

    private static boolean containsUnsafeFileNameCharacter(String segment) {
        for (int index = 0; index < segment.length(); index++) {
            char character = segment.charAt(index);
            if (Character.isISOControl(character) || "<>:\\|?*\"".indexOf(character) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeContentType(String value) {
        String contentType = value == null || value.isBlank() ? DEFAULT_CONTENT_TYPE : value.trim().toLowerCase(Locale.ROOT);
        int parameterSeparator = contentType.indexOf(';');
        if (parameterSeparator >= 0) {
            contentType = contentType.substring(0, parameterSeparator).trim();
        }
        if (!contentType.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+") && !contentType.matches("[a-z0-9!#$&^_.+-]+/\\*")) {
            throw new CocoStorageException(CocoStorageErrorCode.CONTENT_TYPE_NOT_ALLOWED, value);
        }
        return contentType;
    }

    private static String normalizeExtension(String value) {
        if (value == null) {
            throw new CocoStorageException(CocoStorageErrorCode.EXTENSION_NOT_ALLOWED);
        }
        String extension = value.trim().toLowerCase(Locale.ROOT);
        if (extension.startsWith(".")) {
            extension = extension.substring(1);
        }
        if (extension.isBlank() || !extension.matches("[a-z0-9][a-z0-9+_-]*")) {
            throw new CocoStorageException(CocoStorageErrorCode.EXTENSION_NOT_ALLOWED, value);
        }
        return extension;
    }

    private static String extension(String key) {
        String name = key.substring(key.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot <= 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private static void move(Path source, Path target, boolean replace) throws IOException {
        try {
            if (replace) {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            else {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            }
        }
        catch (AtomicMoveNotSupportedException exception) {
            if (replace) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            else {
                Files.move(source, target);
            }
        }
    }

    private static void publishManifest(Path source, Path target, String key, CocoStorageOverwritePolicy overwritePolicy)
            throws IOException {
        if (overwritePolicy == CocoStorageOverwritePolicy.REPLACE) {
            move(source, target, true);
            return;
        }
        try {
            Files.createLink(target, source);
            deleteQuietly(source);
        }
        catch (FileAlreadyExistsException exception) {
            throw new CocoStorageException(CocoStorageErrorCode.OBJECT_ALREADY_EXISTS, exception, key);
        }
        catch (UnsupportedOperationException exception) {
            moveWithoutReplace(source, target, key);
        }
        catch (IOException exception) {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new CocoStorageException(CocoStorageErrorCode.OBJECT_ALREADY_EXISTS, exception, key);
            }
            moveWithoutReplace(source, target, key);
        }
    }

    private static void moveWithoutReplace(Path source, Path target, String key) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException exception) {
            try {
                Files.move(source, target);
            }
            catch (FileAlreadyExistsException conflict) {
                throw new CocoStorageException(CocoStorageErrorCode.OBJECT_ALREADY_EXISTS, conflict, key);
            }
        }
        catch (FileAlreadyExistsException exception) {
            throw new CocoStorageException(CocoStorageErrorCode.OBJECT_ALREADY_EXISTS, exception, key);
        }
    }

    private static void writeManifest(Path target, CocoObjectMetadata metadata, String blobId) throws IOException {
        Properties values = new Properties();
        values.setProperty("key", metadata.key());
        values.setProperty("size", Long.toString(metadata.size()));
        values.setProperty("contentType", metadata.contentType());
        values.setProperty("sha256", metadata.sha256());
        values.setProperty("lastModifiedEpochMillis", Long.toString(metadata.lastModified().toEpochMilli()));
        values.setProperty("blob", blobId);
        try (OutputStream output = Files.newOutputStream(target, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            values.store(output, null);
        }
    }

    private static String requiredValue(Properties values, String name, String key) {
        String value = values.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new CocoStorageException(CocoStorageErrorCode.CORRUPT_METADATA, key);
        }
        return value;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        }
        catch (IOException ignored) {
            // The original failure remains the useful signal; recovery retries stale internal files after the grace period.
        }
    }

    private record WriteResult(long size, String sha256) {
    }

    private record StoredObject(CocoObjectMetadata metadata, String blobId, Path blob) {
    }

    private final class BlobLease {

        private final String blobId;

        private final AtomicBoolean released = new AtomicBoolean();

        private BlobLease(String blobId) {
            this.blobId = blobId;
        }

        private BlobLease retain() {
            if (this.released.get()) {
                throw new IllegalStateException("Coco object resource is closed");
            }
            return acquireLease(this.blobId);
        }

        private void release() {
            if (this.released.compareAndSet(false, true)) {
                releaseLease(this.blobId);
            }
        }
    }

    private final class LocalObjectResource extends AbstractResource implements AutoCloseable {

        private final Path blob;

        private final CocoObjectMetadata metadata;

        private final BlobLease pendingLease;

        private final AtomicBoolean pendingReleased = new AtomicBoolean();

        private final AtomicBoolean resourceClosed = new AtomicBoolean();

        private LocalObjectResource(Path blob, CocoObjectMetadata metadata, BlobLease pendingLease) {
            this.blob = blob;
            this.metadata = metadata;
            this.pendingLease = pendingLease;
        }

        @Override
        public synchronized InputStream getInputStream() throws IOException {
            if (this.resourceClosed.get()) {
                throw new IOException("Coco object resource is closed");
            }
            BlobLease streamLease = acquireLease(this.pendingLease.blobId);
            try {
                requireSafeRegularFile(this.blob, CocoStorageErrorCode.OBJECT_NOT_FOUND);
                InputStream input = Files.newInputStream(this.blob, LinkOption.NOFOLLOW_LINKS);
                releasePendingLease();
                return new FilterInputStream(input) {
                    private final AtomicBoolean inputClosed = new AtomicBoolean();

                    @Override
                    public void close() throws IOException {
                        if (this.inputClosed.compareAndSet(false, true)) {
                            try {
                                super.close();
                            }
                            finally {
                                streamLease.release();
                            }
                        }
                    }
                };
            }
            catch (IOException | RuntimeException exception) {
                streamLease.release();
                throw exception;
            }
        }

        @Override
        public long contentLength() {
            return this.metadata.size();
        }

        @Override
        public String getFilename() {
            String key = this.metadata.key();
            return key.substring(key.lastIndexOf('/') + 1);
        }

        @Override
        public String getDescription() {
            return "Coco object resource [" + this.metadata.key() + "]";
        }

        @Override
        public synchronized void close() {
            if (this.resourceClosed.compareAndSet(false, true)) {
                releasePendingLease();
            }
        }

        private void releasePendingLease() {
            if (this.pendingReleased.compareAndSet(false, true)) {
                this.pendingLease.release();
            }
        }
    }
}

@FunctionalInterface
interface LocalStorageTestHook {

    LocalStorageTestHook NONE = key -> {
    };

    /**
     * 删除操作已经读取到旧 manifest，且仍持有该 key 的条带锁时调用。
     * @param key 已规范化的对象键
     */
    void afterDeleteManifestObserved(String key);
}
