package io.github.coco.storage.local;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.core.io.Resource;

/**
 * 安全流式本地对象存储参考实现。
 * <p>
 * 内容保存在不可变 blob 中，对象键只映射到小型 manifest。写入完成后只原子替换 manifest，读取方始终获得已发布的
 * 独立 blob，避免将大对象载入内存或读取半写入内容。
 * </p>
 */
public final class LocalCocoObjectStorage implements CocoObjectStorage {

    private static final int BUFFER_SIZE = 8192;

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private static final Pattern BLOB_ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final Path root;

    private final Path objectRoot;

    private final Path blobRoot;

    private final long maxSizeBytes;

    private final Set<String> allowedContentTypes;

    private final Set<String> allowedExtensions;

    private final CocoStorageOverwritePolicy defaultOverwritePolicy;

    /**
     * 创建本地对象存储。
     * @param properties 已绑定的存储配置
     */
    public LocalCocoObjectStorage(CocoStorageProperties properties) {
        CocoStorageProperties checked = Objects.requireNonNull(properties, "properties must not be null");
        if (checked.getMaxSizeBytes() <= 0) {
            throw new CocoStorageException(CocoStorageErrorCode.CONTENT_TOO_LARGE);
        }
        this.root = prepareRoot(checked.getLocal().getRoot());
        this.objectRoot = createSafeDirectory(this.root.resolve("objects"));
        this.blobRoot = createSafeDirectory(this.root.resolve("blobs"));
        this.maxSizeBytes = checked.getMaxSizeBytes();
        this.allowedContentTypes = checked.getAllowedContentTypes().stream()
                .map(LocalCocoObjectStorage::normalizeContentType).collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.allowedExtensions = checked.getAllowedExtensions().stream()
                .map(LocalCocoObjectStorage::normalizeExtension).collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.defaultOverwritePolicy = checked.getOverwritePolicy();
    }

    @Override
    public CocoObjectMetadata put(CocoObjectPutRequest request) {
        CocoObjectPutRequest checked = Objects.requireNonNull(request, "request must not be null");
        String key = normalizeKey(checked.key());
        String contentType = normalizeContentType(checked.contentType());
        validateUpload(key, contentType, checked.contentLength());
        CocoStorageOverwritePolicy overwritePolicy = checked.overwritePolicy() == null
                ? this.defaultOverwritePolicy : checked.overwritePolicy();
        Path manifest = manifestPath(key);
        if (overwritePolicy == CocoStorageOverwritePolicy.REJECT && Files.exists(manifest, LinkOption.NOFOLLOW_LINKS)) {
            rejectSymlink(manifest);
            throw new CocoStorageException(CocoStorageErrorCode.OBJECT_ALREADY_EXISTS, key);
        }

        Path temporaryBlob = null;
        Path blob = null;
        Path temporaryManifest = null;
        boolean published = false;
        try {
            temporaryBlob = Files.createTempFile(this.blobRoot, ".coco-", ".tmp");
            WriteResult result = streamToTemporaryBlob(checked.content(), checked.contentLength(), temporaryBlob);
            String blobId = UUID.randomUUID().toString();
            blob = blobPath(blobId);
            move(temporaryBlob, blob, false);
            temporaryBlob = null;
            Instant lastModified = Instant.ofEpochMilli(
                    Files.getLastModifiedTime(blob, LinkOption.NOFOLLOW_LINKS).toMillis());
            CocoObjectMetadata metadata = new CocoObjectMetadata(key, result.size(), contentType, result.sha256(), lastModified);
            temporaryManifest = Files.createTempFile(manifest.getParent(), ".coco-", ".tmp");
            writeManifest(temporaryManifest, metadata, blobId);
            publishManifest(temporaryManifest, manifest, key, overwritePolicy);
            published = true;
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

    @Override
    public CocoObjectResource open(String key) {
        StoredObject stored = readStoredObject(normalizeKey(key));
        return new CocoObjectResource(stored.metadata(), new LocalObjectResource(stored.blob(), stored.metadata()));
    }

    @Override
    public CocoObjectMetadata stat(String key) {
        return readStoredObject(normalizeKey(key)).metadata();
    }

    @Override
    public boolean exists(String key) {
        try {
            readStoredObject(normalizeKey(key));
            return true;
        }
        catch (CocoStorageException exception) {
            if (CocoStorageErrorCode.OBJECT_NOT_FOUND.code().equals(exception.code())) {
                return false;
            }
            throw exception;
        }
    }

    @Override
    public boolean delete(String key) {
        String normalizedKey = normalizeKey(key);
        Path manifest = manifestPath(normalizedKey);
        if (!Files.exists(manifest, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        StoredObject stored = readStoredObject(normalizedKey);
        try {
            Files.deleteIfExists(stored.blob());
            return Files.deleteIfExists(manifest);
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

    private StoredObject readStoredObject(String key) {
        Path manifest = manifestPath(key);
        if (!Files.exists(manifest, LinkOption.NOFOLLOW_LINKS)) {
            throw new CocoStorageException(CocoStorageErrorCode.OBJECT_NOT_FOUND, key);
        }
        rejectSymlink(manifest);
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
                    Long.parseLong(requiredValue(values, "size", key)),
                    requiredValue(values, "contentType", key), requiredValue(values, "sha256", key),
                    Instant.ofEpochMilli(Long.parseLong(requiredValue(values, "lastModifiedEpochMillis", key))));
            Path blob = blobPath(blobId);
            if (!Files.isRegularFile(blob, LinkOption.NOFOLLOW_LINKS)) {
                throw new CocoStorageException(CocoStorageErrorCode.OBJECT_NOT_FOUND, key);
            }
            rejectSymlink(blob);
            return new StoredObject(metadata, blob);
        }
        catch (CocoStorageException exception) {
            throw exception;
        }
        catch (IOException | IllegalArgumentException exception) {
            throw new CocoStorageException(CocoStorageErrorCode.CORRUPT_METADATA, exception, key);
        }
    }

    private Path manifestPath(String key) {
        Path manifest = this.objectRoot.resolve(key + ".properties").normalize();
        validateContained(manifest, this.objectRoot);
        createSafeDirectory(manifest.getParent());
        return manifest;
    }

    private Path blobPath(String blobId) {
        Path blob = this.blobRoot.resolve(blobId + ".bin").normalize();
        validateContained(blob, this.blobRoot);
        return blob;
    }

    private static Path prepareRoot(Path configuredRoot) {
        if (configuredRoot == null) {
            throw new CocoStorageException(CocoStorageErrorCode.INVALID_ROOT);
        }
        Path root = configuredRoot.toAbsolutePath().normalize();
        try {
            ensureNoSymlinkInExistingPath(root);
            Files.createDirectories(root);
            ensureNoSymlinkInExistingPath(root);
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new CocoStorageException(CocoStorageErrorCode.INVALID_ROOT, root);
            }
            return root.toRealPath(LinkOption.NOFOLLOW_LINKS);
        }
        catch (CocoStorageException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw new CocoStorageException(CocoStorageErrorCode.INVALID_ROOT, exception, root);
        }
    }

    private Path createSafeDirectory(Path directory) {
        validateContained(directory, this.root);
        try {
            ensureNoSymlinkInExistingPath(directory);
            Files.createDirectories(directory);
            ensureNoSymlinkInExistingPath(directory);
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new CocoStorageException(CocoStorageErrorCode.INVALID_ROOT, directory);
            }
            Path real = directory.toRealPath(LinkOption.NOFOLLOW_LINKS);
            validateContained(real, this.root);
            return real;
        }
        catch (CocoStorageException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw new CocoStorageException(CocoStorageErrorCode.STORAGE_IO_FAILURE, exception, directory);
        }
    }

    private static void ensureNoSymlinkInExistingPath(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        for (Path part : absolute) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new CocoStorageException(CocoStorageErrorCode.INVALID_ROOT, current);
            }
        }
    }

    private static void validateContained(Path candidate, Path expectedRoot) {
        if (!candidate.toAbsolutePath().normalize().startsWith(expectedRoot.toAbsolutePath().normalize())) {
            throw new CocoStorageException(CocoStorageErrorCode.INVALID_KEY, candidate);
        }
    }

    private static void rejectSymlink(Path path) {
        if (Files.isSymbolicLink(path)) {
            throw new CocoStorageException(CocoStorageErrorCode.INVALID_KEY, path);
        }
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
            // The original failure remains the useful signal; a later retry can remove an orphaned temporary file.
        }
    }

    private record WriteResult(long size, String sha256) {
    }

    private record StoredObject(CocoObjectMetadata metadata, Path blob) {
    }

    private static final class LocalObjectResource extends AbstractResource {

        private final Path blob;

        private final CocoObjectMetadata metadata;

        private LocalObjectResource(Path blob, CocoObjectMetadata metadata) {
            this.blob = blob;
            this.metadata = metadata;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(this.blob, LinkOption.NOFOLLOW_LINKS);
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
    }
}
