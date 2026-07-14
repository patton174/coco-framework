package io.github.coco.feature.openapi.springdoc;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.coco.feature.openapi.core.CocoOpenApiMetadata;
import io.github.coco.feature.openapi.core.CocoOpenApiMetadataProvider;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.FactoryBean;
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

    /**
     * Swagger Components 模型类名。
     */
    public static final String COMPONENTS_CLASS = "io.swagger.v3.oas.models.Components";

    /**
     * Swagger Schema 模型类名。
     */
    public static final String SCHEMA_CLASS = "io.swagger.v3.oas.models.media.Schema";

    /**
     * Swagger 对象 Schema 模型类名。
     */
    public static final String OBJECT_SCHEMA_CLASS = "io.swagger.v3.oas.models.media.ObjectSchema";

    /**
     * Swagger 布尔 Schema 模型类名。
     */
    public static final String BOOLEAN_SCHEMA_CLASS = "io.swagger.v3.oas.models.media.BooleanSchema";

    /**
     * Swagger 整数 Schema 模型类名。
     */
    public static final String INTEGER_SCHEMA_CLASS = "io.swagger.v3.oas.models.media.IntegerSchema";

    /**
     * Swagger 字符串 Schema 模型类名。
     */
    public static final String STRING_SCHEMA_CLASS = "io.swagger.v3.oas.models.media.StringSchema";

    private final CocoOpenApiMetadataProvider metadataProvider;

    private final boolean responseSchemasEnabled;

    private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

    /**
     * <p>
     * 创建 SpringDoc OpenAPI 定制器工厂。
     * </p>
     * @param metadataProvider Coco OpenAPI 元数据提供器
     */
    public CocoSpringDocOpenApiCustomizerFactoryBean(CocoOpenApiMetadataProvider metadataProvider) {
        this(metadataProvider, true);
    }

    /**
     * <p>
     * 创建 SpringDoc OpenAPI 定制器工厂。
     * </p>
     * @param metadataProvider Coco OpenAPI 元数据提供器
     * @param responseSchemasEnabled 是否发布 Coco 统一响应和异常响应组件
     */
    public CocoSpringDocOpenApiCustomizerFactoryBean(CocoOpenApiMetadataProvider metadataProvider,
            boolean responseSchemasEnabled) {
        this.metadataProvider = Objects.requireNonNull(metadataProvider, "metadataProvider must not be null");
        this.responseSchemasEnabled = responseSchemasEnabled;
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
        Class<?> componentsType = requiredClass(COMPONENTS_CLASS);
        Class<?> schemaType = requiredClass(SCHEMA_CLASS);
        Class<?> objectSchemaType = requiredClass(OBJECT_SCHEMA_CLASS);
        Class<?> booleanSchemaType = requiredClass(BOOLEAN_SCHEMA_CLASS);
        Class<?> integerSchemaType = requiredClass(INTEGER_SCHEMA_CLASS);
        Class<?> stringSchemaType = requiredClass(STRING_SCHEMA_CLASS);
        InvocationHandler handler =
                new SpringDocOpenApiCustomizerInvocationHandler(this.metadataProvider, this.responseSchemasEnabled,
                        openApiType, infoType, componentsType, schemaType, objectSchemaType, booleanSchemaType,
                        integerSchemaType, stringSchemaType);
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

        private final CocoOpenApiMetadataProvider metadataProvider;

        private final boolean responseSchemasEnabled;

        private final Class<?> openApiType;

        private final Class<?> infoType;

        private final Class<?> componentsType;

        private final Class<?> schemaType;

        private final Class<?> objectSchemaType;

        private final Class<?> booleanSchemaType;

        private final Class<?> integerSchemaType;

        private final Class<?> stringSchemaType;

        private SpringDocOpenApiCustomizerInvocationHandler(CocoOpenApiMetadataProvider metadataProvider,
                boolean responseSchemasEnabled, Class<?> openApiType, Class<?> infoType, Class<?> componentsType,
                Class<?> schemaType, Class<?> objectSchemaType, Class<?> booleanSchemaType, Class<?> integerSchemaType,
                Class<?> stringSchemaType) {
            this.metadataProvider = metadataProvider;
            this.responseSchemasEnabled = responseSchemasEnabled;
            this.openApiType = openApiType;
            this.infoType = infoType;
            this.componentsType = componentsType;
            this.schemaType = schemaType;
            this.objectSchemaType = objectSchemaType;
            this.booleanSchemaType = booleanSchemaType;
            this.integerSchemaType = integerSchemaType;
            this.stringSchemaType = stringSchemaType;
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
            CocoOpenApiMetadata metadata = Objects.requireNonNull(this.metadataProvider.metadata(),
                    "OpenAPI metadata must not be null");
            Object info = currentInfo(openApi);
            invokeSetter(info, "setTitle", String.class, metadata.title());
            invokeSetter(info, "setVersion", String.class, metadata.version());
            if (metadata.descriptionOptional().isPresent()) {
                invokeSetter(info, "setDescription", String.class, metadata.description());
            }
            invokeSetter(openApi, "setInfo", this.infoType, info);
            if (this.responseSchemasEnabled) {
                registerResponseSchemas(openApi);
            }
        }

        private Object currentInfo(Object openApi) throws ReflectiveOperationException {
            Object info = invokeNoArgs(openApi, "getInfo");
            if (info != null) {
                return info;
            }
            Constructor<?> constructor = this.infoType.getDeclaredConstructor();
            return constructor.newInstance();
        }

        private void registerResponseSchemas(Object openApi) throws ReflectiveOperationException {
            Object components = invokeNoArgs(openApi, "getComponents");
            if (components == null) {
                components = this.componentsType.getDeclaredConstructor().newInstance();
                invokeSetter(openApi, "setComponents", this.componentsType, components);
            }
            Map<String, Object> schemas = schemas(components);
            schemas.putIfAbsent("CocoApiResponse", responseSchema());
            schemas.putIfAbsent("CocoApiErrorResponse", errorResponseSchema());
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> schemas(Object components) throws ReflectiveOperationException {
            Object currentSchemas = invokeNoArgs(components, "getSchemas");
            if (currentSchemas instanceof Map<?, ?> schemas) {
                return (Map<String, Object>) schemas;
            }
            Map<String, Object> schemas = new LinkedHashMap<>();
            invokeSetter(components, "setSchemas", Map.class, schemas);
            return schemas;
        }

        private Object responseSchema() throws ReflectiveOperationException {
            Object response = newInstance(this.objectSchemaType);
            addProperty(response, "success", newInstance(this.booleanSchemaType));
            addProperty(response, "code", newInstance(this.integerSchemaType));
            addProperty(response, "message", newInstance(this.stringSchemaType));
            addProperty(response, "data", nullableSchema());
            required(response, List.of("success", "code", "message"));
            return response;
        }

        private Object errorResponseSchema() throws ReflectiveOperationException {
            Object response = newInstance(this.objectSchemaType);
            Object success = newInstance(this.booleanSchemaType);
            invokeSetter(success, "setDefault", Object.class, Boolean.FALSE);
            addProperty(response, "success", success);
            addProperty(response, "code", newInstance(this.integerSchemaType));
            addProperty(response, "message", newInstance(this.stringSchemaType));
            addProperty(response, "data", nullableSchema());
            required(response, List.of("success", "code", "message"));
            return response;
        }

        private Object nullableSchema() throws ReflectiveOperationException {
            Object schema = newInstance(this.schemaType);
            invokeSetter(schema, "setNullable", Boolean.class, Boolean.TRUE);
            return schema;
        }

        private void addProperty(Object schema, String name, Object property) throws ReflectiveOperationException {
            Method method = schema.getClass().getMethod("addProperty", String.class, this.schemaType);
            invokeReflective(schema, method, name, property);
        }

        private static void required(Object schema, List<String> properties) throws ReflectiveOperationException {
            invokeSetter(schema, "setRequired", List.class, properties);
        }

        private static Object newInstance(Class<?> type) throws ReflectiveOperationException {
            return type.getDeclaredConstructor().newInstance();
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
