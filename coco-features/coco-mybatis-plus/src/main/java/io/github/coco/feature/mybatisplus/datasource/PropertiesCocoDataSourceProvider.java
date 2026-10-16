package io.github.coco.feature.mybatisplus.datasource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

/**
 * 基于 {@link CocoMybatisPlusDataSourceProperties} 声明式配置的数据源提供者。
 * <p>
 * 读取 {@code coco.mybatis-plus.datasource.sources} 下的各数据源配置项，并委派 {@link CocoDataSourceCreator} 创建实例。
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
public final class PropertiesCocoDataSourceProvider implements CocoDataSourceProvider {

    private final CocoMybatisPlusDataSourceProperties properties;

    private final CocoDataSourceCreator creator;

    /**
     * <p>
     * 创建基于配置属性的数据源提供者。
     * </p>
     * @param properties 多数据源配置属性
     * @param creator 物理数据源创建器
     */
    public PropertiesCocoDataSourceProvider(CocoMybatisPlusDataSourceProperties properties,
            CocoDataSourceCreator creator) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.creator = Objects.requireNonNull(creator, "creator must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, DataSource> loadDataSources() {
        Map<String, CocoDataSourceDefinitionProperties> configuredSources = this.properties.getSources();
        if (configuredSources.isEmpty()) {
            return Map.of();
        }
        Map<String, DataSource> result = new LinkedHashMap<>(configuredSources.size());
        try {
            for (Map.Entry<String, CocoDataSourceDefinitionProperties> entry : configuredSources.entrySet()) {
                CocoDataSourceDefinitionProperties sourceProperties = entry.getValue();
                if (sourceProperties == null) {
                    continue;
                }
                CocoDataSourceDefinition definition = sourceProperties.toDefinition(entry.getKey());
                result.put(definition.name(), this.creator.createDataSource(definition));
            }
        }
        catch (RuntimeException ex) {
            for (DataSource created : result.values()) {
                if (created instanceof AutoCloseable closeable) {
                    try {
                        closeable.close();
                    }
                    catch (Exception closeFailure) {
                        ex.addSuppressed(closeFailure);
                    }
                }
            }
            throw ex;
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> loadDataSourceGroups() {
        Map<String, CocoDataSourceDefinitionProperties> configuredSources = this.properties.getSources();
        if (configuredSources.isEmpty()) {
            return Map.of();
        }
        Map<String, String> groups = new LinkedHashMap<>();
        for (Map.Entry<String, CocoDataSourceDefinitionProperties> entry : configuredSources.entrySet()) {
            CocoDataSourceDefinitionProperties sourceProperties = entry.getValue();
            if (sourceProperties != null && StringUtils.hasText(sourceProperties.getGroup())
                    && StringUtils.hasText(entry.getKey())) {
                groups.put(entry.getKey().trim(), sourceProperties.getGroup().trim());
            }
        }
        return Collections.unmodifiableMap(groups);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
