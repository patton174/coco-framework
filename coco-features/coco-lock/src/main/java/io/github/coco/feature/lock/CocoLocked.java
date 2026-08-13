package io.github.coco.feature.lock;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明 Spring Bean 方法执行期间需要持有 Coco 锁。
 * <p>
 * {@link #value()} 支持简单 SpEL，例如 {@code order:#p0} 或 {@code order:#orderId}。{@link #waitTime()} 和
 * {@link #lease()} 使用 ISO-8601 {@link java.time.Duration} 文本，例如 {@code PT500MS}；空字符串表示使用
 * {@code coco.lock} 的默认值。
 * </p>
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface CocoLocked {

    /**
     * 锁键或 SpEL 锁键表达式。
     * @return 锁键表达式
     */
    String value();

    /**
     * 覆盖默认等待时间的 ISO-8601 Duration 文本。
     * @return 等待时间；空字符串表示默认值
     */
    String waitTime() default "";

    /**
     * 覆盖默认租期的 ISO-8601 Duration 文本。
     * @return 租期；空字符串表示默认值
     */
    String lease() default "";
}
