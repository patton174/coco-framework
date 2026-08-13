package io.github.coco.feature.security.authorization;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

/**
 * 解析 Spring 代理调用对应的方法授权声明。
 * <p>
 * 方法声明优先于类型声明；解析结果按原始调用方法、目标类型与最具体方法缓存，隔离不同 JDK 接口契约。
 * 多个适用接口声明不一致时拒绝解析，避免依赖接口枚举顺序选择授权要求。
 * </p>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoMethodAuthorizationResolver {

    private static final String AUTHORIZATION_CONFLICT_MESSAGE =
            "Conflicting Coco method authorization declarations";

    private final ConcurrentMap<MethodKey, AuthorizationResolution> cache = new ConcurrentHashMap<>();

    /**
     * 解析方法的授权要求。
     * @param method 代理暴露的方法
     * @param targetClass 目标类型
     * @return 授权要求；未声明时为空
     */
    public Optional<CocoAuthorizationRequirement> resolve(Method method, Class<?> targetClass) {
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(targetClass, "targetClass must not be null");
        Method specificMethod = BridgeMethodResolver.findBridgedMethod(
                AopUtils.getMostSpecificMethod(method, targetClass));
        AuthorizationResolution resolution = this.cache.computeIfAbsent(
                new MethodKey(method, targetClass, specificMethod),
                key -> findResolution(key.invokedMethod(), key.mostSpecificMethod(), key.targetClass()));
        return resolution.resolvedRequirement();
    }

    /**
     * 判断调用是否需要方法授权。
     * @param method 代理暴露的方法
     * @param targetClass 目标类型
     * @return 已声明授权时返回 {@code true}
     */
    public boolean requiresAuthorization(Method method, Class<?> targetClass) {
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(targetClass, "targetClass must not be null");
        Method specificMethod = BridgeMethodResolver.findBridgedMethod(
                AopUtils.getMostSpecificMethod(method, targetClass));
        AuthorizationResolution resolution = this.cache.computeIfAbsent(
                new MethodKey(method, targetClass, specificMethod),
                key -> findResolution(key.invokedMethod(), key.mostSpecificMethod(), key.targetClass()));
        return resolution.requiresAuthorization();
    }

    private static AuthorizationResolution findResolution(Method invokedMethod, Method specificMethod,
            Class<?> targetClass) {
        CocoAuthorize implementationMethodAnnotation = findMethodAnnotation(specificMethod);
        if (implementationMethodAnnotation != null) {
            return AuthorizationResolution.of(CocoAuthorizationRequirement.from(implementationMethodAnnotation));
        }

        if (invokedMethod.getDeclaringClass().isInterface()) {
            CocoAuthorize invokedInterfaceMethodAnnotation = findMethodAnnotation(invokedMethod);
            return invokedInterfaceMethodAnnotation == null
                    ? resolveTypeRequirement(targetClass)
                    : AuthorizationResolution.of(CocoAuthorizationRequirement.from(invokedInterfaceMethodAnnotation));
        }

        AuthorizationResolution interfaceMethodRequirement = mergeRequirements(
                findInterfaceMethodRequirements(specificMethod, targetClass));
        return interfaceMethodRequirement.requiresAuthorization()
                ? interfaceMethodRequirement
                : resolveTypeRequirement(targetClass);
    }

    private static CocoAuthorize findMethodAnnotation(Method method) {
        return AnnotatedElementUtils.getMergedAnnotation(method, CocoAuthorize.class);
    }

    private static List<CocoAuthorizationRequirement> findInterfaceMethodRequirements(Method method,
            Class<?> targetClass) {
        List<CocoAuthorizationRequirement> requirements = new ArrayList<>();
        for (Class<?> interfaceType : ClassUtils.getAllInterfacesForClassAsSet(targetClass)) {
            try {
                Method interfaceMethod = interfaceType.getMethod(method.getName(), method.getParameterTypes());
                CocoAuthorize authorize = findMethodAnnotation(interfaceMethod);
                if (authorize != null) {
                    requirements.add(CocoAuthorizationRequirement.from(authorize));
                }
            }
            catch (NoSuchMethodException ignored) {
                // This interface does not declare the target method.
            }
        }
        return requirements;
    }

    private static AuthorizationResolution resolveTypeRequirement(Class<?> targetClass) {
        CocoAuthorize classAnnotation = AnnotatedElementUtils.getMergedAnnotation(targetClass, CocoAuthorize.class);
        if (classAnnotation != null) {
            return AuthorizationResolution.of(CocoAuthorizationRequirement.from(classAnnotation));
        }
        List<CocoAuthorizationRequirement> interfaceRequirements = new ArrayList<>();
        for (Class<?> interfaceType : ClassUtils.getAllInterfacesForClassAsSet(targetClass)) {
            CocoAuthorize authorize = AnnotatedElementUtils.getMergedAnnotation(interfaceType, CocoAuthorize.class);
            if (authorize != null) {
                interfaceRequirements.add(CocoAuthorizationRequirement.from(authorize));
            }
        }
        return mergeRequirements(interfaceRequirements);
    }

    private static AuthorizationResolution mergeRequirements(
            List<CocoAuthorizationRequirement> requirements) {
        if (requirements.isEmpty()) {
            return AuthorizationResolution.NONE;
        }
        CocoAuthorizationRequirement first = requirements.get(0);
        if (requirements.stream().allMatch(first::equals)) {
            return AuthorizationResolution.of(first);
        }
        return AuthorizationResolution.CONFLICT;
    }

    private record MethodKey(Method invokedMethod, Class<?> targetClass, Method mostSpecificMethod) {
    }

    private record AuthorizationResolution(Optional<CocoAuthorizationRequirement> value, boolean conflict) {

        private static final AuthorizationResolution NONE = new AuthorizationResolution(Optional.empty(), false);

        private static final AuthorizationResolution CONFLICT = new AuthorizationResolution(Optional.empty(), true);

        private static AuthorizationResolution of(CocoAuthorizationRequirement requirement) {
            return new AuthorizationResolution(Optional.of(requirement), false);
        }

        private Optional<CocoAuthorizationRequirement> resolvedRequirement() {
            if (this.conflict) {
                throw new IllegalStateException(AUTHORIZATION_CONFLICT_MESSAGE);
            }
            return this.value;
        }

        private boolean requiresAuthorization() {
            return this.conflict || this.value.isPresent();
        }
    }
}
