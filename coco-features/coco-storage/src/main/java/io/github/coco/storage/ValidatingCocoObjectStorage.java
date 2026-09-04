package io.github.coco.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * 带上传内容校验的 Coco 对象存储装饰器。
 * <p>
 * 在委托实际写入之前读取内容头部若干字节做校验和扫描，随后把已读字节拼回流首部，
 * 因此被装饰的实现仍然拿到完整内容，整个过程不会把上传内容全部缓存到内存。
 * </p>
 * <p>
 * 读取类方法直接委托，不做额外处理。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-storage}</li>
 * </ul>
 * @author patton174
 * @since 1.1.0
 */
public class ValidatingCocoObjectStorage implements CocoObjectStorage, AutoCloseable {

    private final CocoObjectStorage delegate;

    private final CocoContentValidator validator;

    private final CocoFileScanner scanner;

    private final int probeSize;

    /**
     * <p>
     * 创建内容校验装饰器。
     * </p>
     * @param delegate 被装饰的对象存储
     * @param validator 内容校验器
     * @param scanner 内容扫描器
     * @param probeSize 内容头部探测字节数，非正数时回退到默认值
     */
    public ValidatingCocoObjectStorage(CocoObjectStorage delegate, CocoContentValidator validator,
            CocoFileScanner scanner, int probeSize) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.scanner = Objects.requireNonNull(scanner, "scanner must not be null");
        this.probeSize = probeSize <= 0 ? CocoStorageProperties.DEFAULT_PROBE_SIZE : probeSize;
    }

    /**
     * <p>
     * 校验并扫描内容头部后委托写入。
     * </p>
     * @param request 写入请求
     * @return 已持久化对象元数据
     */
    @Override
    public CocoObjectMetadata put(CocoObjectPutRequest request) {
        CocoObjectPutRequest checked = Objects.requireNonNull(request, "request must not be null");
        InputStream content = checked.content();
        byte[] probeBytes = readProbe(content);
        CocoContentProbe probe = CocoContentProbe.of(checked.key(), checked.contentType(), probeBytes);
        this.validator.validate(probe);
        this.scanner.scan(probe);
        InputStream restored = new SequenceInputStream(new ByteArrayInputStream(probeBytes), content);
        return this.delegate.put(new CocoObjectPutRequest(checked.key(), restored, checked.contentLength(),
                checked.contentType(), checked.overwritePolicy()));
    }

    /**
     * <p>
     * 委托打开对象读取资源。
     * </p>
     * @param key 对象键
     * @return 元数据和可流式读取的资源
     */
    @Override
    public CocoObjectResource open(String key) {
        return this.delegate.open(key);
    }

    /**
     * <p>
     * 委托查询对象元数据。
     * </p>
     * @param key 对象键
     * @return 对象元数据
     */
    @Override
    public CocoObjectMetadata stat(String key) {
        return this.delegate.stat(key);
    }

    /**
     * <p>
     * 委托判断对象是否存在。
     * </p>
     * @param key 对象键
     * @return 存在时返回 {@code true}
     */
    @Override
    public boolean exists(String key) {
        return this.delegate.exists(key);
    }

    /**
     * <p>
     * 委托删除对象。
     * </p>
     * @param key 对象键
     * @return 本次实际删除对象时返回 {@code true}
     */
    @Override
    public boolean delete(String key) {
        return this.delegate.delete(key);
    }

    /**
     * <p>
     * 关闭被装饰的对象存储。
     * </p>
     * <p>
     * 装饰器会遮挡委托实现的 {@link AutoCloseable}，而本地实现需要 {@code close()} 完成孤儿回收，
     * 因此这里显式向下传递关闭动作；委托实现不可关闭时为空操作。
     * </p>
     * @throws Exception 委托实现关闭失败时抛出
     */
    @Override
    public void close() throws Exception {
        if (this.delegate instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    private byte[] readProbe(InputStream content) {
        byte[] buffer = new byte[this.probeSize];
        int read = 0;
        try {
            while (read < buffer.length) {
                int count = content.read(buffer, read, buffer.length - read);
                if (count < 0) {
                    break;
                }
                read += count;
            }
        }
        catch (IOException exception) {
            throw new CocoStorageException(CocoStorageErrorCode.STORAGE_IO_FAILURE, exception);
        }
        return read == buffer.length ? buffer : Arrays.copyOf(buffer, read);
    }
}
