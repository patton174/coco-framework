package io.github.coco.feature.lock;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;

/** 为 {@link CocoLock} 同步方法获得并在 finally 中释放锁的 AOP 切面。 */
@Aspect
public final class CocoLockAspect implements Ordered {
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
    @Around("@annotation(io.github.coco.feature.lock.CocoLock) || @within(io.github.coco.feature.lock.CocoLock)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getMethod();
        Method specificMethod = AopUtils.getMostSpecificMethod(method, joinPoint.getTarget().getClass());
        CocoLock lock = AnnotatedElementUtils.findMergedAnnotation(specificMethod, CocoLock.class);
        if (lock == null) { lock = AnnotatedElementUtils.findMergedAnnotation(joinPoint.getTarget().getClass(), CocoLock.class); }
        if (lock == null) { return joinPoint.proceed(); }
        rejectAsynchronousReturn(specificMethod);
        String key = this.keyResolver.resolve(lock.key(), joinPoint.getTarget(), specificMethod, joinPoint.getArgs());
        CocoLockResult result = this.lockManager.tryAcquire(new CocoLockRequest(key, duration(lock.leaseMillis(),
                this.properties.getLease()), duration(lock.waitMillis(), this.properties.getWait()),
                duration(lock.pollIntervalMillis(), this.properties.getPollInterval())));
        if (!result.acquired()) { throw failure(result.status()); }
        try (CocoLockHandle ignored = result.handle()) { return joinPoint.proceed(); }
    }

    private static void rejectAsynchronousReturn(Method method) {
        Class<?> type = method.getReturnType();
        if (CompletionStage.class.isAssignableFrom(type) || isReactive(type)) { throw CocoLockErrorCode.ASYNCHRONOUS_RETURN.system(); }
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
}
