package io.github.coco.logging.access;

/**
 * Coco 接口访问日志输出样式。
 * <p>
 * 控制默认接口访问日志格式化器输出文本，业务项目也可以提供自己的 {@link CocoAccessLogFormatter} Bean 完全接管格式。
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
public enum CocoAccessLogStyle {

    /**
     * <p>
     * 键值对文本样式。
     * </p>
     */
    TEXT,

    /**
     * <p>
     * JSON 文本样式。
     * </p>
     */
    JSON
}
