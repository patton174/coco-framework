package io.github.coco.feature.storage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 本地文件系统对象存储参考实现。
 * <p>
 * 每个对象都是一个版本化容器文件，header 和 body 完整写入后才发布到对象路径。
 * 所有 root 下的目录和对象路径都使用 {@link BasicFileAttributes} 与
 * {@link LinkOption#NOFOLLOW_LINKS} 检查；符号链接和 Windows reparse point 均保守拒绝。
 * </p>
 * <p>
 * 该实现不能提供抗恶意并发替换目录的文件描述符级保证，因此根目录应由受信任进程独占。
 * </p>
 */
public final class LocalCocoObjectStorage implements CocoObjectStorage {
    private static final String INTERNAL = ".coco-storage";
    private static final String DATA = "objects";
    private static final String TEMP = "tmp";
    private static final String TOKEN_PREFIX = "v1:";
    private static final byte[] MAGIC = "COCOOBJ1".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 1;
    private static final int PREFIX_LENGTH = MAGIC.length + Integer.BYTES + Integer.BYTES + Long.BYTES;
    private static final int MAX_HEADER_LENGTH = 64 * 1024;
    private static final int MAX_CONTENT_TYPE_LENGTH = 1024;
    private static final Set<String> HEADER_FIELDS = Set.of("key", "bodyLength", "contentType", "metadata",
            "lastModifiedEpochMillis");

    private final Path root;
    private final Path dataRoot;
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
            Path internal = safeDirectoryChild(this.root, INTERNAL);
            this.dataRoot = safeDirectoryChild(internal, DATA);
            this.tempRoot = safeDirectoryChild(internal, TEMP);
            requireSafeDirectory(this.dataRoot);
            requireSafeDirectory(this.tempRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot initialize local object storage", exception);
        }
        this.overwrite = properties.isOverwrite();
        this.maxObjectSize = properties.getMaxObjectSize();
        this.listMaxSize = properties.getListMaxSize();
        if (maxObjectSize < 0 || listMaxSize < 1 || listMaxSize > 1000) {
            throw new IllegalArgumentException("invalid storage limits");
        }
    }

    @Override
    public CocoObjectStat put(CocoObjectWriteRequest request) throws IOException {
        CocoObjectKey.validate(request.key());
        validateContentType(request.contentType());
        if (request.contentLength() != null && request.contentLength() > maxObjectSize) {
            throw new IOException("object exceeds maximum size");
        }

        Path destination = dataPath(request.key());
        ensureParent(destination, dataRoot);
        ensureSafeTarget(destination);

        Path bodyStaging = Files.createTempFile(tempRoot, ".body-", ".staging");
        long length = 0;
        try {
            try (var output = Files.newOutputStream(bodyStaging, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = request.inputStream().read(buffer)) != -1) {
                    length += read;
                    if (length > maxObjectSize) {
                        throw new IOException("object exceeds maximum size");
                    }
                    output.write(buffer, 0, read);
                }
            }
            if (request.contentLength() != null && request.contentLength() != length) {
                throw new IOException("content length mismatch");
            }

            CocoObjectMetadata metadata = new CocoObjectMetadata(request.key(), length, request.contentType(),
                    request.metadata(), Instant.now());
            Path containerStaging = Files.createTempFile(destination.getParent(), ".staging-", ".tmp");
            try {
                writeContainer(containerStaging, bodyStaging, metadata);
                publish(containerStaging, destination);
                containerStaging = null;
            } finally {
                if (containerStaging != null) {
                    Files.deleteIfExists(containerStaging);
                }
            }
            return CocoObjectStat.found(metadata);
        } finally {
            Files.deleteIfExists(bodyStaging);
        }
    }

    @Override
    public Optional<CocoObjectReadResult> get(String key) throws IOException {
        CocoObjectKey.validate(key);
        Path object = dataPath(key);
        ensureSafeParents(object, dataRoot);
        StoredContainer container = readContainer(object, key);
        if (container == null) {
            return Optional.empty();
        }
        InputStream input = null;
        try {
            input = Files.newInputStream(object, LinkOption.NOFOLLOW_LINKS);
            skipFully(input, container.bodyOffset());
            return Optional.of(new CocoObjectReadResult(container.metadata(),
                    new BoundedInputStream(input, container.bodyLength())));
        } catch (IOException | RuntimeException exception) {
            if (input != null) {
                input.close();
            }
            throw exception;
        }
    }

    @Override
    public CocoObjectStat stat(String key) throws IOException {
        CocoObjectKey.validate(key);
        Path object = dataPath(key);
        ensureSafeParents(object, dataRoot);
        StoredContainer container = readContainer(object, key);
        return container == null ? CocoObjectStat.notFound(key) : CocoObjectStat.found(container.metadata());
    }

    @Override
    public boolean delete(String key) throws IOException {
        CocoObjectKey.validate(key);
        Path object = dataPath(key);
        ensureSafeParents(object, dataRoot);
        BasicFileAttributes attributes = safeAttributesIfExists(object);
        if (attributes == null) {
            return false;
        }
        requireSafeFile(attributes);
        return Files.deleteIfExists(object);
    }

    @Override
    public CocoObjectListResult list(String prefix, int limit, String continuationToken) throws IOException {
        if (prefix != null && !prefix.isEmpty()) {
            CocoObjectKey.validate(prefix.endsWith("/") ? prefix + "x" : prefix);
        }
        if (limit < 1 || limit > listMaxSize) {
            throw new IllegalArgumentException("invalid list limit");
        }
        String after = decodeToken(continuationToken);
        requireSafeDirectory(dataRoot);

        List<String> keys = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(dataRoot)) {
            Iterator<Path> iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                BasicFileAttributes attributes = safeAttributes(path);
                if (attributes.isDirectory()) {
                    continue;
                }
                if (!attributes.isRegularFile()) {
                    throw new IOException("storage path is not a regular file");
                }
                if (path.getFileName().toString().startsWith(".staging-")) {
                    continue;
                }
                String key = dataRoot.relativize(path).toString()
                        .replace(path.getFileSystem().getSeparator(), "/");
                CocoObjectKey.validate(key);
                if ((prefix == null || key.startsWith(prefix)) && (after == null || key.compareTo(after) > 0)) {
                    keys.add(key);
                }
            }
        }
        keys.sort(Comparator.naturalOrder());

        List<CocoObjectStat> candidates = new ArrayList<>();
        for (String key : keys) {
            CocoObjectStat value = stat(key);
            if (value.found()) {
                candidates.add(value);
            }
        }
        List<CocoObjectStat> values = candidates.subList(0, Math.min(limit, candidates.size()));
        String next = candidates.size() > values.size() && !values.isEmpty()
                ? encodeToken(values.get(values.size() - 1).metadata().key()) : null;
        return new CocoObjectListResult(List.copyOf(values), next);
    }

    private Path dataPath(String key) throws IOException {
        return objectPath(dataRoot, key);
    }

    private Path objectPath(Path base, String key) throws IOException {
        Path value = base.resolve(key).normalize();
        if (!value.startsWith(base)) {
            throw new IOException("object path escapes root");
        }
        return value;
    }

    private static Path safeDirectoryChild(Path parent, String name) throws IOException {
        Path value = parent.resolve(name);
        BasicFileAttributes attributes = safeAttributesIfExists(value);
        if (attributes == null) {
            Files.createDirectory(value);
        }
        requireSafeDirectory(value);
        return value;
    }

    private static void requireSafeDirectory(Path directory) throws IOException {
        BasicFileAttributes attributes = safeAttributes(directory);
        if (!attributes.isDirectory()) {
            throw new IOException("storage directory is unsafe");
        }
    }

    private void ensureParent(Path file, Path base) throws IOException {
        Path parent = file.getParent();
        List<Path> chain = new ArrayList<>();
        while (!parent.equals(base)) {
            chain.add(parent);
            parent = parent.getParent();
            if (parent == null || !parent.startsWith(base)) {
                throw new IOException("object path escapes root");
            }
        }
        requireSafeDirectory(base);
        for (int index = chain.size() - 1; index >= 0; index--) {
            Path directory = chain.get(index);
            BasicFileAttributes attributes = safeAttributesIfExists(directory);
            if (attributes == null) {
                try {
                    Files.createDirectory(directory);
                } catch (FileAlreadyExistsException exception) {
                    // A concurrent creator is acceptable only after the same safety check.
                }
            }
            requireSafeDirectory(directory);
        }
    }

    private static void ensureSafeParents(Path file, Path base) throws IOException {
        Path parent = file.getParent();
        while (true) {
            requireSafeDirectory(parent);
            if (parent.equals(base)) {
                return;
            }
            parent = parent.getParent();
            if (parent == null || !parent.startsWith(base)) {
                throw new IOException("object path escapes root");
            }
        }
    }

    private static void ensureSafeTarget(Path target) throws IOException {
        BasicFileAttributes attributes = safeAttributesIfExists(target);
        if (attributes != null) {
            requireSafeFile(attributes);
        }
    }

    private static BasicFileAttributes safeAttributes(Path path) throws IOException {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            rejectUnsafeAttributes(attributes);
            return attributes;
        } catch (NoSuchFileException exception) {
            throw new IOException("storage path disappeared", exception);
        }
    }

    private static BasicFileAttributes safeAttributesIfExists(Path path) throws IOException {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            rejectUnsafeAttributes(attributes);
            return attributes;
        } catch (NoSuchFileException exception) {
            return null;
        }
    }

    static void rejectUnsafeAttributes(BasicFileAttributes attributes) throws IOException {
        if (attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException("storage path is a symbolic link or reparse point");
        }
    }

    private static void requireSafeFile(BasicFileAttributes attributes) throws IOException {
        if (!attributes.isRegularFile()) {
            throw new IOException("object file is unsafe");
        }
    }

    private void writeContainer(Path destination, Path body, CocoObjectMetadata metadata) throws IOException {
        byte[] header = objectMapper.writeValueAsBytes(new StoredHeader(metadata.key(), metadata.contentLength(),
                metadata.contentType(), metadata.metadata(), metadata.lastModified().toEpochMilli()));
        if (header.length > MAX_HEADER_LENGTH) {
            throw new IOException("object header is too large");
        }
        try (var bodyInput = Files.newInputStream(body, LinkOption.NOFOLLOW_LINKS);
                var output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(destination,
                        StandardOpenOption.TRUNCATE_EXISTING)))) {
            output.write(MAGIC);
            output.writeInt(VERSION);
            output.writeInt(header.length);
            output.writeLong(metadata.contentLength());
            output.write(header);
            bodyInput.transferTo(output);
        }
    }

    private void publish(Path staging, Path destination) throws IOException {
        ensureSafeParents(destination, dataRoot);
        ensureSafeTarget(destination);
        if (overwrite) {
            replaceMove(staging, destination);
            return;
        }
        try {
            // A hard link is the create-if-absent publication primitive. There is no fallback:
            // a regular move could replace or race with an existing object.
            Files.createLink(destination, staging);
        } catch (UnsupportedOperationException exception) {
            throw new IOException("create-only publication requires hard-link support", exception);
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    private static void replaceMove(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            // overwrite=true permits replacement; this fallback is not promised to be atomic by the provider.
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private StoredContainer readContainer(Path object, String expectedKey) throws IOException {
        BasicFileAttributes attributes = safeAttributesIfExists(object);
        if (attributes == null) {
            return null;
        }
        requireSafeFile(attributes);
        long fileSize = attributes.size();
        if (fileSize < PREFIX_LENGTH) {
            throw new IOException("invalid object container size");
        }
        try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(object,
                LinkOption.NOFOLLOW_LINKS)))) {
            byte[] magic = input.readNBytes(MAGIC.length);
            if (magic.length != MAGIC.length || !java.util.Arrays.equals(magic, MAGIC)) {
                throw new IOException("invalid object container magic");
            }
            if (input.readInt() != VERSION) {
                throw new IOException("unsupported object container version");
            }
            int headerLength = input.readInt();
            long prefixBodyLength = input.readLong();
            if (headerLength < 1 || headerLength > MAX_HEADER_LENGTH || prefixBodyLength < 0
                    || prefixBodyLength > maxObjectSize || fileSize < PREFIX_LENGTH + (long) headerLength
                    || fileSize - PREFIX_LENGTH - headerLength != prefixBodyLength) {
                throw new IOException("invalid object container lengths");
            }
            byte[] header = input.readNBytes(headerLength);
            if (header.length != headerLength) {
                throw new IOException("truncated object container header");
            }
            CocoObjectMetadata metadata = parseHeader(header, expectedKey, prefixBodyLength);
            return new StoredContainer(metadata, PREFIX_LENGTH + headerLength, prefixBodyLength);
        }
    }

    private CocoObjectMetadata parseHeader(byte[] header, String expectedKey, long bodyLength) throws IOException {
        try (JsonParser parser = objectMapper.getFactory().createParser(header)) {
            JsonNode node = objectMapper.readTree(parser);
            if (node == null || !node.isObject() || parser.nextToken() != null || node.size() != HEADER_FIELDS.size()) {
                throw new IOException("invalid object container header");
            }
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String field = fieldNames.next();
                if (!HEADER_FIELDS.contains(field)) {
                    throw new IOException("invalid object container header field");
                }
            }
            JsonNode keyNode = required(node, "key");
            JsonNode lengthNode = required(node, "bodyLength");
            JsonNode contentTypeNode = required(node, "contentType");
            JsonNode metadataNode = required(node, "metadata");
            JsonNode modifiedNode = required(node, "lastModifiedEpochMillis");
            if (!keyNode.isTextual() || !expectedKey.equals(keyNode.textValue()) || !lengthNode.isIntegralNumber()
                    || !lengthNode.canConvertToLong() || lengthNode.longValue() != bodyLength
                    || (!contentTypeNode.isNull() && !contentTypeNode.isTextual())
                    || !metadataNode.isObject() || !modifiedNode.isIntegralNumber()
                    || !modifiedNode.canConvertToLong()) {
                throw new IOException("invalid object container header values");
            }
            Map<String, String> metadata = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = metadataNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!field.getValue().isTextual()) {
                    throw new IOException("invalid object metadata value");
                }
                metadata.put(field.getKey(), field.getValue().textValue());
            }
            try {
                String contentType = contentTypeNode.isNull() ? null : contentTypeNode.textValue();
                validateContentType(contentType);
                return new CocoObjectMetadata(expectedKey, bodyLength,
                        contentType, metadata,
                        Instant.ofEpochMilli(modifiedNode.longValue()));
            } catch (RuntimeException exception) {
                throw new IOException("invalid object container metadata", exception);
            }
        }
    }

    private static JsonNode required(JsonNode node, String name) throws IOException {
        JsonNode value = node.get(name);
        if (value == null) {
            throw new IOException("missing object container header field");
        }
        return value;
    }

    private static void validateContentType(String contentType) throws IOException {
        if (contentType == null) {
            return;
        }
        if (contentType.isBlank() || contentType.length() > MAX_CONTENT_TYPE_LENGTH) {
            throw new IOException("invalid content type");
        }
        for (int index = 0; index < contentType.length(); index++) {
            if (Character.isISOControl(contentType.charAt(index))) {
                throw new IOException("invalid content type");
            }
        }
    }

    private static void skipFully(InputStream input, long count) throws IOException {
        long skipped = 0;
        while (skipped < count) {
            long value = input.skip(count - skipped);
            if (value > 0) {
                skipped += value;
                continue;
            }
            if (input.read() == -1) {
                throw new IOException("truncated object container body");
            }
            skipped++;
        }
    }

    private String encodeToken(String key) {
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        if (!token.startsWith(TOKEN_PREFIX)) {
            throw new IllegalArgumentException("invalid continuation token");
        }
        try {
            String key = new String(Base64.getUrlDecoder().decode(token.substring(TOKEN_PREFIX.length())),
                    StandardCharsets.UTF_8);
            CocoObjectKey.validate(key);
            return key;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid continuation token", exception);
        }
    }

    private record StoredHeader(String key, long bodyLength, String contentType, Map<String, String> metadata,
            long lastModifiedEpochMillis) {
    }

    private record StoredContainer(CocoObjectMetadata metadata, long bodyOffset, long bodyLength) {
    }

    private static final class BoundedInputStream extends FilterInputStream {
        private long remaining;
        private boolean closed;

        private BoundedInputStream(InputStream input, long remaining) {
            super(input);
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            ensureOpen();
            if (remaining == 0) {
                return -1;
            }
            int value = super.read();
            if (value == -1) {
                throw new IOException("truncated object container body");
            }
            remaining--;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            ensureOpen();
            if (length == 0) {
                return 0;
            }
            if (remaining == 0) {
                return -1;
            }
            int requested = (int) Math.min(length, remaining);
            int value = super.read(buffer, offset, requested);
            if (value == -1) {
                throw new IOException("truncated object container body");
            }
            remaining -= value;
            return value;
        }

        @Override
        public long skip(long count) throws IOException {
            ensureOpen();
            long skipped = super.skip(Math.min(count, remaining));
            remaining -= skipped;
            return skipped;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        private void ensureOpen() throws IOException {
            if (closed) {
                throw new IOException("stream is closed");
            }
        }
    }
}
