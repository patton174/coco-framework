package io.github.coco.i18n;

/**
 * Coco 消息资源包注册表。
 * <p>
 * 只暴露添加 basename 的能力，避免业务模块直接接触消息源实现细节。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-i18n}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public interface CocoMessageBundleRegistry {

    /**
     * <p>
     * 添加一个消息资源 basename。
     * </p>
     * @param basename 消息资源 basename
     */
    void add(String basename);
}
