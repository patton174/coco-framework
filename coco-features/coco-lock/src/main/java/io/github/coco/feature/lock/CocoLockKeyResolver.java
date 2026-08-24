package io.github.coco.feature.lock;

import java.lang.reflect.Method;

/** 解析注解中的固定锁键或 Spring SpEL 锁键。 */
public interface CocoLockKeyResolver {
    /** 根据当前调用解析锁键。 */
    String resolve(String keyExpression, Object target, Method method, Object[] arguments);
}
