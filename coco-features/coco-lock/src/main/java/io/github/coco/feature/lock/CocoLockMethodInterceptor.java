package io.github.coco.feature.lock;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

/**
 * {@link CocoLocked} 的方法拦截器。
 */
public final class CocoLockMethodInterceptor implements MethodInterceptor {

    private final CocoLockManager lockManager;

    private final CocoLockProperties properties;

    private static final Pattern SIMPLE_VARIABLE = Pattern.compile("#([A-Za-z_][A-Za-z0-9_]*|p[0-9]+)");

    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 创建锁方法拦截器。
     * @param lockManager 锁管理器
     * @param properties 锁配置
     */
    public CocoLockMethodInterceptor(CocoLockManager lockManager, CocoLockProperties properties) {
        this.lockManager = lockManager;
        this.properties = properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = AopUtils.getMostSpecificMethod(invocation.getMethod(), invocation.getThis().getClass());
        CocoLocked locked = findAnnotation(method, invocation.getThis().getClass());
        if (locked == null) {
            return invocation.proceed();
        }
        rejectAsyncReturn(method);
        String key = resolveKey(locked.value(), method, invocation.getThis(), invocation.getArguments());
        Duration waitTime = durationOrDefault(locked.waitTime(), this.properties.getDefaultWait(), "waitTime");
        Duration leaseTime = durationOrDefault(locked.lease(), this.properties.getDefaultLease(), "lease");
        Optional<CocoLock> lock = this.lockManager.tryLock(key, waitTime, leaseTime);
        if (lock.isEmpty()) {
            throw new CocoLockAcquisitionException(key);
        }
        try (CocoLock heldLock = lock.get()) {
            return invocation.proceed();
        }
    }

    private CocoLocked findAnnotation(Method method, Class<?> targetClass) {
        CocoLocked methodAnnotation = org.springframework.core.annotation.AnnotatedElementUtils
                .findMergedAnnotation(method, CocoLocked.class);
        return methodAnnotation != null ? methodAnnotation : org.springframework.core.annotation.AnnotatedElementUtils
                .findMergedAnnotation(targetClass, CocoLocked.class);
    }

    private String resolveKey(String source, Method method, Object target, Object[] arguments) {
        if (!StringUtils.hasText(source)) {
            throw new CocoLockException("Coco lock key must not be blank");
        }
        String key;
        try {
            if (!source.contains("#")) {
                key = source;
            }
            else {
                key = resolveSimpleSpel(source, new MethodBasedEvaluationContext(target, method, arguments,
                        this.parameterNameDiscoverer));
            }
        }
        catch (RuntimeException ex) {
            throw new CocoLockException("Failed to evaluate Coco lock key expression", ex);
        }
        if (!StringUtils.hasText(key)) {
            throw new CocoLockException("Coco lock key must not be blank");
        }
        return key;
    }

    private static String resolveSimpleSpel(String source, StandardEvaluationContext context) {
        Matcher matcher = SIMPLE_VARIABLE.matcher(source);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            Object value = context.lookupVariable(matcher.group(1));
            if (value == null) {
                throw new CocoLockException("Coco lock key variable is null: " + matcher.group());
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(value.toString()));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private static Duration durationOrDefault(String value, Duration defaultValue, String name) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Duration.parse(value);
        }
        catch (RuntimeException ex) {
            throw new CocoLockException("Invalid Coco lock " + name + " duration: " + value, ex);
        }
    }

    private static void rejectAsyncReturn(Method method) {
        Class<?> returnType = method.getReturnType();
        if (Future.class.isAssignableFrom(returnType) || CompletionStage.class.isAssignableFrom(returnType)
                || "reactor.core.publisher.Mono".equals(returnType.getName())
                || "reactor.core.publisher.Flux".equals(returnType.getName())) {
            throw new CocoLockException("@CocoLocked does not support asynchronous return type: " + returnType.getName());
        }
    }
}
