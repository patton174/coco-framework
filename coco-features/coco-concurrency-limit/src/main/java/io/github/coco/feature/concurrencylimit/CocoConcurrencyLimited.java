package io.github.coco.feature.concurrencylimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

/**
 * 标记 Controller 或处理方法由指定的显式并发限制路由保护。
 * <p>
 * 注解只引用 {@code coco.concurrency-limit.routes} 中的路由，不创建隐式限制，也不读取用户、角色或租户模型。
 * </p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface CocoConcurrencyLimited {

    /**
     * {@link #route()} 的简写。
     * @return 并发限制路由标识
     */
    @AliasFor("route")
    String value() default "";

    /**
     * 返回显式并发限制路由标识。
     * @return 并发限制路由标识
     */
    @AliasFor("value")
    String route() default "";
}
