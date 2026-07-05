package io.github.coco.feature.web.logging;

/**
 * Coco 接口访问日志输出级别。
 * <p>
 * 用于隔离控制 Coco 接口访问日志打印级别，避免影响业务应用自身日志级别。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public enum CocoAccessLogLevel {

    /**
     * <p>
     * 关闭接口访问日志打印。
     * </p>
     */
    OFF,

    /**
     * <p>
     * 使用 ERROR 级别打印。
     * </p>
     */
    ERROR,

    /**
     * <p>
     * 使用 WARN 级别打印。
     * </p>
     */
    WARN,

    /**
     * <p>
     * 使用 INFO 级别打印。
     * </p>
     */
    INFO,

    /**
     * <p>
     * 使用 DEBUG 级别打印。
     * </p>
     */
    DEBUG,

    /**
     * <p>
     * 使用 TRACE 级别打印。
     * </p>
     */
    TRACE;

    /**
     * <p>
     * 判断当前级别是否允许打印。
     * </p>
     * @return 非 {@link #OFF} 时返回 {@code true}
     */
    public boolean enabled() {
        return this != OFF;
    }
}
