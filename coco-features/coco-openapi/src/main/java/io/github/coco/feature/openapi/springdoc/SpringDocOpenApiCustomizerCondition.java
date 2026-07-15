package io.github.coco.feature.openapi.springdoc;

import java.lang.reflect.Method;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

/**
 * SpringDoc OpenAPI 定制器兼容性条件。
 * <p>
 * 仅在当前 classpath 同时提供 Coco 使用的 SpringDoc SPI 和 Swagger 模型，并且定制器方法签名
 * 与当前适配器兼容时才创建代理。这样混入不兼容 SpringDoc 版本时不会在应用启动或首次生成文档时失败。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
public final class SpringDocOpenApiCustomizerCondition extends SpringBootCondition {

    /**
     * {@inheritDoc}
     */
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ClassLoader classLoader = context.getClassLoader();
        boolean responseSchemasEnabled = responseSchemasEnabled(context);
        if (isCompatible(classLoader, responseSchemasEnabled)) {
            return ConditionOutcome.match("compatible SpringDoc OpenApiCustomizer is available");
        }
        return ConditionOutcome.noMatch("compatible SpringDoc OpenApiCustomizer is not available");
    }

    static boolean isCompatible(ClassLoader classLoader, boolean responseSchemasEnabled) {
        try {
            Class<?> customizerType = ClassUtils.forName(
                    CocoSpringDocOpenApiCustomizerFactoryBean.OPEN_API_CUSTOMIZER_CLASS, classLoader);
            Class<?> openApiType = ClassUtils.forName(
                    CocoSpringDocOpenApiCustomizerFactoryBean.OPEN_API_CLASS, classLoader);
            ClassUtils.forName(CocoSpringDocOpenApiCustomizerFactoryBean.INFO_CLASS, classLoader);
            Method customize = customizerType.getMethod("customise", openApiType);
            if (!customizerType.isInterface() || customize.getReturnType() != Void.TYPE) {
                return false;
            }
            return !responseSchemasEnabled || responseSchemasAvailable(classLoader);
        }
        catch (ReflectiveOperationException | LinkageError ex) {
            return false;
        }
    }

    private static boolean responseSchemasEnabled(ConditionContext context) {
        String configured = context.getEnvironment()
                .getProperty("coco.openapi.springdoc.response-schemas-enabled");
        return configured == null || Boolean.parseBoolean(configured);
    }

    private static boolean responseSchemasAvailable(ClassLoader classLoader) throws ClassNotFoundException {
        ClassUtils.forName(CocoSpringDocOpenApiCustomizerFactoryBean.COMPONENTS_CLASS, classLoader);
        ClassUtils.forName(CocoSpringDocOpenApiCustomizerFactoryBean.SCHEMA_CLASS, classLoader);
        ClassUtils.forName(CocoSpringDocOpenApiCustomizerFactoryBean.OBJECT_SCHEMA_CLASS, classLoader);
        ClassUtils.forName(CocoSpringDocOpenApiCustomizerFactoryBean.BOOLEAN_SCHEMA_CLASS, classLoader);
        ClassUtils.forName(CocoSpringDocOpenApiCustomizerFactoryBean.INTEGER_SCHEMA_CLASS, classLoader);
        ClassUtils.forName(CocoSpringDocOpenApiCustomizerFactoryBean.STRING_SCHEMA_CLASS, classLoader);
        return true;
    }
}
