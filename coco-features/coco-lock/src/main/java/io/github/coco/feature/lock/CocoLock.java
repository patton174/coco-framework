package io.github.coco.feature.lock;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为同步业务方法声明互斥锁。
 * <p>方法上的声明覆盖类型上的声明。key 可以是固定值，也可以是 Spring SpEL（{@code #{#p0}} 或 {@code #p0}）。</p>
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CocoLock {

    /** 锁键或 Spring SpEL 表达式。 */
    String key();

    /** 覆盖默认租约的毫秒数；负数时使用配置值。 */
    long leaseMillis() default -1L;

    /** 覆盖默认等待时长的毫秒数；负数时使用配置值。 */
    long waitMillis() default -1L;

    /** 覆盖默认轮询间隔的毫秒数；负数时使用配置值。 */
    long pollIntervalMillis() default -1L;
}
