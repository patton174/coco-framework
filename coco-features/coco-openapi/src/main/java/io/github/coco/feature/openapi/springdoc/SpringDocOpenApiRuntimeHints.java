package io.github.coco.feature.openapi.springdoc;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.util.ClassUtils;

/**
 * SpringDoc 反射和动态代理运行时提示。
 * <p>
 * SpringDoc 是可选依赖，因此仅在兼容 API 位于 AOT 构建 classpath 时注册提示。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
public final class SpringDocOpenApiRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        if (!isCompatibleClasspath(classLoader)) {
            return;
        }
        hints.proxies().registerJdkProxy(TypeReference.of(
                CocoSpringDocOpenApiCustomizerFactoryBean.OPEN_API_CUSTOMIZER_CLASS));
        registerType(hints, CocoSpringDocOpenApiCustomizerFactoryBean.OPEN_API_CLASS,
                MemberCategory.INVOKE_PUBLIC_METHODS);
        registerType(hints, CocoSpringDocOpenApiCustomizerFactoryBean.INFO_CLASS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS);
        registerType(hints, CocoSpringDocOpenApiCustomizerFactoryBean.COMPONENTS_CLASS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS);
        registerType(hints, CocoSpringDocOpenApiCustomizerFactoryBean.SCHEMA_CLASS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS);
        registerType(hints, CocoSpringDocOpenApiCustomizerFactoryBean.OBJECT_SCHEMA_CLASS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS);
        registerType(hints, CocoSpringDocOpenApiCustomizerFactoryBean.BOOLEAN_SCHEMA_CLASS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS);
        registerType(hints, CocoSpringDocOpenApiCustomizerFactoryBean.INTEGER_SCHEMA_CLASS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS);
        registerType(hints, CocoSpringDocOpenApiCustomizerFactoryBean.STRING_SCHEMA_CLASS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS);
    }

    private static boolean isCompatibleClasspath(ClassLoader classLoader) {
        return ClassUtils.isPresent(CocoSpringDocOpenApiCustomizerFactoryBean.OPEN_API_CUSTOMIZER_CLASS, classLoader)
                && ClassUtils.isPresent(CocoSpringDocOpenApiCustomizerFactoryBean.OPEN_API_CLASS, classLoader)
                && ClassUtils.isPresent(CocoSpringDocOpenApiCustomizerFactoryBean.INFO_CLASS, classLoader)
                && ClassUtils.isPresent(CocoSpringDocOpenApiCustomizerFactoryBean.COMPONENTS_CLASS, classLoader)
                && ClassUtils.isPresent(CocoSpringDocOpenApiCustomizerFactoryBean.SCHEMA_CLASS, classLoader)
                && ClassUtils.isPresent(CocoSpringDocOpenApiCustomizerFactoryBean.OBJECT_SCHEMA_CLASS, classLoader)
                && ClassUtils.isPresent(CocoSpringDocOpenApiCustomizerFactoryBean.BOOLEAN_SCHEMA_CLASS, classLoader)
                && ClassUtils.isPresent(CocoSpringDocOpenApiCustomizerFactoryBean.INTEGER_SCHEMA_CLASS, classLoader)
                && ClassUtils.isPresent(CocoSpringDocOpenApiCustomizerFactoryBean.STRING_SCHEMA_CLASS, classLoader);
    }

    private static void registerType(RuntimeHints hints, String className, MemberCategory... memberCategories) {
        hints.reflection().registerType(TypeReference.of(className), memberCategories);
    }
}
