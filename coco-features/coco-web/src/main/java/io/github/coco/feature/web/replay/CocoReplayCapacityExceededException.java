package io.github.coco.feature.web.replay;

import java.util.Objects;

import io.github.coco.exception.type.CocoSystemException;

/**
 * Coco Web 防重放存储容量耗尽异常。
 * <p>
 * 默认内存存储在全局或应用隔离容量达到硬上限时抛出该异常。自定义存储也可以复用该类型，
 * 让 Web 过滤链以统一、可观测的系统错误拒绝请求，而不是把容量耗尽误判为重复请求。
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
public final class CocoReplayCapacityExceededException extends CocoSystemException {

    private static final long serialVersionUID = 1L;

    /**
     * 容量耗尽消息编码。
     */
    public static final String MESSAGE_CODE = "coco.web.replay.capacity-exhausted";

    private final Scope scope;

    private final int capacity;

    /**
     * <p>
     * 创建防重放容量耗尽异常。
     * </p>
     * @param scope 达到上限的容量范围
     * @param capacity 已配置的硬上限
     */
    public CocoReplayCapacityExceededException(Scope scope, int capacity) {
        super(MESSAGE_CODE, "Replay protection capacity is exhausted.");
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    /**
     * <p>
     * 返回达到上限的容量范围。
     * </p>
     * @return 容量范围
     */
    public Scope scope() {
        return this.scope;
    }

    /**
     * <p>
     * 返回已配置的硬上限。
     * </p>
     * @return 容量硬上限
     */
    public int capacity() {
        return this.capacity;
    }

    /**
     * 防重放容量范围。
     */
    public enum Scope {

        /**
         * 当前存储实例的全局范围。
         */
        GLOBAL("global"),

        /**
         * 单个应用标识的隔离范围。
         */
        APP_ID("app-id");

        private final String id;

        Scope(String id) {
            this.id = id;
        }

        /**
         * <p>
         * 返回稳定范围标识。
         * </p>
         * @return 稳定范围标识
         */
        public String id() {
            return this.id;
        }
    }
}
