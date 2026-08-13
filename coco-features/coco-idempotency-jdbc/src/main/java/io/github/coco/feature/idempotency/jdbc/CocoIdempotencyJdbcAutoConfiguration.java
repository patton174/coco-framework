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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;

/**
 * JDBC 幂等共享存储自动配置。
 * <p>业务自定义 {@link CocoIdempotencyStore} 优先。本适配器不创建数据源或事务管理器。业务可以提供命名为
 * {@value #DATA_SOURCE_BEAN_NAME} 的专用数据源；未提供时回退到唯一普通数据源。两种数据源都必须能返回绝对独立的连接，
 * 不能是 Spring 事务感知代理。</p>
 *
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration(after = JdbcTemplateAutoConfiguration.class, before = CocoIdempotencyAutoConfiguration.class)
@ConditionalOnClass(DataSource.class)
@ConditionalOnCocoFeature(CocoFeature.WEB)
@ConditionalOnProperty(prefix = CocoIdempotencyFeature.PROPERTY_PREFIX, name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = CocoIdempotencyJdbcProperties.PROPERTY_PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CocoIdempotencyJdbcProperties.class)
public class CocoIdempotencyJdbcAutoConfiguration {

    /** 专用幂等数据源 Bean 名称。 */
    public static final String DATA_SOURCE_BEAN_NAME = "cocoIdempotencyDataSource";

    /**
     * 注册 JDBC 幂等存储。
     * @param dedicatedDataSource 业务可选的专用幂等数据源
     * @param dataSources 业务的数据源候选项
     * @param properties JDBC 适配器配置
     * @return JDBC 幂等存储
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(CocoIdempotencyStore.class)
    public JdbcCocoIdempotencyStore jdbcCocoIdempotencyStore(
            @Qualifier(DATA_SOURCE_BEAN_NAME) ObjectProvider<DataSource> dedicatedDataSource,
            ObjectProvider<DataSource> dataSources, CocoIdempotencyJdbcProperties properties) {
        DataSource resolved = dedicatedDataSource.getIfAvailable();
        if (resolved == null) resolved = dataSources.getIfUnique();
        if (resolved == null) {
            throw new IllegalStateException("coco.idempotency.jdbc.enabled requires a DataSource named "
                    + DATA_SOURCE_BEAN_NAME + " or a single DataSource");
        }
        return new JdbcCocoIdempotencyStore(resolved, properties);
    }
}
