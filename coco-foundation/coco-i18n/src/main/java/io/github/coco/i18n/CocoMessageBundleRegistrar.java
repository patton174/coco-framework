package io.github.coco.i18n;

/**
 * Coco 模块消息资源注册器。
 * <p>
 * 框架模块通过该接口声明自己的国际化消息资源包，由 {@code coco-i18n} 在自动配置阶段统一合并到 Coco
 * 专用消息源中。
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
@FunctionalInterface
public interface CocoMessageBundleRegistrar {

    /**
     * <p>
     * 向 Coco 消息资源注册表添加当前模块的消息资源。
     * </p>
     * @param registry 消息资源注册表
     */
    void registerBundles(CocoMessageBundleRegistry registry);
}
