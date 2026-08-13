package io.github.coco.feature.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalCocoObjectStorageTest {
    @TempDir Path directory;

    @Test void completesStreamingObjectLifecycleAndMetadataRoundTrip() throws Exception {
        TrackingInputStream input = new TrackingInputStream("hello".getBytes(StandardCharsets.UTF_8));
        LocalCocoObjectStorage storage = storage(true, 16);
        CocoObjectStat put = storage.put(new CocoObjectWriteRequest("docs/a.txt", input, 5L, "text/plain", Map.of("owner", "test")));
        assertThat(put.found()).isTrue(); assertThat(input.closed).isFalse();
        CocoObjectMetadata stat = storage.stat("docs/a.txt").metadata();
        assertThat(stat.contentType()).isEqualTo("text/plain");
        assertThat(stat.metadata()).containsEntry("owner", "test");
        try (CocoObjectReadResult result = storage.get("docs/a.txt").orElseThrow()) {
            assertThat(result.inputStream().readAllBytes()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(storage.delete("docs/a.txt")).isTrue(); assertThat(storage.stat("docs/a.txt").found()).isFalse();
    }

    @Test void supportsEmptyObjectsAndRejectsFalseOrOversizedStreamingLengths() throws Exception {
        LocalCocoObjectStorage storage = storage(true, 3);
        assertThat(storage.put(new CocoObjectWriteRequest("empty", new ByteArrayInputStream(new byte[0]), 0L, null, Map.of())).metadata().contentLength()).isZero();
        assertThatThrownBy(() -> storage.put(new CocoObjectWriteRequest("wrong", new ByteArrayInputStream(new byte[] { 1 }), 2L, null, Map.of()))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> storage.put(new CocoObjectWriteRequest("large", new ByteArrayInputStream(new byte[] { 1, 2, 3, 4 }), 1L, null, Map.of()))).isInstanceOf(IOException.class);
        assertStagingClean();
    }

    @Test void rejectsUnsafeKeysMetadataAndOverwrite() throws Exception {
        LocalCocoObjectStorage storage = storage(false, 10);
        for (String key : new String[] { "/absolute", "a\\b", "a/../b", "a//b", "C:/x", "\\\\host\\x", "a\u0000b" }) assertThatThrownBy(() -> CocoObjectKey.validate(key)).isInstanceOf(IllegalArgumentException.class);
        storage.put(new CocoObjectWriteRequest("item", new ByteArrayInputStream(new byte[] { 1 }), null, null, Map.of()));
        assertThatThrownBy(() -> storage.put(new CocoObjectWriteRequest("item", new ByteArrayInputStream(new byte[] { 2 }), null, null, Map.of()))).isInstanceOf(IOException.class);
        try (CocoObjectReadResult result = storage.get("item").orElseThrow()) { assertThat(result.inputStream().readAllBytes()).containsExactly(1); }
        assertThatThrownBy(() -> new CocoObjectWriteRequest("metadata", new ByteArrayInputStream(new byte[0]), null, null, Map.of("x".repeat(129), "v"))).isInstanceOf(IllegalArgumentException.class);
        assertStagingClean();
    }

    @Test void createOnlyConcurrentlyPublishesExactlyOneObjectWithoutOverwriting() throws Exception {
        LocalCocoObjectStorage storage = storage(false, 10);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> writer = () -> {
                try {
                    storage.put(new CocoObjectWriteRequest("item", new ByteArrayInputStream(new byte[] { 1 }), 1L, null, Map.of()));
                    return true;
                } catch (IOException exception) {
                    return false;
                }
            };
            List<Future<Boolean>> results = executor.invokeAll(List.of(writer, writer));
            assertThat(results.stream().filter(result -> get(result)).count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
        try (CocoObjectReadResult result = storage.get("item").orElseThrow()) { assertThat(result.inputStream().readAllBytes()).containsExactly(1); }
        assertStagingClean();
    }

    @Test void atomicallyReplacesObjectsAndClosesReadStreams() throws Exception {
        LocalCocoObjectStorage storage = storage(true, 10);
        storage.put(new CocoObjectWriteRequest("item", new ByteArrayInputStream(new byte[] { 1 }), 1L, null, Map.of()));
        storage.put(new CocoObjectWriteRequest("item", new ByteArrayInputStream(new byte[] { 2, 3 }), 2L, null, Map.of()));
        CocoObjectReadResult result = storage.get("item").orElseThrow();
        assertThat(result.inputStream().readAllBytes()).containsExactly(2, 3);
        result.close();
        assertThatThrownBy(result.inputStream()::read).isInstanceOf(IOException.class);
    }

    @Test void rejectsCorruptOrOversizedContainersAndDoesNotCreateSidecars() throws Exception {
        LocalCocoObjectStorage storage = storage(true, 10);
        Path object = objectPath("item");
        Files.createDirectories(object.getParent());
        Files.write(object, new byte[24]);
        assertThatThrownBy(() -> storage.stat("item")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> storage.list("", 10, null)).isInstanceOf(IOException.class);
        storage.put(new CocoObjectWriteRequest("item", new ByteArrayInputStream(new byte[] { 1 }), 1L, null, Map.of()));
        byte[] complete = Files.readAllBytes(object);
        Files.write(object, java.util.Arrays.copyOf(complete, complete.length - 1));
        assertThatThrownBy(() -> storage.stat("item")).isInstanceOf(IOException.class);
        storage.put(new CocoObjectWriteRequest("item", new ByteArrayInputStream(new byte[] { 1 }), 1L, null, Map.of()));
        complete = Files.readAllBytes(object);
        java.nio.ByteBuffer.wrap(complete, 16, Long.BYTES).putLong(11L);
        Files.write(object, complete);
        assertThatThrownBy(() -> storage.get("item")).isInstanceOf(IOException.class);
        assertThat(Files.exists(directory.resolve(".coco-storage/metadata"))).isFalse();
        assertStagingClean();
    }

    @Test void listsStablyPaginatesAndHidesStagingFiles() throws Exception {
        LocalCocoObjectStorage storage = storage(true, 10);
        for (String key : new String[] { "b", "a", "folder/c" }) storage.put(new CocoObjectWriteRequest(key, new ByteArrayInputStream(new byte[0]), 0L, null, Map.of()));
        Files.write(objectPath(".staging-hidden"), new byte[] { 1 });
        CocoObjectListResult first = storage.list("", 2, null);
        assertThat(first.objects()).extracting(value -> value.metadata().key()).containsExactly("a", "b"); assertThat(first.continuationToken()).isNotBlank();
        assertThat(storage.list("", 2, first.continuationToken()).objects()).extracting(value -> value.metadata().key()).containsExactly("folder/c");
        assertThatThrownBy(() -> storage.list("", 1, "v2:bad")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsSymbolicLinkEscapeWhenSupported() throws Exception {
        Path outside = Files.createTempDirectory("coco-storage-outside"); LocalCocoObjectStorage storage = storage(true, 10);
        Path objects = directory.resolve(".coco-storage/objects");
        try { Files.createSymbolicLink(objects.resolve("escape"), outside); }
        catch (UnsupportedOperationException | IOException exception) { return; }
        assertThatThrownBy(() -> storage.put(new CocoObjectWriteRequest("escape/file", new ByteArrayInputStream(new byte[0]), 0L, null, Map.of()))).isInstanceOf(IOException.class);
    }

    @Test void rejectsOtherAttributesUsedForWindowsJunctions() {
        BasicFileAttributes reparsePoint = new BasicFileAttributes() {
            @Override public FileTime lastModifiedTime() { return FileTime.from(Instant.EPOCH); }
            @Override public FileTime lastAccessTime() { return FileTime.from(Instant.EPOCH); }
            @Override public FileTime creationTime() { return FileTime.from(Instant.EPOCH); }
            @Override public boolean isRegularFile() { return false; }
            @Override public boolean isDirectory() { return false; }
            @Override public boolean isSymbolicLink() { return false; }
            @Override public boolean isOther() { return true; }
            @Override public long size() { return 0; }
            @Override public Object fileKey() { return null; }
        };
        assertThatThrownBy(() -> LocalCocoObjectStorage.rejectUnsafeAttributes(reparsePoint)).isInstanceOf(IOException.class);
    }

    private void assertStagingClean() throws IOException {
        try (var files = Files.walk(directory.resolve(".coco-storage"))) {
            assertThat(files.filter(path -> path.getFileName().toString().startsWith(".staging-")
                    || path.getFileName().toString().startsWith(".body-")).toList()).isEmpty();
        }
    }

    private Path objectPath(String key) { return directory.resolve(".coco-storage/objects").resolve(key); }
    private static boolean get(Future<Boolean> result) { try { return result.get(); } catch (Exception exception) { throw new AssertionError(exception); } }
    private LocalCocoObjectStorage storage(boolean overwrite, long maximum) {
        CocoStorageProperties.Local local = new CocoStorageProperties.Local(); local.setRoot(directory.toString()); local.setOverwrite(overwrite); local.setMaxObjectSize(maximum); local.setListMaxSize(10); return new LocalCocoObjectStorage(local);
    }
    private static final class TrackingInputStream extends ByteArrayInputStream { private boolean closed; private TrackingInputStream(byte[] bytes) { super(bytes); } @Override public void close() throws IOException { closed = true; super.close(); } }
}
