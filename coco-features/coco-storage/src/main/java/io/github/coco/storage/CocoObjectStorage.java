package io.github.coco.storage;

/**
 * Coco 文件和对象存储 SPI。
 * <p>
 * 该接口只负责对象内容及其稳定元数据。业务应用自行决定 HTTP Controller、授权、数据库附件表和对象键命名策略。
 * </p>
 */
public interface CocoObjectStorage {

    /**
     * 流式写入对象。
     * @param request 写入请求
     * @return 已持久化对象元数据
     */
    CocoObjectMetadata put(CocoObjectPutRequest request);

    /**
     * 打开对象的流式读取资源。
     * @param key 对象键
     * @return 元数据和可流式读取的资源
     */
    CocoObjectResource open(String key);

    /**
     * 查询对象元数据。
     * @param key 对象键
     * @return 对象元数据
     */
    CocoObjectMetadata stat(String key);

    /**
     * 判断对象是否存在。
     * @param key 对象键
     * @return 存在时返回 {@code true}
     */
    boolean exists(String key);

    /**
     * 删除对象。
     * <p>
     * 删除是幂等的：对象已不存在时返回 {@code false}，不会抛出“未找到”异常。
     * </p>
     * @param key 对象键
     * @return 本次实际删除对象时返回 {@code true}
     */
    boolean delete(String key);
}
