package io.github.coco.messaging.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.coco.messaging.CocoMessageEnvelope;
import io.github.coco.messaging.CocoMessageHandler;
import io.github.coco.messaging.CocoMessageListener;
import io.github.coco.messaging.CocoMessageTransport;
import io.github.coco.messaging.CocoMessagingException;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * 在所有单例 Bean 初始化后注册注解监听器和 Handler Bean。
 */
public final class CocoMessageListenerRegistrar implements SmartInitializingSingleton {

    private final ConfigurableListableBeanFactory beanFactory;

    private final CocoMessageTransport transport;

    /**
     * @param beanFactory Bean 工厂
     * @param transport 消息传输
     */
    public CocoMessageListenerRegistrar(ConfigurableListableBeanFactory beanFactory, CocoMessageTransport transport) {
        this.beanFactory = beanFactory;
        this.transport = transport;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<Registration> registrations = new ArrayList<>();
        String[] beanNames = this.beanFactory.getBeanDefinitionNames();
        Arrays.sort(beanNames);
        for (String beanName : beanNames) {
            Object bean = this.beanFactory.getBean(beanName);
            if (bean instanceof CocoMessageHandler handler) {
                registrations.add(Registration.handler(beanName, handler));
            }
            collectAnnotatedMethods(beanName, bean, registrations);
        }
        registrations.sort(Comparator.comparingInt(Registration::order).thenComparing(Registration::beanName)
                .thenComparing(Registration::signature));
        registrations.forEach(registration -> this.transport.subscribe(registration.topic(), registration.handler()));
    }

    private void collectAnnotatedMethods(String beanName, Object bean, List<Registration> registrations) {
        Class<?> targetType = AopUtils.getTargetClass(bean);
        Method[] methods = targetType.getMethods();
        Arrays.sort(methods, Comparator.comparing(Method::toGenericString));
        Map<LogicalMethod, ListenerMethod> listenerMethods = new LinkedHashMap<>();
        for (Method method : methods) {
            if (method.isBridge() || method.isSynthetic()) {
                continue;
            }
            Method mostSpecificMethod = AopUtils.getMostSpecificMethod(method, targetType);
            if (mostSpecificMethod.isBridge() || mostSpecificMethod.isSynthetic()) {
                continue;
            }
            CocoMessageListener listener = findListener(mostSpecificMethod, targetType);
            if (listener == null) {
                continue;
            }
            validateMethod(mostSpecificMethod, listener);
            Method invocableMethod = selectInvocableMethod(bean, mostSpecificMethod);
            if (!invocableMethod.trySetAccessible()) {
                throw new CocoMessagingException("coco.messaging.error.listener-signature-invalid",
                        mostSpecificMethod.toGenericString());
            }
            LogicalMethod logicalMethod = new LogicalMethod(mostSpecificMethod.getName(),
                    mostSpecificMethod.getParameterTypes());
            listenerMethods.putIfAbsent(logicalMethod, new ListenerMethod(listener, mostSpecificMethod, invocableMethod));
        }
        listenerMethods.values().forEach(listenerMethod -> registrations.add(Registration.method(beanName,
                listenerMethod.listener(), bean, listenerMethod.discoveryMethod(), listenerMethod.invocableMethod())));
    }

    private static CocoMessageListener findListener(Method method, Class<?> targetType) {
        CocoMessageListener listener = AnnotatedElementUtils.findMergedAnnotation(method, CocoMessageListener.class);
        if (listener != null) {
            return listener;
        }
        return findListenerOnInterfaces(method, targetType);
    }

    private static CocoMessageListener findListenerOnInterfaces(Method method, Class<?> type) {
        for (Class<?> interfaceType : type.getInterfaces()) {
            for (Method interfaceMethod : interfaceType.getMethods()) {
                if (matchesLogicalMethod(method, interfaceMethod)) {
                    CocoMessageListener listener = AnnotatedElementUtils.findMergedAnnotation(interfaceMethod,
                            CocoMessageListener.class);
                    if (listener != null) {
                        return listener;
                    }
                }
            }
            CocoMessageListener inheritedListener = findListenerOnInterfaces(method, interfaceType);
            if (inheritedListener != null) {
                return inheritedListener;
            }
        }
        Class<?> superclass = type.getSuperclass();
        return superclass == null || superclass == Object.class ? null : findListenerOnInterfaces(method, superclass);
    }

    private static boolean matchesLogicalMethod(Method targetMethod, Method candidateMethod) {
        if (!targetMethod.getName().equals(candidateMethod.getName())
                || targetMethod.getParameterCount() != candidateMethod.getParameterCount()) {
            return false;
        }
        Class<?>[] targetParameters = targetMethod.getParameterTypes();
        Class<?>[] candidateParameters = candidateMethod.getParameterTypes();
        for (int index = 0; index < targetParameters.length; index++) {
            if (!candidateParameters[index].isAssignableFrom(targetParameters[index])) {
                return false;
            }
        }
        return true;
    }

    private static Method selectInvocableMethod(Object bean, Method method) {
        try {
            return AopUtils.selectInvocableMethod(method, bean.getClass());
        }
        catch (IllegalStateException exception) {
            throw new CocoMessagingException("coco.messaging.error.listener-invocation-failed", exception,
                    method.toGenericString());
        }
    }

    private static void validateMethod(Method method, CocoMessageListener listener) {
        if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())
                || method.getReturnType() != Void.TYPE || method.getParameterCount() != 1
                || method.getParameterTypes()[0].isPrimitive()) {
            throw new CocoMessagingException("coco.messaging.error.listener-signature-invalid", method.toGenericString());
        }
        CocoMessageEnvelope.create(listener.topic(), null);
    }

    private record LogicalMethod(String name, List<Class<?>> parameterTypes) {

        private LogicalMethod(String name, Class<?>[] parameterTypes) {
            this(name, List.of(parameterTypes));
        }
    }

    private record ListenerMethod(CocoMessageListener listener, Method discoveryMethod, Method invocableMethod) {
    }

    private record Registration(String topic, int order, String beanName, String signature, CocoMessageHandler handler) {

        private static Registration handler(String beanName, CocoMessageHandler handler) {
            String topic = CocoMessageEnvelope.create(handler.topic(), null).topic();
            return new Registration(topic, 0, beanName, handler.getClass().getName(), handler);
        }

        private static Registration method(String beanName, CocoMessageListener listener, Object bean, Method discoveryMethod,
                Method invocableMethod) {
            Class<?> parameterType = discoveryMethod.getParameterTypes()[0];
            CocoMessageHandler handler = new CocoMessageHandler() {
                @Override
                public String topic() {
                    return listener.topic();
                }

                @Override
                public void handle(CocoMessageEnvelope envelope) {
                    invoke(bean, invocableMethod, parameterType, envelope);
                }
            };
            return new Registration(listener.topic(), listener.order(), beanName, discoveryMethod.toGenericString(), handler);
        }

        private static void invoke(Object bean, Method method, Class<?> parameterType, CocoMessageEnvelope envelope) {
            Object argument = parameterType == CocoMessageEnvelope.class ? envelope : envelope.payload();
            if (argument != null && !parameterType.isInstance(argument)) {
                throw new CocoMessagingException("coco.messaging.error.payload-type-mismatch", parameterType.getName(),
                        argument.getClass().getName());
            }
            try {
                method.invoke(bean, argument);
            }
            catch (IllegalAccessException exception) {
                throw new CocoMessagingException("coco.messaging.error.listener-invocation-failed", exception,
                        method.toGenericString());
            }
            catch (IllegalArgumentException exception) {
                throw new CocoMessagingException("coco.messaging.error.listener-invocation-failed", exception,
                        method.toGenericString());
            }
            catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new CocoMessagingException("coco.messaging.error.listener-invocation-failed", cause,
                        method.toGenericString());
            }
        }
    }
}
