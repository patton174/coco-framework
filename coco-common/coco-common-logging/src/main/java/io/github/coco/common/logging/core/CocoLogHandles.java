package io.github.coco.common.logging.core;

/**
 * Coco 内置日志句柄。
 * <p>
 * 集中声明框架基础设施默认注册的日志隔离边界，业务侧仍可通过自定义注册器追加自己的句柄。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-logging}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoLogHandles {

    /**
     * 接口访问日志句柄名称。
     */
    public static final String ACCESS = "access";

    /**
     * 应用生命周期日志句柄名称。
     */
    public static final String LIFECYCLE = "lifecycle";

    private CocoLogHandles() {
    }

    /**
     * <p>
     * 注册 Coco 内置日志句柄。
     * </p>
     * @param registry 日志句柄注册表
     */
    public static void registerDefaults(CocoLogHandleRegistry registry) {
        registry.register(CocoLogHandle.of(ACCESS, "io.github.coco.access", CocoLogLevel.INFO));
        registry.register(CocoLogHandle.of(LIFECYCLE, "io.github.coco.lifecycle", CocoLogLevel.INFO));
    }
}
