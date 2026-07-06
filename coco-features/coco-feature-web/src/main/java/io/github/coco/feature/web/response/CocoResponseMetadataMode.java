package io.github.coco.feature.web.response;

/**
 * Coco Web 响应元数据输出模式。
 * <p>
 * 控制统一响应体中是否额外携带请求追踪信息。默认模式不向响应体写入链路字段，链路标识优先通过响应头或
 * Cookie 输出。
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
public enum CocoResponseMetadataMode {

    /**
     * <p>
     * 不在响应体中输出链路元数据。
     * </p>
     */
    NONE,

    /**
     * <p>
     * 仅在响应体中输出 TraceId。
     * </p>
     */
    TRACE,

    /**
     * <p>
     * 在响应体中输出 TraceId 和请求路径，主要用于调试或兼容旧客户端。
     * </p>
     */
    DEBUG;

    /**
     * <p>
     * 判断当前模式是否允许在响应体中输出 TraceId。
     * </p>
     * @return 允许输出 TraceId 时返回 {@code true}
     */
    public boolean includesTraceId() {
        return this == TRACE || this == DEBUG;
    }

    /**
     * <p>
     * 判断当前模式是否允许在响应体中输出请求路径。
     * </p>
     * @return 允许输出请求路径时返回 {@code true}
     */
    public boolean includesPath() {
        return this == DEBUG;
    }
}
