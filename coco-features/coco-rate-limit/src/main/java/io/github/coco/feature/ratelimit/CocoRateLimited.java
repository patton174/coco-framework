package io.github.coco.feature.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

/**
 * 标记 Controller 或处理方法预期由指定的限流路由保护。
 * <p>
 * 注解只表达业务意图，不会创建隐式路由，也不会读取用户、角色或事务状态。实际拦截规则仍由
 * {@code coco.rate-limit.routes} 显式配置，并在 Servlet 入口处执行。
 * </p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface CocoRateLimited {

    /**
     * {@link #route()} 的简写。
     * @return 限流路由标识
     */
    @AliasFor("route")
    String value() default "";

    /**
     * 对应 {@code coco.rate-limit.routes} 中路由的标识。
     * @return 限流路由标识
     */
    @AliasFor("value")
    String route() default "";
}
