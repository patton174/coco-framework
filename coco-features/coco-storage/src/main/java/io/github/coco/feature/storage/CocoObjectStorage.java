package io.github.coco.feature.storage;

import java.io.IOException;
import java.util.Optional;

/**
 * 流式对象存储服务 SPI。
 * <p>实现可由业务应用以同类型 Bean 完全替换；本接口不假定任何访问控制模型。</p>
 */
public interface CocoObjectStorage {

    /** 写入对象。实现不得关闭 {@link CocoObjectWriteRequest#inputStream()}。 */
    CocoObjectStat put(CocoObjectWriteRequest request) throws IOException;

    /** 按键读取对象；空结果明确表示对象不存在。调用方必须关闭返回的读取结果。 */
    Optional<CocoObjectReadResult> get(String key) throws IOException;

    /** 返回对象状态；{@link CocoObjectStat#found()} 为 {@code false} 表示对象不存在。 */
    CocoObjectStat stat(String key) throws IOException;

    /** 删除对象；返回 {@code true} 表示删除了原有对象。 */
    boolean delete(String key) throws IOException;

    /** 按键稳定排序列举对象。 */
    CocoObjectListResult list(String prefix, int limit, String continuationToken) throws IOException;
}
