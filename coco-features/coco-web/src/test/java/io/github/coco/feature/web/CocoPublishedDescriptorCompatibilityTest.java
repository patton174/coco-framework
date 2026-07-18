package io.github.coco.feature.web;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;

import io.github.coco.feature.web.context.CocoRequestParameterResolver;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.payload.CocoPayloadParameterResolver;
import io.github.coco.feature.web.context.payload.DefaultCocoPayloadParameterResolver;
import io.github.coco.feature.web.trace.CocoTraceFilter;
import io.github.coco.feature.web.trace.CocoTraceIdValidator;
import io.github.coco.feature.web.trace.DefaultCocoTraceIdValidator;
import io.github.coco.logging.access.CocoAccessLogRecorder;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * 已发布 Web descriptor 的行为兼容测试。
 */
@SuppressWarnings("deprecation")
class CocoPublishedDescriptorCompatibilityTest {

    @Test
    void publishedRequestParameterResolverFailsFastForNullProperties() {
        CocoWebContextAutoConfiguration configuration = new CocoWebContextAutoConfiguration();

        assertThrows(NullPointerException.class,
                () -> configuration.cocoRequestParameterResolver(null, proxy(CocoPayloadParameterResolver.class)));
    }

    @Test
    void publishedTraceRegistrationKeepsDefaultDispatcherAndAsyncSettings() {
        CocoWebProperties properties = new CocoWebProperties();
        CocoTraceIdValidator validator = new DefaultCocoTraceIdValidator(properties.getTrace());
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        CocoWebTraceAutoConfiguration configuration = new CocoWebTraceAutoConfiguration();

        FilterRegistrationBean<CocoTraceFilter> registration = configuration.cocoTraceFilterRegistration(
                properties,
                beanFactory.getBeanProvider(CocoAccessLogRecorder.class),
                proxy(CocoWebRequestContextResolver.class),
                validator);

        assertTrue(registration.determineDispatcherTypes().equals(java.util.EnumSet.allOf(DispatcherType.class)));
        assertTrue(registration.isAsyncSupported());
        assertTrue(registration.getUrlPatterns().isEmpty());
        assertTrue(!registration.isMatchAfter());
        assertTrue("cocoTraceFilter".equals(field(registration, "name")));
        assertTrue(registration.getOrder() == Integer.MIN_VALUE + 1);
    }

    @Test
    void currentTraceRegistrationKeepsExplicitDispatcherAndAsyncSettings() {
        CocoWebProperties properties = new CocoWebProperties();
        CocoTraceIdValidator validator = new DefaultCocoTraceIdValidator(properties.getTrace());
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        CocoWebTraceAutoConfiguration configuration = new CocoWebTraceAutoConfiguration();

        FilterRegistrationBean<CocoTraceFilter> registration = configuration.cocoTraceFilterRegistration(
                properties,
                beanFactory.getBeanProvider(CocoAccessLogRecorder.class),
                proxy(CocoWebRequestContextResolver.class),
                validator,
                beanFactory.getBeanProvider(io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter.class));

        assertTrue(registration.isAsyncSupported());
        assertTrue(registration.determineDispatcherTypes().containsAll(
                java.util.Set.of(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR)));
    }

    @Test
    void publishedRequestParameterResolverStillConstructsForRealProperties() {
        CocoPayloadParameterResolver payload = proxy(CocoPayloadParameterResolver.class);
        CocoRequestParameterResolver resolver = new CocoWebContextAutoConfiguration()
                .cocoRequestParameterResolver(new CocoWebProperties(), payload);

        assertTrue(resolver instanceof io.github.coco.feature.web.context.DefaultCocoRequestParameterResolver);
        assertSame(payload, field(resolver, "payloadParameterResolver"));
    }

    @Test
    void publishedRequestParameterResolverRestoresNestedAndPayloadDefaults() {
        CocoWebProperties properties = new CocoWebProperties();
        properties.setContext(null);
        properties.getContext().setParameter(null);

        CocoRequestParameterResolver resolver = new CocoWebContextAutoConfiguration()
                .cocoRequestParameterResolver(properties, null);

        assertTrue(field(resolver, "payloadParameterResolver") instanceof DefaultCocoPayloadParameterResolver);
        assertSame(properties.getContext().getParameter(), field(resolver, "properties"));
    }

    @Test
    void publishedTraceRegistrationKeepsNullBoundariesAndDefaultValidator() {
        CocoWebProperties properties = new CocoWebProperties();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        CocoWebRequestContextResolver resolver = proxy(CocoWebRequestContextResolver.class);
        CocoWebTraceAutoConfiguration configuration = new CocoWebTraceAutoConfiguration();

        assertThrows(NullPointerException.class, () -> configuration.cocoTraceFilterRegistration(
                null, beanFactory.getBeanProvider(CocoAccessLogRecorder.class), resolver, null));
        assertThrows(NullPointerException.class,
                () -> configuration.cocoTraceFilterRegistration(properties, null, resolver, null));
        assertThrows(NullPointerException.class, () -> configuration.cocoTraceFilterRegistration(
                properties, beanFactory.getBeanProvider(CocoAccessLogRecorder.class), null, null));

        FilterRegistrationBean<CocoTraceFilter> registration = configuration.cocoTraceFilterRegistration(
                properties, beanFactory.getBeanProvider(CocoAccessLogRecorder.class), resolver, null);
        assertTrue(field(registration.getFilter(), "traceIdValidator") instanceof DefaultCocoTraceIdValidator);
    }

    private static <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (instance, method, arguments) -> null));
    }

    private static Object field(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            }
            catch (NoSuchFieldException ex) {
                // Continue with the superclass.
            }
            catch (IllegalAccessException ex) {
                throw new AssertionError(ex);
            }
        }
        throw new AssertionError("Missing field " + name + " on " + target.getClass());
    }
}
