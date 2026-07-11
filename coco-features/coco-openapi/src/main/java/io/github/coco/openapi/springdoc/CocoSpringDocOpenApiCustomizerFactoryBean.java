package io.github.coco.openapi.springdoc;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;

import io.github.coco.openapi.core.CocoOpenApiMetadata;
import io.github.coco.openapi.core.CocoOpenApiMetadataProvider;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.ClassUtils;

/**
 * Coco SpringDoc OpenAPI 定制器工厂。
 * <p>
 * 在业务项目自行引入 SpringDoc 时，通过运行期代理注册 {@code OpenApiCustomizer}，将
 * Coco 的 OpenAPI 元数据写入 SpringDoc 文档模型。该实现不让 Coco 编译期或运行期强依赖
 * SpringDoc。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-openapi}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoSpringDocOpenApiCustomizerFactoryBean implements FactoryBean<Object>, BeanClassLoaderAware {

    /**
     * SpringDoc OpenAPI 定制器接口类名。
     */
    public static final String OPEN_API_CUSTOMIZER_CLASS = "org.springdoc.core.customizers.OpenApiCustomizer";

    /**
     * Swagger OpenAPI 模型类名。
     */
    public static final String OPEN_API_CLASS = "io.swagger.v3.oas.models.OpenAPI";

    /**
     * Swagger Info 模型类名。
     */
    public static final String INFO_CLASS = "io.swagger.v3.oas.models.info.Info";

    private final ObjectProvider<CocoOpenApiMetadataProvider> metadataProvider;

    private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

    /**
     * <p>
     * 创建 SpringDoc OpenAPI 定制器工厂。
     * </p>
     * @param metadataProvider Coco OpenAPI 元数据提供器延迟引用
     */
    public CocoSpringDocOpenApiCustomizerFactoryBean(ObjectProvider<CocoOpenApiMetadataProvider> metadataProvider) {
        this.metadataProvider = Objects.requireNonNull(metadataProvider, "metadataProvider must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader == null ? ClassUtils.getDefaultClassLoader() : classLoader;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getObject() {
        Class<?> customizerType = requiredClass(OPEN_API_CUSTOMIZER_CLASS);
        Class<?> openApiType = requiredClass(OPEN_API_CLASS);
        Class<?> infoType = requiredClass(INFO_CLASS);
        InvocationHandler handler =
                new SpringDocOpenApiCustomizerInvocationHandler(this.metadataProvider, openApiType, infoType);
        return Proxy.newProxyInstance(customizerType.getClassLoader(), new Class<?>[] { customizerType }, handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<?> getObjectType() {
        try {
            return ClassUtils.forName(OPEN_API_CUSTOMIZER_CLASS, this.beanClassLoader);
        }
        catch (ClassNotFoundException | LinkageError ex) {
            return Object.class;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSingleton() {
        return true;
    }

    private Class<?> requiredClass(String className) {
        try {
            return ClassUtils.forName(className, this.beanClassLoader);
        }
        catch (ClassNotFoundException | LinkageError ex) {
            throw new IllegalStateException("SpringDoc OpenAPI integration class is not available: " + className, ex);
        }
    }

    private static final class SpringDocOpenApiCustomizerInvocationHandler implements InvocationHandler {

        private final ObjectProvider<CocoOpenApiMetadataProvider> metadataProvider;

        private final Class<?> openApiType;

        private final Class<?> infoType;

        private SpringDocOpenApiCustomizerInvocationHandler(ObjectProvider<CocoOpenApiMetadataProvider> metadataProvider,
                Class<?> openApiType, Class<?> infoType) {
            this.metadataProvider = metadataProvider;
            this.openApiType = openApiType;
            this.infoType = infoType;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, args);
            }
            if ("customise".equals(method.getName()) && args != null && args.length == 1) {
                customize(args[0]);
                return null;
            }
            throw new UnsupportedOperationException("Unsupported SpringDoc OpenApiCustomizer method: " + method);
        }

        private static Object objectMethod(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "toString" -> "CocoSpringDocOpenApiCustomizer";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException("Unsupported object method: " + method);
            };
        }

        private void customize(Object openApi) throws ReflectiveOperationException {
            if (openApi == null) {
                return;
            }
            if (!this.openApiType.isInstance(openApi)) {
                throw new IllegalArgumentException("openApi must be an instance of " + this.openApiType.getName());
            }
            CocoOpenApiMetadata metadata = Objects.requireNonNull(this.metadataProvider.getObject().metadata(),
                    "OpenAPI metadata must not be null");
            Object info = currentInfo(openApi);
            invokeSetter(info, "setTitle", String.class, metadata.title());
            invokeSetter(info, "setVersion", String.class, metadata.version());
            if (metadata.descriptionOptional().isPresent()) {
                invokeSetter(info, "setDescription", String.class, metadata.description());
            }
            invokeSetter(openApi, "setInfo", this.infoType, info);
        }

        private Object currentInfo(Object openApi) throws ReflectiveOperationException {
            Object info = invokeNoArgs(openApi, "getInfo");
            if (info != null) {
                return info;
            }
            Constructor<?> constructor = this.infoType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        }

        private static Object invokeNoArgs(Object target, String name) throws ReflectiveOperationException {
            Method method = target.getClass().getMethod(name);
            return invokeReflective(target, method);
        }

        private static void invokeSetter(Object target, String name, Class<?> parameterType, Object value)
                throws ReflectiveOperationException {
            Method method = target.getClass().getMethod(name, parameterType);
            invokeReflective(target, method, value);
        }

        private static Object invokeReflective(Object target, Method method, Object... args)
                throws ReflectiveOperationException {
            try {
                return method.invoke(target, args);
            }
            catch (InvocationTargetException ex) {
                Throwable targetException = ex.getTargetException();
                if (targetException instanceof ReflectiveOperationException reflectiveException) {
                    throw reflectiveException;
                }
                if (targetException instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (targetException instanceof Error error) {
                    throw error;
                }
                throw ex;
            }
        }
    }
}
