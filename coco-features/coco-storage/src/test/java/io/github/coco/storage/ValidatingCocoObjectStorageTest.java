package io.github.coco.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

/**
 * 内容校验装饰器的流还原、字段透传和短路行为测试。
 * <p>
 * 覆盖探测字节拼回后委托实现拿到的内容与原始内容完全一致（含长于、短于、等于探测长度和空内容，
 * 以及每次只返回一个字节的流），写入请求字段不被改写，校验或扫描拒绝时不再委托写入，
 * 读取类方法直接透传，以及关闭动作向下传递。
 * </p>
 */
class ValidatingCocoObjectStorageTest {

    @Test
    void restoresContentLongerThanTheProbeByteForByte() {
        RecordingCocoObjectStorage delegate = new RecordingCocoObjectStorage();
        ValidatingCocoObjectStorage storage = storage(delegate, 8);
        byte[] content = patternBytes(100);

        storage.put(CocoObjectPutRequest.of("uploads/data.bin", new ByteArrayInputStream(content), 100L,
                "application/octet-stream"));

        assertThat(delegate.content()).isEqualTo(content);
    }

    @Test
    void restoresContentShorterThanTheProbeByteForByte() {
        RecordingCocoObjectStorage delegate = new RecordingCocoObjectStorage();
        ValidatingCocoObjectStorage storage = storage(delegate, 64);
        byte[] content = patternBytes(5);

        storage.put(CocoObjectPutRequest.of("uploads/short.bin", new ByteArrayInputStream(content), 5L,
                "application/octet-stream"));

        assertThat(delegate.content()).isEqualTo(content);
    }

    @Test
    void restoresContentExactlyAsLongAsTheProbeByteForByte() {
        RecordingCocoObjectStorage delegate = new RecordingCocoObjectStorage();
        ValidatingCocoObjectStorage storage = storage(delegate, 16);
        byte[] content = patternBytes(16);

        storage.put(CocoObjectPutRequest.of("uploads/exact.bin", new ByteArrayInputStream(content), 16L,
                "application/octet-stream"));

        assertThat(delegate.content()).isEqualTo(content);
    }

    @Test
    void handlesEmptyContentWithoutError() {
        RecordingCocoObjectStorage delegate = new RecordingCocoObjectStorage();
        ValidatingCocoObjectStorage storage = storage(delegate, 8);

        CocoObjectMetadata metadata = storage.put(CocoObjectPutRequest.of("uploads/empty.bin",
                new ByteArrayInputStream(new byte[0]), 0L, "application/octet-stream"));

        assertThat(delegate.content()).isEmpty();
        assertThat(metadata.size()).isZero();
    }

    @Test
    void restoresContentFromAStreamThatReturnsASingleBytePerRead() {
        RecordingCocoObjectStorage delegate = new RecordingCocoObjectStorage();
        byte[] content = patternBytes(100);
        List<byte[]> probes = new ArrayList<>();
        ValidatingCocoObjectStorage storage = new ValidatingCocoObjectStorage(delegate,
                probe -> probes.add(probe.probeBytes()), probe -> {
                }, 8);

        storage.put(CocoObjectPutRequest.of("uploads/dripping.bin",
                new OneBytePerReadInputStream(new ByteArrayInputStream(content)), 100L, "application/octet-stream"));

        assertThat(delegate.content()).isEqualTo(content);
        // 单字节流会让只调用一次 read 的探测循环拿到 1 个字节，内容仍然完整但校验器看不到足够的魔数。
        assertThat(probes).singleElement().isEqualTo(Arrays.copyOf(content, 8));
    }

    @Test
    void forwardsEveryRequestFieldUnchangedToTheDelegate() {
        RecordingCocoObjectStorage delegate = new RecordingCocoObjectStorage();
        ValidatingCocoObjectStorage storage = storage(delegate, 8);

        storage.put(new CocoObjectPutRequest("uploads/nested/typed.bin", new ByteArrayInputStream(patternBytes(40)), 40L,
                "application/octet-stream", CocoStorageOverwritePolicy.REPLACE));

        CocoObjectPutRequest received = delegate.request();
        assertThat(received.key()).isEqualTo("uploads/nested/typed.bin");
        assertThat(received.contentLength()).isEqualTo(40L);
        assertThat(received.contentType()).isEqualTo("application/octet-stream");
        assertThat(received.overwritePolicy()).isEqualTo(CocoStorageOverwritePolicy.REPLACE);
    }

    @Test
    void doesNotDelegateWriteWhenTheValidatorRejectsTheContent() {
        RecordingCocoObjectStorage delegate = new RecordingCocoObjectStorage();
        ValidatingCocoObjectStorage storage = new ValidatingCocoObjectStorage(delegate,
                probe -> {
                    throw new CocoStorageException(CocoStorageErrorCode.DANGEROUS_CONTENT, "PE executable (EXE/DLL)");
                }, probe -> {
                }, 8);

        assertThatThrownBy(() -> storage.put(CocoObjectPutRequest.of("uploads/evil.png",
                new ByteArrayInputStream(patternBytes(40)), 40L, "image/png")))
                .isInstanceOf(CocoStorageException.class)
                .extracting(exception -> ((CocoStorageException) exception).code())
                .isEqualTo(CocoStorageErrorCode.DANGEROUS_CONTENT.code());
        assertThat(delegate.request()).isNull();
        assertThat(delegate.content()).isNull();
    }

    @Test
    void doesNotDelegateWriteWhenTheScannerRejectsTheContent() {
        RecordingCocoObjectStorage delegate = new RecordingCocoObjectStorage();
        ValidatingCocoObjectStorage storage = new ValidatingCocoObjectStorage(delegate, probe -> {
        }, probe -> {
            throw new CocoStorageException(CocoStorageErrorCode.SCAN_REJECTED, "test-engine");
        }, 8);

        assertThatThrownBy(() -> storage.put(CocoObjectPutRequest.of("uploads/infected.bin",
                new ByteArrayInputStream(patternBytes(40)), 40L, "application/octet-stream")))
                .isInstanceOf(CocoStorageException.class)
                .extracting(exception -> ((CocoStorageException) exception).code())
                .isEqualTo(CocoStorageErrorCode.SCAN_REJECTED.code());
        assertThat(delegate.request()).isNull();
    }

    @Test
    void runsTheValidatorBeforeTheScanner() {
        RecordingCocoObjectStorage delegate = new RecordingCocoObjectStorage();
        List<String> order = new ArrayList<>();
        ValidatingCocoObjectStorage passing = new ValidatingCocoObjectStorage(delegate, probe -> order.add("validator"),
                probe -> order.add("scanner"), 8);

        passing.put(CocoObjectPutRequest.of("uploads/ordered.bin", new ByteArrayInputStream(patternBytes(40)), 40L,
                "application/octet-stream"));

        assertThat(order).containsExactly("validator", "scanner");
        List<String> rejected = new ArrayList<>();
        ValidatingCocoObjectStorage failing = new ValidatingCocoObjectStorage(delegate, probe -> {
            rejected.add("validator");
            throw new CocoStorageException(CocoStorageErrorCode.SIGNATURE_MISMATCH, "bin");
        }, probe -> rejected.add("scanner"), 8);
        assertThatThrownBy(() -> failing.put(CocoObjectPutRequest.of("uploads/ordered.bin",
                new ByteArrayInputStream(patternBytes(40)), 40L, "application/octet-stream")))
                .isInstanceOf(CocoStorageException.class);
        assertThat(rejected).containsExactly("validator");
    }

    @Test
    void delegatesReadAndDeleteOperationsAndReturnsTheDelegateResult() {
        RecordingCocoObjectStorage delegate = new RecordingCocoObjectStorage();
        ValidatingCocoObjectStorage storage = storage(delegate, 8);

        assertThat(storage.stat("uploads/known.bin")).isEqualTo(delegate.metadata("uploads/known.bin"));
        assertThat(storage.open("uploads/known.bin").metadata()).isEqualTo(delegate.metadata("uploads/known.bin"));
        assertThat(storage.exists("uploads/known.bin")).isTrue();
        assertThat(storage.delete("uploads/known.bin")).isTrue();
        assertThat(delegate.observedKeys()).containsExactly("uploads/known.bin", "uploads/known.bin",
                "uploads/known.bin", "uploads/known.bin");
    }

    @Test
    void propagatesCloseToACloseableDelegateAndStaysSilentForAPlainOne() throws Exception {
        ClosingCocoObjectStorage closeable = new ClosingCocoObjectStorage();
        ValidatingCocoObjectStorage closing = storage(closeable, 8);

        assertThatThrownBy(closing::close).isInstanceOf(IOException.class).hasMessage("delegate close failure");
        assertThat(closeable.closeCount()).isEqualTo(1);
        ValidatingCocoObjectStorage plain = storage(new RecordingCocoObjectStorage(), 8);
        assertThatCode(plain::close).doesNotThrowAnyException();
    }

    private static ValidatingCocoObjectStorage storage(CocoObjectStorage delegate, int probeSize) {
        return new ValidatingCocoObjectStorage(delegate, probe -> {
        }, probe -> {
        }, probeSize);
    }

    private static byte[] patternBytes(int size) {
        byte[] content = new byte[size];
        for (int index = 0; index < content.length; index++) {
            content[index] = (byte) (index * 7 + 3);
        }
        return content;
    }

    private static CocoObjectMetadata fakeMetadata(String key, long size) {
        return new CocoObjectMetadata(key, size, "application/octet-stream", "a".repeat(64),
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static final class RecordingCocoObjectStorage implements CocoObjectStorage {

        private final List<String> observedKeys = new ArrayList<>();

        private CocoObjectPutRequest request;

        private byte[] content;

        @Override
        public CocoObjectMetadata put(CocoObjectPutRequest request) {
            this.request = request;
            try {
                this.content = request.content().readAllBytes();
            }
            catch (IOException exception) {
                throw new CocoStorageException(CocoStorageErrorCode.STORAGE_IO_FAILURE, exception);
            }
            return fakeMetadata(request.key(), this.content.length);
        }

        @Override
        public CocoObjectResource open(String key) {
            this.observedKeys.add(key);
            return new CocoObjectResource(metadata(key), new ByteArrayResource(new byte[] { 1 }));
        }

        @Override
        public CocoObjectMetadata stat(String key) {
            this.observedKeys.add(key);
            return metadata(key);
        }

        @Override
        public boolean exists(String key) {
            this.observedKeys.add(key);
            return true;
        }

        @Override
        public boolean delete(String key) {
            this.observedKeys.add(key);
            return true;
        }

        private CocoObjectMetadata metadata(String key) {
            return fakeMetadata(key, 1L);
        }

        private CocoObjectPutRequest request() {
            return this.request;
        }

        private byte[] content() {
            return this.content;
        }

        private List<String> observedKeys() {
            return this.observedKeys;
        }
    }

    private static final class ClosingCocoObjectStorage implements CocoObjectStorage, AutoCloseable {

        private int closeCount;

        @Override
        public CocoObjectMetadata put(CocoObjectPutRequest request) {
            throw new UnsupportedOperationException("not used by close tests");
        }

        @Override
        public CocoObjectResource open(String key) {
            throw new UnsupportedOperationException("not used by close tests");
        }

        @Override
        public CocoObjectMetadata stat(String key) {
            throw new UnsupportedOperationException("not used by close tests");
        }

        @Override
        public boolean exists(String key) {
            throw new UnsupportedOperationException("not used by close tests");
        }

        @Override
        public boolean delete(String key) {
            throw new UnsupportedOperationException("not used by close tests");
        }

        @Override
        public void close() throws IOException {
            this.closeCount++;
            throw new IOException("delegate close failure");
        }

        private int closeCount() {
            return this.closeCount;
        }
    }

    private static final class OneBytePerReadInputStream extends InputStream {

        private final InputStream source;

        private OneBytePerReadInputStream(InputStream source) {
            this.source = source;
        }

        @Override
        public int read() throws IOException {
            return this.source.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            int value = this.source.read();
            if (value < 0) {
                return -1;
            }
            bytes[offset] = (byte) value;
            return 1;
        }
    }
}
