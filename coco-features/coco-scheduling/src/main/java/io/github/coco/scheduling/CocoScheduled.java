package io.github.coco.scheduling;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明由 Coco 调度器注册的方法任务。
 * <p>
 * {@link #cron()}、{@link #fixedDelay()} 与 {@link #fixedRate()} 必须且只能指定一个。固定周期和初始延迟
 * 使用 Spring {@code DurationStyle} 支持的文本，例如 {@code 10s} 或 {@code PT10S}。
 * </p>
 *
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CocoScheduled {

    /** 稳定任务名称；未指定时使用 {@code beanName#methodName}。 */
    String name() default "";

    /** Cron 表达式。 */
    String cron() default "";

    /** 固定延迟。 */
    String fixedDelay() default "";

    /** 固定频率。 */
    String fixedRate() default "";

    /** Cron 时区，例如 {@code Asia/Shanghai}。 */
    String zone() default "";

    /** 首次执行前的延迟。 */
    String initialDelay() default "";

    /** 重叠执行策略。 */
    CocoTaskOverlapPolicy overlapPolicy() default CocoTaskOverlapPolicy.SKIP;

    /** 是否注册到底层调度器。 */
    boolean enabled() default true;
}
