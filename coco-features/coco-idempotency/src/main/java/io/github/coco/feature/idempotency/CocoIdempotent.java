package io.github.coco.feature.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要请求幂等保护的 Controller 或处理方法。
 * <p>方法注解优先于类注解。该注解不缓存或回放首次响应，也不改变业务事务边界。</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface CocoIdempotent {

    /** @return 逻辑隔离命名空间；空值表示默认命名空间 */
    String namespace() default "";

    /** @return 保留秒数；负值使用 {@code coco.idempotency.ttl} */
    long ttlSeconds() default -1L;
}
