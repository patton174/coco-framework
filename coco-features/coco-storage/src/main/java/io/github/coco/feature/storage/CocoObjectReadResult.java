package io.github.coco.feature.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** 对象读取结果。关闭本对象会关闭底层输入流。 */
public final class CocoObjectReadResult implements AutoCloseable {
    private final CocoObjectMetadata metadata;
    private final InputStream inputStream;
    public CocoObjectReadResult(CocoObjectMetadata metadata, InputStream inputStream) { this.metadata = Objects.requireNonNull(metadata); this.inputStream = Objects.requireNonNull(inputStream); }
    public CocoObjectMetadata metadata() { return this.metadata; }
    public InputStream inputStream() { return this.inputStream; }
    @Override public void close() throws IOException { this.inputStream.close(); }
}
