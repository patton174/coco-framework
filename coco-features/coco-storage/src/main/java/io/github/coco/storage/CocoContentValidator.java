package io.github.coco.storage;

/**
 * Coco 上传内容校验 SPI。
 * <p>
 * 校验器在内容真正写入存储之前执行；判定拒绝时抛出 {@link CocoStorageException}，
 * 通过返回即表示放行。业务方可声明自己的实现取代默认的魔数校验器。
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
@FunctionalInterface
public interface CocoContentValidator {

    /**
     * <p>
     * 校验上传内容探测快照。
     * </p>
     * @param probe 内容探测快照
     * @throws CocoStorageException 内容被拒绝时抛出
     */
    void validate(CocoContentProbe probe);
}
