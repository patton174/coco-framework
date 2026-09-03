package io.github.coco.storage;

/**
 * Coco 上传内容扫描 SPI。
 * <p>
 * 框架不实现恶意软件检测，本接口只是集成点。真实的病毒扫描依赖外部引擎（例如 ClamAV）
 * 及其持续更新的病毒库；框架无法在不引入外部依赖和特征库维护责任的前提下提供有效检测。
 * 未接入外部引擎时，默认实现 {@link NoOpCocoFileScanner} 不做任何检查。
 * </p>
 * <p>
 * 实现方判定拒绝时抛出 {@link CocoStorageException}，通过返回即表示放行。
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
public interface CocoFileScanner {

    /**
     * <p>
     * 扫描上传内容探测快照。
     * </p>
     * @param probe 内容探测快照
     * @throws CocoStorageException 内容被拒绝时抛出
     */
    void scan(CocoContentProbe probe);
}
