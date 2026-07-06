package io.github.coco.feature.web.replay;

import java.time.Instant;

/**
 * Coco Web 防重放存储。
 * <p>
 * 定义请求随机串占用契约，默认实现使用进程内内存；业务项目可以提供 Redis、数据库或其他分布式实现。
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
@FunctionalInterface
public interface CocoReplayStore {

    /**
     * <p>
     * 尝试占用防重放键。
     * </p>
     * @param key 防重放键
     * @param expiresAt 键过期时间
     * @return 占用成功时返回 {@code true}；键已存在时返回 {@code false}
     */
    boolean reserve(CocoReplayKey key, Instant expiresAt);
}
