package io.github.coco.web.replay;

import java.time.Instant;

/**
 * Coco Web 防重放存储。
 * <p>
 * 定义防重放键原子占用契约，默认实现使用进程内内存；业务项目可以提供 Redis、数据库或其他共享实现。
 * 实现必须在自身共享范围内保证同一未过期键最多只有一个调用占用成功，并允许已过期键再次占用。
 * 存储不可用、超时或其他基础设施失败必须向上抛出，不能转换为“键已存在”。
 * </p>
 * <p>
 * 该契约不要求防重放占用与业务写入处于同一事务，也不提供业务请求的 exactly-once 语义。
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
@FunctionalInterface
public interface CocoReplayStore {

    /**
     * <p>
     * 尝试占用防重放键。
     * </p>
     * @param key 防重放键
     * @param expiresAt 键的绝对过期时间
     * @return 占用成功时返回 {@code true}；同一键仍处于有效占用期时返回 {@code false}
     * @throws RuntimeException 存储不可用、超时或其他基础设施操作失败时抛出
     */
    boolean reserve(CocoReplayKey key, Instant expiresAt);
}
