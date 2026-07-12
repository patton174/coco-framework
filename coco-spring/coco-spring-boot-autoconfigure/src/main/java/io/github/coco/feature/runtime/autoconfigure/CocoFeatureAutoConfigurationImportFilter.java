package io.github.coco.feature.runtime.autoconfigure;

import java.util.Set;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.runtime.condition.CocoRuntimeFeatureResolver;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

/**
 * Coco 功能自动配置导入过滤器。
 * <p>
 * 根据 Coco 运行期功能启用计划过滤被禁用能力对应的第三方自动配置，避免单 starter 模式下未启用能力仍影响业务应用启动。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-spring-boot-autoconfigure}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoFeatureAutoConfigurationImportFilter
        implements AutoConfigurationImportFilter, EnvironmentAware, BeanClassLoaderAware {

    private static final Set<String> MYBATIS_PLUS_AUTO_CONFIGURATIONS = Set.of(
            "com.baomidou.mybatisplus.autoconfigure.MybatisPlusInnerInterceptorAutoConfiguration",
            "com.baomidou.mybatisplus.autoconfigure.IdentifierGeneratorAutoConfiguration",
            "com.baomidou.mybatisplus.autoconfigure.MybatisPlusLanguageDriverAutoConfiguration",
            "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
            "com.baomidou.mybatisplus.autoconfigure.DdlAutoConfiguration");

    private static final Set<String> DATA_SOURCE_AUTO_CONFIGURATIONS = Set.of(
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.JndiDataSourceAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.XADataSourceAutoConfiguration");

    private static final Set<String> DATA_SOURCE_PROPERTY_NAMES = Set.of(
            "spring.datasource.url",
            "spring.datasource.jdbc-url",
            "spring.datasource.driver-class-name",
            "spring.datasource.jndi-name",
            "spring.datasource.type");

    private static final Set<String> EMBEDDED_DATABASE_DRIVER_NAMES = Set.of(
            "org.h2.Driver",
            "org.hsqldb.jdbcDriver",
            "org.apache.derby.jdbc.EmbeddedDriver");

    private Environment environment;

    private ClassLoader beanClassLoader;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
        boolean[] matches = new boolean[autoConfigurationClasses.length];
        boolean mybatisPlusEnabled = new CocoRuntimeFeatureResolver()
                .resolve(this.environment, this.beanClassLoader)
                .isEnabled(CocoFeature.MYBATIS_PLUS);
        boolean shouldFilterDataSource = !mybatisPlusEnabled && !hasDataSourceConfiguration()
                && !hasEmbeddedDatabaseDriver();
        for (int index = 0; index < autoConfigurationClasses.length; index++) {
            String autoConfigurationClass = autoConfigurationClasses[index];
            matches[index] = shouldImport(autoConfigurationClass, mybatisPlusEnabled, shouldFilterDataSource);
        }
        return matches;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }

    private boolean shouldImport(String autoConfigurationClass, boolean mybatisPlusEnabled,
            boolean shouldFilterDataSource) {
        if (autoConfigurationClass == null) {
            return true;
        }
        if (!mybatisPlusEnabled && MYBATIS_PLUS_AUTO_CONFIGURATIONS.contains(autoConfigurationClass)) {
            return false;
        }
        return !shouldFilterDataSource || !DATA_SOURCE_AUTO_CONFIGURATIONS.contains(autoConfigurationClass);
    }

    private boolean hasDataSourceConfiguration() {
        if (this.environment == null) {
            return false;
        }
        return DATA_SOURCE_PROPERTY_NAMES.stream().anyMatch(this.environment::containsProperty);
    }

    private boolean hasEmbeddedDatabaseDriver() {
        ClassLoader classLoader = this.beanClassLoader == null
                ? ClassUtils.getDefaultClassLoader()
                : this.beanClassLoader;
        return EMBEDDED_DATABASE_DRIVER_NAMES.stream()
                .anyMatch(driverName -> ClassUtils.isPresent(driverName, classLoader));
    }
}
