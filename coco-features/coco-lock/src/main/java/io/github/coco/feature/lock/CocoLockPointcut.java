package io.github.coco.feature.lock;

import java.lang.reflect.Method;

import org.springframework.aop.support.StaticMethodMatcherPointcut;

/** 只为实际声明了 {@link CocoLock} 的方法或类型创建代理的匹配点。 */
final class CocoLockPointcut extends StaticMethodMatcherPointcut {
    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return targetClass != null && CocoLockAspect.hasLock(method, targetClass);
    }
}
