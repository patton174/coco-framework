package io.github.coco.feature.lock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/** 为 {@link CocoLock} 同步方法获得并在 finally 中释放锁的 AOP 切面。 */
public final class CocoLockAspect implements MethodInterceptor, Ordered {
    private final CocoLockManager lockManager;
    private final CocoLockKeyResolver keyResolver;
    private final CocoLockProperties properties;

    /** 创建切面。 */
    public CocoLockAspect(CocoLockManager lockManager, CocoLockKeyResolver keyResolver, CocoLockProperties properties) {
        this.lockManager = lockManager;
        this.keyResolver = keyResolver;
        this.properties = properties;
    }

    /** 对方法或所属类型的锁声明执行互斥。 */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object target = invocation.getThis();
        if (target == null) { return invocation.proceed(); }
        LockOperation operation = resolveOperation(invocation.getMethod(), target.getClass());
        if (operation.lock() == null) { return invocation.proceed(); }
        rejectAsynchronousReturn(operation.methods());
        CocoLock lock = operation.lock();
        String key = this.keyResolver.resolve(lock.key(), target, operation.specificMethod(), invocation.getArguments());
        CocoLockResult result = this.lockManager.tryAcquire(new CocoLockRequest(key, duration(lock.leaseMillis(),
                this.properties.getLease()), duration(lock.waitMillis(), this.properties.getWait()),
                duration(lock.pollIntervalMillis(), this.properties.getPollInterval())));
        if (!result.acquired()) { throw failure(result.status()); }
        CocoLockHandle handle = result.handle();
        Throwable failure = null;
        try {
            if (handle.lost()) { throw CocoLockErrorCode.UNAVAILABLE.system(); }
            Object value = invocation.proceed();
            if (handle.lost()) { throw CocoLockErrorCode.UNAVAILABLE.system(); }
            return value;
        }
        catch (Throwable exception) {
            failure = exception;
            throw exception;
        }
        finally {
            try { handle.close(); }
            catch (Throwable releaseFailure) {
                if (failure != null) { failure.addSuppressed(releaseFailure); }
                else { throw releaseFailure; }
            }
        }
    }

    private static LockOperation resolveOperation(Method signatureMethod, Class<?> targetClass) {
        Method specificMethod = AopUtils.getMostSpecificMethod(signatureMethod, targetClass);
        Set<Method> methods = new LinkedHashSet<>();
        methods.add(specificMethod);
        methods.add(signatureMethod);
        addInterfaceMethods(targetClass, signatureMethod, methods);
        addBridgeMethods(targetClass, specificMethod, methods);
        for (Method method : methods) {
            CocoLock lock = AnnotatedElementUtils.findMergedAnnotation(method, CocoLock.class);
            if (lock != null) { return new LockOperation(lock, specificMethod, List.copyOf(methods)); }
        }
        for (Class<?> type : types(targetClass)) {
            CocoLock lock = AnnotatedElementUtils.findMergedAnnotation(type, CocoLock.class);
            if (lock != null) { return new LockOperation(lock, specificMethod, List.copyOf(methods)); }
        }
        return new LockOperation(null, specificMethod, List.copyOf(methods));
    }

    static boolean hasLock(Method method, Class<?> targetClass) {
        return resolveOperation(method, targetClass).lock() != null;
    }

    private static void addBridgeMethods(Class<?> targetClass, Method specificMethod, Set<Method> methods) {
        methods.add(BridgeMethodResolver.findBridgedMethod(specificMethod));
        for (Method candidate : targetClass.getMethods()) {
            if (!candidate.isBridge()) { continue; }
            Method bridged = BridgeMethodResolver.findBridgedMethod(candidate);
            if (bridged.equals(specificMethod)) {
                methods.add(candidate);
                methods.add(bridged);
            }
        }
    }

    private static void addInterfaceMethods(Class<?> type, Method signatureMethod, Set<Method> methods) {
        for (Class<?> interfaceType : type.getInterfaces()) {
            for (Method candidate : interfaceType.getMethods()) {
                if (matches(signatureMethod, candidate)) { methods.add(candidate); }
            }
            addInterfaceMethods(interfaceType, signatureMethod, methods);
        }
        Class<?> superclass = type.getSuperclass();
        if (superclass != null && superclass != Object.class) { addInterfaceMethods(superclass, signatureMethod, methods); }
    }

    private static boolean matches(Method expected, Method candidate) {
        if (!expected.getName().equals(candidate.getName())
                || expected.getParameterCount() != candidate.getParameterCount()) { return false; }
        Class<?>[] expectedParameters = expected.getParameterTypes();
        Class<?>[] candidateParameters = candidate.getParameterTypes();
        for (int index = 0; index < expectedParameters.length; index++) {
            if (!expectedParameters[index].isAssignableFrom(candidateParameters[index])
                    && !candidateParameters[index].isAssignableFrom(expectedParameters[index])) { return false; }
        }
        return true;
    }

    private static List<Class<?>> types(Class<?> targetClass) {
        List<Class<?>> result = new ArrayList<>();
        for (Class<?> current = targetClass; current != null && current != Object.class; current = current.getSuperclass()) {
            result.add(current);
        }
        addInterfaceTypes(targetClass, result);
        return result;
    }

    private static void addInterfaceTypes(Class<?> type, List<Class<?>> types) {
        for (Class<?> interfaceType : type.getInterfaces()) {
            if (!types.contains(interfaceType)) {
                types.add(interfaceType);
                addInterfaceTypes(interfaceType, types);
            }
        }
        Class<?> superclass = type.getSuperclass();
        if (superclass != null && superclass != Object.class) { addInterfaceTypes(superclass, types); }
    }

    private static void rejectAsynchronousReturn(List<Method> methods) {
        for (Method method : methods) {
            Class<?> type = method.getReturnType();
            if (CompletionStage.class.isAssignableFrom(type) || isReactive(type)) {
                throw CocoLockErrorCode.ASYNCHRONOUS_RETURN.system();
            }
        }
    }

    private static boolean isReactive(Class<?> type) {
        if ("org.reactivestreams.Publisher".equals(type.getName()) || "java.util.concurrent.Flow$Publisher".equals(type.getName())) {
            return true;
        }
        for (Class<?> candidate : type.getInterfaces()) { if (isReactive(candidate)) { return true; } }
        Class<?> parent = type.getSuperclass();
        return parent != null && parent != Object.class && isReactive(parent);
    }

    private static java.time.Duration duration(long millis, java.time.Duration fallback) {
        return millis < 0L ? fallback : java.time.Duration.ofMillis(millis);
    }

    private static RuntimeException failure(CocoLockStore.AcquireResult result) {
        return switch (result) {
            case CONTENDED -> CocoLockErrorCode.TIMED_OUT.conflict();
            case UNAVAILABLE -> CocoLockErrorCode.UNAVAILABLE.system();
            case ACQUIRED -> throw new IllegalArgumentException("acquired lock has no failure");
        };
    }

    @Override public int getOrder() { return this.properties.getAspectOrder(); }

    private record LockOperation(CocoLock lock, Method specificMethod, List<Method> methods) { }
}
