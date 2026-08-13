package io.github.coco.feature.audit;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Objects;

/**
 * 声明式审计方法调用的最小只读描述。
 * <p>
 * 该对象有意不包含目标实例、参数、返回值、异常对象、HTTP 请求或凭据。失败时只公开异常的完整类名。
 * </p>
 * @param targetClass 被调用 Spring Bean 的目标类型
 * @param method 被解析后的业务方法
 * @param annotation 生效的审计声明
 * @param success 调用是否成功
 * @param exceptionType 失败异常的完整类名；成功时为空
 * @param startedAt 调用开始时间
 * @param completedAt 调用完成时间
 * @author patton174
 * @since 2.0.3
 */
public record CocoAuditInvocation(Class<?> targetClass, Method method, CocoAudited annotation, boolean success,
        String exceptionType, Instant startedAt, Instant completedAt) {

    /**
     * 创建安全的调用描述。
     */
    public CocoAuditInvocation {
        targetClass = Objects.requireNonNull(targetClass, "targetClass must not be null");
        method = Objects.requireNonNull(method, "method must not be null");
        annotation = Objects.requireNonNull(annotation, "annotation must not be null");
        startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
        if (success && exceptionType != null) {
            throw new IllegalArgumentException("exceptionType must be null for a successful invocation");
        }
        if (!success && (exceptionType == null || exceptionType.isBlank())) {
            throw new IllegalArgumentException("exceptionType must not be blank for a failed invocation");
        }
        exceptionType = normalize(exceptionType);
    }

    /**
     * 返回耗时毫秒数。
     * @return 非负耗时毫秒数
     */
    public long durationMillis() {
        return Math.max(0L, this.completedAt.toEpochMilli() - this.startedAt.toEpochMilli());
    }

    static String requireType(CocoAudited annotation) {
        Objects.requireNonNull(annotation, "annotation must not be null");
        String type = normalize(annotation.type());
        if (type == null) {
            throw new IllegalArgumentException("Coco audit type must not be blank");
        }
        return type;
    }

    static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
