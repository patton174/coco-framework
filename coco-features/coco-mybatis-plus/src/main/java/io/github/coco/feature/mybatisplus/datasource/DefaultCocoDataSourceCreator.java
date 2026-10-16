package io.github.coco.feature.mybatisplus.datasource;

import io.github.coco.feature.mybatisplus.CocoMybatisPlusErrorCode;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * 默认物理数据源创建器实现。
 * <p>
 * 基于 Spring Boot {@link DataSourceBuilder} 创建底层数据源，并通过 {@link Binder} 绑定连接池扩展属性。
 * 当底层为 HikariCP 且未显式指定连接池名称时，自动注入带有数据源标识的池名称；HikariCP 缺失时不会触发其类加载。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-mybatis-plus}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class DefaultCocoDataSourceCreator implements CocoDataSourceCreator {

    private static final String HIKARI_DATA_SOURCE_CLASS = "com.zaxxer.hikari.HikariDataSource";

    private final ClassLoader classLoader;

    /**
     * <p>
     * 使用默认类加载器创建数据源创建器。
     * </p>
     */
    public DefaultCocoDataSourceCreator() {
        this(DefaultCocoDataSourceCreator.class.getClassLoader());
    }

    /**
     * <p>
     * 使用指定类加载器创建数据源创建器。
     * </p>
     * @param classLoader 类加载器
     */
    public DefaultCocoDataSourceCreator(ClassLoader classLoader) {
        this.classLoader = classLoader == null ? ClassUtils.getDefaultClassLoader() : classLoader;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataSource createDataSource(CocoDataSourceDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        if (!StringUtils.hasText(definition.url())) {
            throw CocoMybatisPlusErrorCode.INVALID_DATASOURCE_DEFINITION.request();
        }
        DataSourceBuilder<?> builder = DataSourceBuilder.create(this.classLoader);
        if (definition.type() != null) {
            builder.type(definition.type());
        }
        if (StringUtils.hasText(definition.driverClassName())) {
            builder.driverClassName(definition.driverClassName());
        }
        builder.url(definition.url());
        if (definition.username() != null) {
            builder.username(definition.username());
        }
        if (definition.password() != null) {
            builder.password(definition.password());
        }
        DataSource dataSource = builder.build();
        bindCustomProperties(dataSource, definition.properties());
        if (isHikariDataSource(dataSource)) {
            HikariCustomizer.customize(dataSource, definition);
        }
        return dataSource;
    }

    private static void bindCustomProperties(DataSource dataSource, Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) {
            return;
        }
        Binder binder = new Binder(new MapConfigurationPropertySource(properties));
        binder.bind(ConfigurationPropertyName.EMPTY, Bindable.ofInstance(dataSource));
        binder.bind("hikari", Bindable.ofInstance(dataSource));
    }

    private boolean isHikariDataSource(DataSource dataSource) {
        Class<?> hikariType = ClassUtils.isPresent(HIKARI_DATA_SOURCE_CLASS, this.classLoader)
                ? loadHikariType(this.classLoader) : null;
        return hikariType != null && hikariType.isInstance(dataSource);
    }

    private static Class<?> loadHikariType(ClassLoader classLoader) {
        try {
            return ClassUtils.forName(HIKARI_DATA_SOURCE_CLASS, classLoader);
        }
        catch (ClassNotFoundException | LinkageError ex) {
            return null;
        }
    }

    /**
     * HikariCP 专用定制，仅在确认 HikariCP 存在且数据源为其实例时才会被加载。
     */
    private static final class HikariCustomizer {

        private HikariCustomizer() {
        }

        static void customize(DataSource dataSource, CocoDataSourceDefinition definition) {
            com.zaxxer.hikari.HikariDataSource hikari = (com.zaxxer.hikari.HikariDataSource) dataSource;
            if (!hasCustomPoolName(definition.properties()) && !StringUtils.hasText(hikari.getPoolName())) {
                hikari.setPoolName("CocoHikariPool-" + definition.name());
            }
        }

        private static boolean hasCustomPoolName(Map<String, String> properties) {
            if (properties == null || properties.isEmpty()) {
                return false;
            }
            return properties.containsKey("pool-name")
                    || properties.containsKey("poolName")
                    || properties.containsKey("hikari.pool-name")
                    || properties.containsKey("hikari.poolName");
        }
    }
}
