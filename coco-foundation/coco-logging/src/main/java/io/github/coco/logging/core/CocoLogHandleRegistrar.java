package io.github.coco.logging.core;

/**
 * Coco 日志句柄注册器。
 * <p>
 * 框架内部模块可以声明该 SPI，把自己的日志隔离边界注册到日志模块。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-logging}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoLogHandleRegistrar {

    /**
     * <p>
     * 注册日志句柄。
     * </p>
     * @param registry 日志句柄注册表
     */
    void register(CocoLogHandleRegistry registry);
}
