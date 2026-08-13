package io.github.coco.feature.security.authorization;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * 解析 Spring 代理调用对应的方法授权声明。
 * <p>
 * 方法声明优先于类型声明；解析结果按具体方法与目标类型缓存，避免同一代理调用重复解析或重复决策。
 * </p>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoMethodAuthorizationResolver {

    private final ConcurrentMap<MethodKey, Optional<CocoAuthorizationRequirement>> cache = new ConcurrentHashMap<>();

    /**
     * 解析方法的授权要求。
     * @param method 代理暴露的方法
     * @param targetClass 目标类型
     * @return 授权要求；未声明时为空
     */
    public Optional<CocoAuthorizationRequirement> resolve(Method method, Class<?> targetClass) {
        Method specificMethod = BridgeMethodResolver.findBridgedMethod(AopUtils.getMostSpecificMethod(method, targetClass));
        return this.cache.computeIfAbsent(new MethodKey(specificMethod, targetClass),
                key -> findRequirement(method, key.method(), key.targetClass()));
    }

    /**
     * 判断调用是否需要方法授权。
     * @param method 代理暴露的方法
     * @param targetClass 目标类型
     * @return 已声明授权时返回 {@code true}
     */
    public boolean requiresAuthorization(Method method, Class<?> targetClass) {
        return resolve(method, targetClass).isPresent();
    }

    private static Optional<CocoAuthorizationRequirement> findRequirement(Method invokedMethod, Method specificMethod,
            Class<?> targetClass) {
        CocoAuthorize authorize = findMethodAnnotation(specificMethod);
        if (authorize == null && !specificMethod.equals(invokedMethod)) {
            authorize = findMethodAnnotation(invokedMethod);
        }
        if (authorize == null) {
            authorize = findInterfaceMethodAnnotation(specificMethod, targetClass);
        }
        if (authorize == null) {
            authorize = AnnotatedElementUtils.findMergedAnnotation(targetClass, CocoAuthorize.class);
        }
        if (authorize == null) {
            authorize = findInterfaceTypeAnnotation(targetClass);
        }
        return authorize == null ? Optional.empty() : Optional.of(CocoAuthorizationRequirement.from(authorize));
    }

    private static CocoAuthorize findMethodAnnotation(Method method) {
        return AnnotatedElementUtils.findMergedAnnotation(method, CocoAuthorize.class);
    }

    private static CocoAuthorize findInterfaceMethodAnnotation(Method method, Class<?> targetClass) {
        for (Class<?> interfaceType : org.springframework.util.ClassUtils.getAllInterfacesForClassAsSet(targetClass)) {
            try {
                CocoAuthorize authorize = findMethodAnnotation(interfaceType.getMethod(method.getName(), method.getParameterTypes()));
                if (authorize != null) {
                    return authorize;
                }
            }
            catch (NoSuchMethodException ignored) {
                // This interface does not declare the target method.
            }
        }
        return null;
    }

    private static CocoAuthorize findInterfaceTypeAnnotation(Class<?> targetClass) {
        for (Class<?> interfaceType : org.springframework.util.ClassUtils.getAllInterfacesForClassAsSet(targetClass)) {
            CocoAuthorize authorize = AnnotatedElementUtils.findMergedAnnotation(interfaceType, CocoAuthorize.class);
            if (authorize != null) {
                return authorize;
            }
        }
        return null;
    }

    private record MethodKey(Method method, Class<?> targetClass) {
    }
}
