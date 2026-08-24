package io.github.coco.storage.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.github.coco.storage.CocoObjectMetadata;
import io.github.coco.storage.CocoObjectPutRequest;
import io.github.coco.storage.CocoObjectResource;
import io.github.coco.storage.CocoStorageErrorCode;
import io.github.coco.storage.CocoStorageException;
import io.github.coco.storage.CocoStorageOverwritePolicy;
import io.github.coco.storage.CocoStorageProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 本地对象存储安全、并发和回收行为测试。
 */
class LocalCocoObjectStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsLargeContentAndPublishesStableChecksum() throws Exception {
        LocalCocoObjectStorage storage = storage(3L * 1024L * 1024L);
        PatternInputStream input = new PatternInputStream(2L * 1024L * 1024L + 17L);

        CocoObjectMetadata metadata = storage.put(CocoObjectPutRequest.of("reports/large.bin", input, null,
                "application/octet-stream"));

        assertThat(metadata.size()).isEqualTo(2L * 1024L * 1024L + 17L);
        assertThat(metadata.sha256()).isEqualTo(sha256(patternBytes((int) metadata.size())));
        assertThat(input.maximumRequestedRead()).isLessThanOrEqualTo(8192);
        try (CocoObjectResource resource = storage.open("reports/large.bin")) {
            assertThat(resource.resource().contentLength()).isEqualTo(metadata.size());
        }
    }

    @Test
    void mapsManifestToFixedHashShardsWithoutUserKeyPath() throws Exception {
        LocalCocoObjectStorage storage = storage(1024);
        String key = "customer/42/private-contract.txt";
        storage.put(CocoObjectPutRequest.of(key, new ByteArrayInputStream(new byte[] { 1 }), 1L, "text/plain"));

        Path manifestRoot = root().resolve("manifests");
        try (var paths = Files.walk(manifestRoot)) {
            List<Path> manifests = paths.filter(path -> path.getFileName().toString().endsWith(".properties")).toList();
            assertThat(manifests).hasSize(1);
            Path relative = manifestRoot.relativize(manifests.get(0));
            assertThat(relative.getNameCount()).isEqualTo(3);
            assertThat(relative.getName(0).toString()).matches("[0-9a-f]{2}");
            assertThat(relative.getName(1).toString()).matches("[0-9a-f]{2}");
            assertThat(relative.getFileName().toString()).matches("[0-9a-f]{64}\\.properties");
            assertThat(manifests.get(0).toString()).doesNotContain("customer").doesNotContain("private-contract");
        }
    }

    @Test
    void rejectsDeclaredLengthMismatchAndActualLimitOverflow() {
        LocalCocoObjectStorage storage = storage(4);

        assertStorageCode(() -> storage.put(CocoObjectPutRequest.of("length.bin",
                new ByteArrayInputStream(new byte[] { 1, 2, 3 }), 2L, "application/octet-stream")),
                CocoStorageErrorCode.CONTENT_LENGTH_MISMATCH);
        assertStorageCode(() -> storage.put(CocoObjectPutRequest.of("limit.bin",
                new ByteArrayInputStream(new byte[] { 1, 2, 3, 4, 5 }), null, "application/octet-stream")),
                CocoStorageErrorCode.CONTENT_TOO_LARGE);
        assertThat(storage.exists("length.bin")).isFalse();
        assertThat(storage.exists("limit.bin")).isFalse();
    }

    @Test
    void validatesTypeAndExtensionAgainstTheObjectKey() {
        CocoStorageProperties properties = properties(1024);
        properties.setAllowedContentTypes(Set.of("text/plain"));
        properties.setAllowedExtensions(Set.of("txt"));
        LocalCocoObjectStorage storage = new LocalCocoObjectStorage(properties);

        assertStorageCode(() -> storage.put(CocoObjectPutRequest.of("report.csv",
                new ByteArrayInputStream(new byte[] { 1 }), 1L, "text/plain")), CocoStorageErrorCode.EXTENSION_NOT_ALLOWED);
        assertStorageCode(() -> storage.put(CocoObjectPutRequest.of("report.txt",
                new ByteArrayInputStream(new byte[] { 1 }), 1L, "application/json")),
                CocoStorageErrorCode.CONTENT_TYPE_NOT_ALLOWED);
    }

    @Test
    void rejectsTraversalAbsoluteAndWindowsSeparatorKeys() {
        LocalCocoObjectStorage storage = storage(1024);

        for (String key : List.of("../outside", "nested/../outside", "/absolute", "C:/absolute", "nested\\outside",
                "//server/share", "folder//file")) {
            assertStorageCode(() -> storage.put(CocoObjectPutRequest.of(key, new ByteArrayInputStream(new byte[] { 1 }),
                    1L, "application/octet-stream")), CocoStorageErrorCode.INVALID_KEY);
        }
    }

    @Test
    void rejectsSymbolicLinkEscapeWhenSupportedByTheFileSystem() throws Exception {
        LocalCocoObjectStorage storage = storage(1024);
        Path manifestRoot = root().resolve("manifests");
        Path outside = this.temporaryDirectory.resolve("outside");
        Files.createDirectories(outside);
        Files.delete(manifestRoot);
        try {
            Files.createSymbolicLink(manifestRoot, outside);
        }
        catch (UnsupportedOperationException | IOException exception) {
            Assumptions.assumeTrue(false, "当前文件系统不允许创建符号链接: " + exception.getClass().getSimpleName());
        }

        assertStorageCode(() -> storage.put(CocoObjectPutRequest.of("escaped.bin",
                new ByteArrayInputStream(new byte[] { 1 }), 1L, "application/octet-stream")), CocoStorageErrorCode.INVALID_ROOT);
        try (var paths = Files.list(outside)) {
            assertThat(paths.toList()).isEmpty();
        }
    }

    @Test
    void rejectsWindowsJunctionAsConfiguredRoot() throws Exception {
        assumeWindows();
        Path target = this.temporaryDirectory.resolve("junction-target");
        Files.createDirectories(target);
        Path junction = this.temporaryDirectory.resolve("junction-root");
        createJunction(junction, target);
        CocoStorageProperties properties = new CocoStorageProperties();
        properties.getLocal().setRoot(junction);

        assertStorageCode(() -> new LocalCocoObjectStorage(properties), CocoStorageErrorCode.INVALID_ROOT);
    }

    @Test
    void rejectsWindowsJunctionInFrameworkInternalDirectory() throws Exception {
        assumeWindows();
        LocalCocoObjectStorage storage = storage(1024);
        Path target = this.temporaryDirectory.resolve("junction-target");
        Files.createDirectories(target);
        Path manifestRoot = root().resolve("manifests");
        Files.delete(manifestRoot);
        createJunction(manifestRoot, target);

        assertStorageCode(() -> storage.put(CocoObjectPutRequest.of("junction.bin",
                new ByteArrayInputStream(new byte[] { 1 }), 1L, "application/octet-stream")), CocoStorageErrorCode.INVALID_ROOT);
        try (var paths = Files.list(target)) {
            assertThat(paths.toList()).isEmpty();
        }
    }

    @Test
    void rejectsDefaultOverwriteAndAtomicallyReplacesWhenExplicit() throws Exception {
        LocalCocoObjectStorage storage = storage(1024);
        CocoObjectMetadata first = storage.put(CocoObjectPutRequest.of("same.bin",
                new ByteArrayInputStream(new byte[] { 1, 2 }), 2L, "application/octet-stream"));

        assertStorageCode(() -> storage.put(CocoObjectPutRequest.of("same.bin",
                new ByteArrayInputStream(new byte[] { 3 }), 1L, "application/octet-stream")),
                CocoStorageErrorCode.OBJECT_ALREADY_EXISTS);
        CocoObjectMetadata replacement = storage.put(new CocoObjectPutRequest("same.bin",
                new ByteArrayInputStream(new byte[] { 3, 4, 5 }), 3L, "application/octet-stream",
                CocoStorageOverwritePolicy.REPLACE));

        assertThat(replacement.sha256()).isNotEqualTo(first.sha256());
        try (CocoObjectResource resource = storage.open("same.bin");
                InputStream input = resource.resource().getInputStream()) {
            assertThat(input.readAllBytes()).containsExactly(3, 4, 5);
        }
    }

    @Test
    void serializesConcurrentReplaceAndDeleteForTheSameKey() throws Exception {
        LocalCocoObjectStorage storage = storage(1024);
        storage.put(CocoObjectPutRequest.of("raced.bin", new ByteArrayInputStream(new byte[] { 0 }), 1L,
                "application/octet-stream"));
        ExecutorService executor = Executors.newFixedThreadPool(24);
        CountDownLatch ready = new CountDownLatch(24);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> results = java.util.stream.IntStream.range(0, 24).mapToObj(index -> executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                if (index % 3 == 0) {
                    storage.delete("raced.bin");
                }
                else {
                    storage.put(new CocoObjectPutRequest("raced.bin", new ByteArrayInputStream(new byte[] { (byte) index }),
                            1L, "application/octet-stream", CocoStorageOverwritePolicy.REPLACE));
                }
                return null;
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> result : results) {
                result.get(10, TimeUnit.SECONDS);
            }
            if (storage.exists("raced.bin")) {
                try (CocoObjectResource resource = storage.open("raced.bin");
                        InputStream input = resource.resource().getInputStream()) {
                    assertThat(input.readAllBytes()).hasSize(1);
                }
            }
        }
        finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void keepsTheNewManifestWhenDeleteObservedTheOldManifestFirst() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        CountDownLatch deleteObserved = new CountDownLatch(1);
        CountDownLatch continueDelete = new CountDownLatch(1);
        LocalCocoObjectStorage storage = storage(properties(1024), clock, key -> {
            deleteObserved.countDown();
            awaitLatch(continueDelete);
        });
        storage.put(CocoObjectPutRequest.of("linearized.bin", new ByteArrayInputStream(new byte[] { 1 }), 1L,
                "application/octet-stream"));

        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<Boolean> delete = executor.submit(() -> storage.delete("linearized.bin"));
            awaitLatch(deleteObserved);
            CountDownLatch replaceAttempted = new CountDownLatch(1);
            Future<CocoObjectMetadata> replace = executor.submit(() -> {
                replaceAttempted.countDown();
                return storage.put(new CocoObjectPutRequest("linearized.bin", new ByteArrayInputStream(new byte[] { 2 }),
                        1L, "application/octet-stream", CocoStorageOverwritePolicy.REPLACE));
            });
            CountDownLatch gcAttempted = new CountDownLatch(1);
            Future<Integer> garbageCollection = executor.submit(() -> {
                gcAttempted.countDown();
                return storage.collectGarbage();
            });
            awaitLatch(replaceAttempted);
            awaitLatch(gcAttempted);
            continueDelete.countDown();

            assertThat(delete.get(5, TimeUnit.SECONDS)).isTrue();
            replace.get(5, TimeUnit.SECONDS);
            garbageCollection.get(5, TimeUnit.SECONDS);
            try (CocoObjectResource resource = storage.open("linearized.bin");
                    InputStream input = resource.resource().getInputStream()) {
                assertThat(input.readAllBytes()).containsExactly(2);
            }
        }
        finally {
            continueDelete.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void garbageCollectionRetainsOpenedSnapshotUntilReleasedAndReclaimsReplacedBlob() throws Exception {
        LocalCocoObjectStorage storage = storageWithGarbageCollection(Duration.ZERO);
        storage.put(CocoObjectPutRequest.of("versioned.bin", new ByteArrayInputStream(new byte[] { 1 }), 1L,
                "application/octet-stream"));
        CocoObjectResource snapshot = storage.open("versioned.bin");
        storage.put(new CocoObjectPutRequest("versioned.bin", new ByteArrayInputStream(new byte[] { 2 }), 1L,
                "application/octet-stream", CocoStorageOverwritePolicy.REPLACE));

        assertThat(storage.collectGarbage()).isZero();
        assertThat(blobCount()).isEqualTo(2);
        try (snapshot; InputStream input = snapshot.resource().getInputStream()) {
            assertThat(input.readAllBytes()).containsExactly(1);
        }
        assertThat(storage.collectGarbage()).isEqualTo(1);
        assertThat(blobCount()).isEqualTo(1);
    }

    @Test
    void zeroGcIntervalDisablesPeriodicCollectionButKeepsManualCollectionAvailable() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        CocoStorageProperties properties = properties(1024);
        properties.getLocal().setGcInterval(Duration.ZERO);
        properties.getLocal().setOrphanGracePeriod(Duration.ZERO);
        LocalCocoObjectStorage storage = storage(properties, clock, LocalStorageTestHook.NONE);
        storage.put(CocoObjectPutRequest.of("manual-gc.bin", new ByteArrayInputStream(new byte[] { 1 }), 1L,
                "application/octet-stream"));
        assertThat(storage.delete("manual-gc.bin")).isTrue();
        assertThat(blobCount()).isEqualTo(1);

        assertThat(storage.collectGarbage()).isEqualTo(1);
        assertThat(blobCount()).isZero();
        storage.close();
    }

    @Test
    void startsReplacementGraceWhenTheManifestStopsReferencingAnOldBlob() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LocalCocoObjectStorage storage = storageWithGarbageCollection(Duration.ofHours(1), clock);
        storage.put(CocoObjectPutRequest.of("grace.bin", new ByteArrayInputStream(new byte[] { 1 }), 1L,
                "application/octet-stream"));
        String oldBlobId = blobIds().iterator().next();
        Files.setLastModifiedTime(blob(oldBlobId), FileTime.from(Instant.EPOCH));

        storage.put(new CocoObjectPutRequest("grace.bin", new ByteArrayInputStream(new byte[] { 2 }), 1L,
                "application/octet-stream", CocoStorageOverwritePolicy.REPLACE));

        assertThat(marker(oldBlobId)).exists();
        assertThat(storage.collectGarbage()).isZero();
        assertThat(blob(oldBlobId)).exists();
        clock.advance(Duration.ofMinutes(59));
        assertThat(storage.collectGarbage()).isZero();
        assertThat(blob(oldBlobId)).exists();
        clock.advance(Duration.ofMinutes(1));
        assertThat(storage.collectGarbage()).isEqualTo(1);
        assertThat(blob(oldBlobId)).doesNotExist();
    }

    @Test
    void restartsGraceForMissingOrInvalidOrphanMarkersDuringRecovery() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        CocoStorageProperties properties = properties(1024);
        properties.getLocal().setOrphanGracePeriod(Duration.ofHours(1));
        LocalCocoObjectStorage first = storage(properties, clock, LocalStorageTestHook.NONE);
        first.put(CocoObjectPutRequest.of("missing-marker.bin", new ByteArrayInputStream(new byte[] { 1 }), 1L,
                "application/octet-stream"));
        first.put(CocoObjectPutRequest.of("invalid-marker.bin", new ByteArrayInputStream(new byte[] { 2 }), 1L,
                "application/octet-stream"));
        assertThat(first.delete("missing-marker.bin")).isTrue();
        assertThat(first.delete("invalid-marker.bin")).isTrue();
        Set<String> orphanedBlobIds = blobIds();
        for (String blobId : orphanedBlobIds) {
            Files.setLastModifiedTime(blob(blobId), FileTime.from(Instant.EPOCH));
        }
        first.close();
        String missingMarkerBlobId = orphanedBlobIds.stream().filter(blobId -> Files.exists(marker(blobId))).findFirst()
                .orElseThrow();
        String invalidMarkerBlobId = orphanedBlobIds.stream().filter(blobId -> !blobId.equals(missingMarkerBlobId)).findFirst()
                .orElseThrow();
        Files.delete(marker(missingMarkerBlobId));
        Files.writeString(marker(invalidMarkerBlobId), "blob=not-a-blob\norphanedAtEpochMillis=invalid\n");
        clock.advance(Duration.ofDays(1));

        LocalCocoObjectStorage recovered = storage(properties, clock, LocalStorageTestHook.NONE);
        assertThat(blobIds()).containsExactlyInAnyOrderElementsOf(orphanedBlobIds);
        assertThat(marker(missingMarkerBlobId)).exists();
        assertThat(marker(invalidMarkerBlobId)).exists();
        clock.advance(Duration.ofMinutes(59));
        assertThat(recovered.collectGarbage()).isZero();
        clock.advance(Duration.ofMinutes(1));
        assertThat(recovered.collectGarbage()).isEqualTo(2);
        recovered.close();
    }

    @Test
    void retainsReferencedBlobAndClearsItsStaleOrphanMarker() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LocalCocoObjectStorage storage = storageWithGarbageCollection(Duration.ZERO, clock);
        storage.put(CocoObjectPutRequest.of("referenced.bin", new ByteArrayInputStream(new byte[] { 1 }), 1L,
                "application/octet-stream"));
        String blobId = blobIds().iterator().next();
        Files.writeString(marker(blobId), "blob=" + blobId + "\norphanedAtEpochMillis=0\n");

        assertThat(storage.collectGarbage()).isZero();
        assertThat(blob(blobId)).exists();
        assertThat(marker(blobId)).doesNotExist();
    }

    @Test
    void startupRecoveryRemovesDeletedBlobAfterTheConfiguredGracePeriod() throws Exception {
        CocoStorageProperties delayed = properties(1024);
        delayed.getLocal().setOrphanGracePeriod(Duration.ofDays(1));
        LocalCocoObjectStorage first = new LocalCocoObjectStorage(delayed);
        first.put(CocoObjectPutRequest.of("deleted.bin", new ByteArrayInputStream(new byte[] { 1 }), 1L,
                "application/octet-stream"));
        assertThat(first.delete("deleted.bin")).isTrue();
        first.close();
        assertThat(blobCount()).isEqualTo(1);

        LocalCocoObjectStorage recovered = storageWithGarbageCollection(Duration.ZERO);
        assertThat(blobCount()).isZero();
        recovered.close();
    }

    @Test
    void cleansTemporaryFilesAfterAStreamingFailure() throws Exception {
        LocalCocoObjectStorage storage = storage(1024);

        assertStorageCode(() -> storage.put(CocoObjectPutRequest.of("broken.bin", new FailingInputStream(), null,
                "application/octet-stream")), CocoStorageErrorCode.STORAGE_IO_FAILURE);

        try (var paths = Files.walk(root())) {
            assertThat(paths.filter(path -> path.getFileName().toString().startsWith(".coco-")).toList()).isEmpty();
        }
        assertThat(storage.exists("broken.bin")).isFalse();
    }

    @Test
    void streamsReadAndDeletesIdempotently() throws Exception {
        LocalCocoObjectStorage storage = storage(1024);
        CocoObjectMetadata written = storage.put(CocoObjectPutRequest.of("documents/read.txt",
                new ByteArrayInputStream("content".getBytes()), 7L, "text/plain"));

        assertThat(storage.stat("documents/read.txt")).isEqualTo(written);
        try (CocoObjectResource resource = storage.open("documents/read.txt");
                InputStream input = resource.resource().getInputStream()) {
            assertThat(input.readAllBytes()).isEqualTo("content".getBytes());
        }
        assertThat(storage.delete("documents/read.txt")).isTrue();
        assertThat(storage.delete("documents/read.txt")).isFalse();
        assertThat(storage.exists("documents/read.txt")).isFalse();
    }

    private LocalCocoObjectStorage storage(long maxSizeBytes) {
        return new LocalCocoObjectStorage(properties(maxSizeBytes));
    }

    private LocalCocoObjectStorage storageWithGarbageCollection(Duration gracePeriod) {
        return storageWithGarbageCollection(gracePeriod, Clock.systemUTC());
    }

    private LocalCocoObjectStorage storageWithGarbageCollection(Duration gracePeriod, Clock clock) {
        CocoStorageProperties properties = properties(1024);
        properties.getLocal().setOrphanGracePeriod(gracePeriod);
        return storage(properties, clock, LocalStorageTestHook.NONE);
    }

    private static LocalCocoObjectStorage storage(CocoStorageProperties properties, Clock clock, LocalStorageTestHook hook) {
        return new LocalCocoObjectStorage(properties, clock, hook);
    }

    private CocoStorageProperties properties(long maxSizeBytes) {
        CocoStorageProperties properties = new CocoStorageProperties();
        properties.getLocal().setRoot(root());
        properties.getLocal().setGcInterval(Duration.ZERO);
        properties.setMaxSizeBytes(maxSizeBytes);
        return properties;
    }

    private Path root() {
        return this.temporaryDirectory.resolve("storage");
    }

    private long blobCount() throws IOException {
        try (var paths = Files.list(root().resolve("blobs"))) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".bin")).count();
        }
    }

    private Set<String> blobIds() throws IOException {
        try (var paths = Files.list(root().resolve("blobs"))) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".bin"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.bin$", ""))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private Path blob(String blobId) {
        return root().resolve("blobs").resolve(blobId + ".bin");
    }

    private Path marker(String blobId) {
        return root().resolve("blobs").resolve(blobId + ".orphan");
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("同步点超时");
            }
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("同步点被中断", exception);
        }
    }

    private static void assumeWindows() {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win"),
                "Windows junction test only runs on Windows");
    }

    private static void createJunction(Path link, Path target) throws Exception {
        Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), target.toString())
                .redirectErrorStream(true).start();
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
    }

    private static void assertStorageCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            CocoStorageErrorCode expected) {
        assertThatThrownBy(action).isInstanceOf(CocoStorageException.class)
                .extracting(exception -> ((CocoStorageException) exception).code()).isEqualTo(expected.code());
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static byte[] patternBytes(int size) {
        byte[] bytes = new byte[size];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) index;
        }
        return bytes;
    }

    private static final class PatternInputStream extends InputStream {

        private final long size;

        private long position;

        private int maximumRequestedRead;

        private PatternInputStream(long size) {
            this.size = size;
        }

        @Override
        public int read() {
            if (this.position >= this.size) {
                return -1;
            }
            return (byte) this.position++ & 0xff;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            this.maximumRequestedRead = Math.max(this.maximumRequestedRead, length);
            if (this.position >= this.size) {
                return -1;
            }
            int count = (int) Math.min(Math.min(length, 317), this.size - this.position);
            for (int index = 0; index < count; index++) {
                bytes[offset + index] = (byte) this.position++;
            }
            return count;
        }

        private int maximumRequestedRead() {
            return this.maximumRequestedRead;
        }
    }

    private static final class FailingInputStream extends InputStream {

        private int reads;

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (this.reads++ == 0) {
                bytes[offset] = 1;
                return 1;
            }
            throw new IOException("expected stream failure");
        }

        @Override
        public int read() throws IOException {
            throw new IOException("expected stream failure");
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return this.instant;
        }

        private void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }
    }
}
