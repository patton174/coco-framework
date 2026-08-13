package io.github.coco.feature.idempotency.jdbc;

import javax.sql.DataSource;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.idempotency.CocoIdempotencyAutoConfiguration;
import io.github.coco.feature.idempotency.CocoIdempotencyFeature;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStore;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC 幂等共享存储自动配置。
 * <p>业务自定义 {@link CocoIdempotencyStore} 优先。本适配器不创建数据源或事务管理器，并且只接受可取得独立
 * {@link DataSource} 的标准 {@link JdbcTemplate} 或唯一数据源。</p>
 *
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration(after = JdbcTemplateAutoConfiguration.class, before = CocoIdempotencyAutoConfiguration.class)
@ConditionalOnClass({ JdbcOperations.class, DataSource.class })
@ConditionalOnCocoFeature(CocoFeature.WEB)
@ConditionalOnProperty(prefix = CocoIdempotencyFeature.PROPERTY_PREFIX, name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = CocoIdempotencyJdbcProperties.PROPERTY_PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CocoIdempotencyJdbcProperties.class)
public class CocoIdempotencyJdbcAutoConfiguration {

    /**
     * 注册 JDBC 幂等存储。
     * @param jdbcOperations 业务可选的 JDBC 操作入口，优先于数据源候选项
     * @param dataSource 业务的唯一数据源候选项
     * @param properties JDBC 适配器配置
     * @return JDBC 幂等存储
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(CocoIdempotencyStore.class)
    public JdbcCocoIdempotencyStore jdbcCocoIdempotencyStore(ObjectProvider<JdbcOperations> jdbcOperations,
            ObjectProvider<DataSource> dataSource, CocoIdempotencyJdbcProperties properties) {
        DataSource resolved = dataSourceFrom(jdbcOperations.getIfUnique());
        if (resolved == null) resolved = dataSource.getIfUnique();
        if (resolved == null) {
            throw new IllegalStateException("coco.idempotency.jdbc.enabled requires a single JdbcOperations backed by JdbcTemplate or a single DataSource");
        }
        return new JdbcCocoIdempotencyStore(resolved, properties);
    }

    private static DataSource dataSourceFrom(JdbcOperations jdbcOperations) {
        return jdbcOperations instanceof JdbcTemplate jdbcTemplate ? jdbcTemplate.getDataSource() : null;
    }
}
