package io.github.coco.feature.audit.core;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Coco 审计事件。
 * <p>
 * 表示一次具有审计意义的框架或业务动作。该对象只承载审计语义，不负责日志打印、数据库写入或消息投递。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-audit}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoAuditEvent {

    private final String type;

    private final String action;

    private final String resourceType;

    private final String resourceId;

    private final String traceId;

    private final String actor;

    private final String tenantId;

    private final boolean success;

    private final Instant occurredAt;

    private final Map<String, Object> attributes;

    private CocoAuditEvent(Builder builder) {
        this.type = requireText(builder.type, "type");
        this.action = normalize(builder.action);
        this.resourceType = normalize(builder.resourceType);
        this.resourceId = normalize(builder.resourceId);
        this.traceId = normalize(builder.traceId);
        this.actor = normalize(builder.actor);
        this.tenantId = normalize(builder.tenantId);
        this.success = builder.success;
        this.occurredAt = builder.occurredAt == null ? Instant.now() : builder.occurredAt;
        this.attributes = normalizeAttributes(builder.attributes);
    }

    /**
     * <p>
     * 创建审计事件构建器。
     * </p>
     * @param type 审计事件类型
     * @return 审计事件构建器
     */
    public static Builder builder(String type) {
        return new Builder(type);
    }

    /**
     * <p>
     * 返回审计事件类型。
     * </p>
     * @return 审计事件类型
     */
    public String type() {
        return this.type;
    }

    /**
     * <p>
     * 返回审计动作。
     * </p>
     * @return 审计动作；不存在时为空
     */
    public Optional<String> action() {
        return Optional.ofNullable(this.action);
    }

    /**
     * <p>
     * 返回资源类型。
     * </p>
     * @return 资源类型；不存在时为空
     */
    public Optional<String> resourceType() {
        return Optional.ofNullable(this.resourceType);
    }

    /**
     * <p>
     * 返回资源标识。
     * </p>
     * @return 资源标识；不存在时为空
     */
    public Optional<String> resourceId() {
        return Optional.ofNullable(this.resourceId);
    }

    /**
     * <p>
     * 返回 TraceId。
     * </p>
     * @return TraceId；不存在时为空
     */
    public Optional<String> traceId() {
        return Optional.ofNullable(this.traceId);
    }

    /**
     * <p>
     * 返回操作者标识。
     * </p>
     * @return 操作者标识；不存在时为空
     */
    public Optional<String> actor() {
        return Optional.ofNullable(this.actor);
    }

    /**
     * <p>
     * 返回租户标识。
     * </p>
     * @return 租户标识；不存在时为空
     */
    public Optional<String> tenantId() {
        return Optional.ofNullable(this.tenantId);
    }

    /**
     * <p>
     * 返回审计动作是否成功。
     * </p>
     * @return 成功时返回 {@code true}
     */
    public boolean success() {
        return this.success;
    }

    /**
     * <p>
     * 返回事件发生时间。
     * </p>
     * @return 事件发生时间
     */
    public Instant occurredAt() {
        return this.occurredAt;
    }

    /**
     * <p>
     * 返回审计扩展属性。
     * </p>
     * @return 审计扩展属性
     */
    public Map<String, Object> attributes() {
        return this.attributes;
    }

    private static String requireText(String value, String name) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, Object> normalizeAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        attributes.forEach((key, value) -> {
            String normalizedKey = normalize(key);
            if (normalizedKey != null && value != null) {
                normalized.put(normalizedKey, value);
            }
        });
        return Collections.unmodifiableMap(normalized);
    }

    /**
     * Coco 审计事件构建器。
     * <p>
     * 以链式方式设置审计事件属性。
     * </p>
     * <p>
     * 项目信息：
     * </p>
     * <ul>
     *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
     *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
     *   <li>模块：{@code coco-feature-audit}</li>
     * </ul>
     * @author patton174
     * @since 1.0.0
     */
    public static final class Builder {

        private final String type;

        private String action;

        private String resourceType;

        private String resourceId;

        private String traceId;

        private String actor;

        private String tenantId;

        private boolean success = true;

        private Instant occurredAt;

        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private Builder(String type) {
            this.type = type;
        }

        /**
         * <p>
         * 设置审计动作。
         * </p>
         * @param action 审计动作
         * @return 当前构建器
         */
        public Builder action(String action) {
            this.action = action;
            return this;
        }

        /**
         * <p>
         * 设置资源类型。
         * </p>
         * @param resourceType 资源类型
         * @return 当前构建器
         */
        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        /**
         * <p>
         * 设置资源标识。
         * </p>
         * @param resourceId 资源标识
         * @return 当前构建器
         */
        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        /**
         * <p>
         * 设置 TraceId。
         * </p>
         * @param traceId TraceId
         * @return 当前构建器
         */
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        /**
         * <p>
         * 设置操作者标识。
         * </p>
         * @param actor 操作者标识
         * @return 当前构建器
         */
        public Builder actor(String actor) {
            this.actor = actor;
            return this;
        }

        /**
         * <p>
         * 设置租户标识。
         * </p>
         * @param tenantId 租户标识
         * @return 当前构建器
         */
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * <p>
         * 设置审计动作是否成功。
         * </p>
         * @param success 是否成功
         * @return 当前构建器
         */
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        /**
         * <p>
         * 设置事件发生时间。
         * </p>
         * @param occurredAt 事件发生时间
         * @return 当前构建器
         */
        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        /**
         * <p>
         * 添加审计扩展属性。
         * </p>
         * @param name 属性名称
         * @param value 属性值
         * @return 当前构建器
         */
        public Builder attribute(String name, Object value) {
            String normalizedName = normalize(name);
            if (normalizedName != null && value != null) {
                this.attributes.put(normalizedName, value);
            }
            return this;
        }

        /**
         * <p>
         * 构建审计事件。
         * </p>
         * @return 审计事件
         */
        public CocoAuditEvent build() {
            return new CocoAuditEvent(this);
        }
    }
}
