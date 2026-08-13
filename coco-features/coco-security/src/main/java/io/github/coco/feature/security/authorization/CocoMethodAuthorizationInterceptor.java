package io.github.coco.feature.security.authorization;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;

import io.github.coco.feature.security.context.CocoSecurityContextResolver;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * {@link CocoAuthorize} 的方法拦截器。
 * <p>
 * 授权决策在调用目标业务方法之前完成，不包装业务方法的返回值、受检异常或 {@link Error}。
 * </p>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoMethodAuthorizationInterceptor implements MethodInterceptor {

    private final CocoMethodAuthorizationManager authorizationManager;

    private final CocoSecurityContextResolver contextResolver;

    private final CocoMethodAuthorizationResolver authorizationResolver;

    /**
     * 创建方法授权拦截器。
     * @param authorizationManager 授权决策器
     * @param contextResolver 安全上下文解析器
     * @param authorizationResolver 注解解析器
     */
    public CocoMethodAuthorizationInterceptor(CocoMethodAuthorizationManager authorizationManager,
            CocoSecurityContextResolver contextResolver, CocoMethodAuthorizationResolver authorizationResolver) {
        this.authorizationManager = authorizationManager;
        this.contextResolver = contextResolver;
        this.authorizationResolver = authorizationResolver;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        Object target = Objects.requireNonNull(invocation.getThis(), "MethodInvocation target must not be null");
        Class<?> targetClass = target.getClass();
        Optional<CocoAuthorizationRequirement> requirement = this.authorizationResolver.resolve(method, targetClass);
        if (requirement.isPresent()) {
            this.authorizationManager.authorize(requirement.get(), this.contextResolver);
        }
        return invocation.proceed();
    }
}
