package io.github.coco.scheduling;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.Arrays;
import java.util.Comparator;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.convert.DurationStyle;
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
            Class<?> beanType = this.beanFactory.getType(beanName, false);
            if (beanType == null) {
                continue;
            }
            Object bean = this.beanFactory.getBean(beanName);
            Arrays.stream(ReflectionUtils.getAllDeclaredMethods(beanType))
                    .filter(method -> AnnotatedElementUtils.hasAnnotation(method, CocoScheduled.class))
                    .sorted(Comparator.comparing(Method::toGenericString))
                    .forEach(method -> register(beanName, bean, method));
        }
    }

    private void register(String beanName, Object bean, Method method) {
        CocoScheduled scheduled = AnnotatedElementUtils.findMergedAnnotation(method, CocoScheduled.class);
        if (scheduled == null) {
            return;
        }
        if (method.getParameterCount() != 0) {
            throw this.validator.error(CocoSchedulingMessage.ANNOTATION_METHOD_ARGUMENTS, taskName(beanName, method));
        }
        String taskName = value(scheduled.name());
        CocoTaskDefinition.Builder builder = CocoTaskDefinition.builder(
                taskName == null ? taskName(beanName, method) : taskName,
                () -> invoke(bean, method));
        String cron = value(scheduled.cron());
        if (cron != null) {
            builder.cron(cron);
        }
        String fixedDelay = value(scheduled.fixedDelay());
        if (fixedDelay != null) {
            builder.fixedDelay(parseDuration(fixedDelay, taskName(beanName, method)));
        }
        String fixedRate = value(scheduled.fixedRate());
        if (fixedRate != null) {
            builder.fixedRate(parseDuration(fixedRate, taskName(beanName, method)));
        }
        String zone = value(scheduled.zone());
        if (zone != null) {
            try {
                builder.zone(ZoneId.of(zone));
            }
            catch (ZoneRulesException exception) {
                throw this.validator.error(CocoSchedulingMessage.ANNOTATION_ZONE_INVALID, taskName(beanName, method));
            }
        }
        String initialDelay = value(scheduled.initialDelay());
        if (initialDelay != null) {
            builder.initialDelay(parseDuration(initialDelay, taskName(beanName, method)));
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
        ReflectionUtils.makeAccessible(method);
        try {
            method.invoke(bean);
        }
        catch (IllegalAccessException exception) {
            throw new IllegalStateException("Coco scheduled method is not accessible", exception);
        }
        catch (InvocationTargetException exception) {
            Throwable targetException = exception.getTargetException();
            if (targetException instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (targetException instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(targetException);
        }
    }
}
