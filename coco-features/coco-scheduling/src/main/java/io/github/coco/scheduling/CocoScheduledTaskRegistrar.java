package io.github.coco.scheduling;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;

/**
 * 在容器单例初始化完成后注册 {@link CocoScheduled} 方法任务。
 *
 * @since 1.0.0
 */
public final class CocoScheduledTaskRegistrar implements SmartInitializingSingleton {

    private final ConfigurableListableBeanFactory beanFactory;
    private final CocoTaskScheduler scheduler;
    private final CocoTaskDefinitionValidator validator;

    CocoScheduledTaskRegistrar(ConfigurableListableBeanFactory beanFactory, CocoTaskScheduler scheduler,
            CocoTaskDefinitionValidator validator) {
        this.beanFactory = beanFactory;
        this.scheduler = scheduler;
        this.validator = validator;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (String beanName : this.beanFactory.getBeanDefinitionNames()) {
            Object bean = this.beanFactory.getBean(beanName);
            Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
            if (targetClass == null) {
                continue;
            }
            methods(targetClass).stream()
                    .filter(method -> AnnotatedElementUtils.hasAnnotation(method, CocoScheduled.class))
                    .sorted(Comparator.comparing(Method::toGenericString))
                    .forEach(method -> register(beanName, bean, targetClass, method));
        }
    }

    private Set<Method> methods(Class<?> targetClass) {
        Set<Method> methods = new LinkedHashSet<>();
        Arrays.stream(ReflectionUtils.getAllDeclaredMethods(targetClass))
                .filter(method -> !method.isBridge() && !method.isSynthetic())
                .map(method -> mostSpecificUserMethod(method, targetClass))
                .filter(method -> !method.isBridge() && !method.isSynthetic())
                .forEach(methods::add);
        return methods;
    }

    private Method mostSpecificUserMethod(Method method, Class<?> targetClass) {
        Method bridged = BridgeMethodResolver.findBridgedMethod(method);
        return BridgeMethodResolver.findBridgedMethod(AopUtils.getMostSpecificMethod(bridged, targetClass));
    }

    private void register(String beanName, Object bean, Class<?> targetClass, Method targetMethod) {
        CocoScheduled scheduled = AnnotatedElementUtils.findMergedAnnotation(targetMethod, CocoScheduled.class);
        if (scheduled == null) {
            return;
        }
        String defaultName = taskName(beanName, targetMethod);
        if (targetMethod.getParameterCount() != 0) {
            throw this.validator.error(CocoSchedulingMessage.ANNOTATION_METHOD_ARGUMENTS, defaultName);
        }
        Method invocableMethod;
        try {
            invocableMethod = AopUtils.selectInvocableMethod(targetMethod, bean.getClass());
        }
        catch (IllegalStateException exception) {
            throw this.validator.error(CocoSchedulingMessage.ANNOTATION_METHOD_NOT_INVOCABLE, defaultName);
        }
        String taskName = value(scheduled.name());
        CocoTaskDefinition.Builder builder = CocoTaskDefinition.builder(
                taskName == null ? defaultName : taskName,
                () -> invoke(bean, invocableMethod));
        String cron = value(scheduled.cron());
        if (cron != null) {
            builder.cron(cron);
        }
        String fixedDelay = value(scheduled.fixedDelay());
        if (fixedDelay != null) {
            builder.fixedDelay(parseDuration(fixedDelay, defaultName));
        }
        String fixedRate = value(scheduled.fixedRate());
        if (fixedRate != null) {
            builder.fixedRate(parseDuration(fixedRate, defaultName));
        }
        String zone = value(scheduled.zone());
        if (zone != null) {
            try {
                builder.zone(ZoneId.of(zone));
            }
            catch (ZoneRulesException exception) {
                throw this.validator.error(CocoSchedulingMessage.ANNOTATION_ZONE_INVALID, defaultName);
            }
        }
        String initialDelay = value(scheduled.initialDelay());
        if (initialDelay != null) {
            builder.initialDelay(parseDuration(initialDelay, defaultName));
        }
        builder.overlapPolicy(scheduled.overlapPolicy()).enabled(scheduled.enabled());
        this.scheduler.register(builder.build());
    }

    private Duration parseDuration(String value, String taskName) {
        try {
            return DurationStyle.detectAndParse(value);
        }
        catch (IllegalArgumentException exception) {
            throw this.validator.error(CocoSchedulingMessage.ANNOTATION_DURATION_INVALID, taskName);
        }
    }

    private String value(String value) {
        String resolved = this.beanFactory.resolveEmbeddedValue(value);
        return resolved == null || resolved.isBlank() ? null : resolved.trim();
    }

    private String taskName(String beanName, Method method) {
        return beanName + "#" + method.getName();
    }

    private void invoke(Object bean, Method method) {
        try {
            AopUtils.invokeJoinpointUsingReflection(bean, method, new Object[0]);
        }
        catch (Throwable exception) {
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (exception instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(exception);
        }
    }
}
