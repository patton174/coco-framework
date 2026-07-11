package io.github.coco.web.replay;

/**
 * Coco Web 防重放存储类型。
 * <p>
 * 控制框架默认注册进程内存储还是基于业务现有 JDBC 基础设施的共享存储。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public enum CocoReplayStoreType {

    /**
     * <p>
     * 使用当前应用进程内的内存存储。
     * </p>
     */
    IN_MEMORY,

    /**
     * <p>
     * 使用业务项目提供的 {@code JdbcOperations} 共享存储。
     * </p>
     */
    JDBC
}
