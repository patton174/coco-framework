package io.github.coco.messaging.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

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
        for (Method method : methods) {
            CocoMessageListener listener = AnnotatedElementUtils.findMergedAnnotation(method,
                    CocoMessageListener.class);
            if (listener == null) {
                continue;
            }
            validateMethod(method, listener);
            if (!method.trySetAccessible()) {
                throw new CocoMessagingException("coco.messaging.error.listener-signature-invalid", method.toGenericString());
            }
            registrations.add(Registration.method(beanName, listener, bean, method));
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

    private record Registration(String topic, int order, String beanName, String signature, CocoMessageHandler handler) {

        private static Registration handler(String beanName, CocoMessageHandler handler) {
            String topic = CocoMessageEnvelope.create(handler.topic(), null).topic();
            return new Registration(topic, 0, beanName, handler.getClass().getName(), handler);
        }

        private static Registration method(String beanName, CocoMessageListener listener, Object bean, Method method) {
            Class<?> parameterType = method.getParameterTypes()[0];
            CocoMessageHandler handler = new CocoMessageHandler() {
                @Override
                public String topic() {
                    return listener.topic();
                }

                @Override
                public void handle(CocoMessageEnvelope envelope) {
                    invoke(bean, method, parameterType, envelope);
                }
            };
            return new Registration(listener.topic(), listener.order(), beanName, method.toGenericString(), handler);
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
