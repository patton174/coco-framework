package io.github.coco.feature.audit;

import java.lang.reflect.Method;
import java.time.Instant;

import io.github.coco.feature.audit.core.CocoAuditPublisher;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

/**
 * {@link CocoAudited} 的方法拦截器。
 */
final class CocoAuditMethodInterceptor implements MethodInterceptor {

    private final CocoAuditPublisher publisher;

    private final CocoAuditEventFactory eventFactory;

    CocoAuditMethodInterceptor(CocoAuditPublisher publisher, CocoAuditEventFactory eventFactory) {
        this.publisher = publisher;
        this.eventFactory = eventFactory;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Class<?> targetClass = ClassUtils.getUserClass(invocation.getThis());
        Method method = BridgeMethodResolver.findBridgedMethod(
                AopUtils.getMostSpecificMethod(invocation.getMethod(), targetClass));
        CocoAudited audited = findAnnotation(method, invocation.getMethod(), targetClass);
        if (audited == null) {
            return invocation.proceed();
        }
        CocoAuditInvocation.requireType(audited);
        Instant startedAt = Instant.now();
        Object result;
        try {
            result = invocation.proceed();
        }
        catch (Throwable businessFailure) {
            try {
                publish(targetClass, method, audited, false, businessFailure.getClass().getName(), startedAt);
            }
            catch (Throwable publisherFailure) {
                if (publisherFailure != businessFailure) {
                    businessFailure.addSuppressed(publisherFailure);
                }
            }
            throw businessFailure;
        }
        publish(targetClass, method, audited, true, null, startedAt);
        return result;
    }

    private void publish(Class<?> targetClass, Method method, CocoAudited audited, boolean success, String exceptionType,
            Instant startedAt) {
        CocoAuditInvocation invocation = new CocoAuditInvocation(targetClass, method, audited, success, exceptionType,
                startedAt, Instant.now());
        this.publisher.publish(this.eventFactory.create(invocation));
    }

    static CocoAudited findAnnotation(Method method, Method invokedMethod, Class<?> targetClass) {
        CocoAudited annotation = AnnotatedElementUtils.findMergedAnnotation(method, CocoAudited.class);
        if (annotation != null) {
            return annotation;
        }
        Method interfaceMethod = ClassUtils.getInterfaceMethodIfPossible(method, targetClass);
        if (!interfaceMethod.equals(method)) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(interfaceMethod, CocoAudited.class);
        }
        if (annotation == null && invokedMethod.getDeclaringClass().isInterface()
                && !invokedMethod.equals(interfaceMethod)) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(invokedMethod, CocoAudited.class);
        }
        if (annotation != null) {
            return annotation;
        }
        annotation = AnnotatedElementUtils.findMergedAnnotation(targetClass, CocoAudited.class);
        return annotation != null ? annotation : findOnInterfaces(targetClass);
    }

    private static CocoAudited findOnInterfaces(Class<?> type) {
        for (Class<?> interfaceType : type.getInterfaces()) {
            CocoAudited annotation = AnnotatedElementUtils.findMergedAnnotation(interfaceType, CocoAudited.class);
            if (annotation != null) {
                return annotation;
            }
            annotation = findOnInterfaces(interfaceType);
            if (annotation != null) {
                return annotation;
            }
        }
        Class<?> superclass = type.getSuperclass();
        return superclass == null || superclass == Object.class ? null : findOnInterfaces(superclass);
    }
}
