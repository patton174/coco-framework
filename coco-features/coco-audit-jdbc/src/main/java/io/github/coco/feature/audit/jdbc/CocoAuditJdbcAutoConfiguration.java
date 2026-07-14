package io.github.coco.feature.audit.jdbc;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.audit.CocoAuditAutoConfiguration;
import io.github.coco.feature.audit.core.CocoAuditRecorder;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * Coco JDBC 审计记录器自动配置。
 * <p>
 * 仅在业务显式启用 {@code coco.audit.jdbc.enabled} 且提供唯一 {@link JdbcOperations} 候选时注册。业务自定义
 * {@link CocoAuditRecorder} 会使该配置回退；该模块不推断用户、租户或审计表结构。开启 schema 初始化时，业务提供的
 * {@link CocoAuditSchemaInitializer} 负责执行目标数据库方言的 DDL。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration(after = JdbcTemplateAutoConfiguration.class, before = CocoAuditAutoConfiguration.class)
@ConditionalOnCocoFeature(CocoFeature.AUDIT)
@EnableConfigurationProperties(CocoAuditJdbcProperties.class)
@ConditionalOnClass(JdbcOperations.class)
@ConditionalOnSingleCandidate(JdbcOperations.class)
@ConditionalOnProperty(prefix = "coco.audit.jdbc", name = "enabled", havingValue = "true")
public class CocoAuditJdbcAutoConfiguration {

    /**
     * 注册 JDBC 审计模块消息资源。
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoAuditJdbcMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoAuditJdbcMessageBundleRegistrar() {
        return registry -> registry.add("coco-audit-jdbc-messages");
    }

    /**
     * 创建 JDBC 审计记录器。
     * @param jdbcOperations 业务项目提供的单候选 JDBC 操作入口
     * @param properties JDBC 审计配置
     * @return JDBC 审计记录器
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(CocoAuditRecorder.class)
    public JdbcCocoAuditRecorder jdbcCocoAuditRecorder(JdbcOperations jdbcOperations,
            CocoAuditJdbcProperties properties, org.springframework.beans.factory.ObjectProvider<CocoAuditSchemaInitializer>
                    schemaInitializer) {
        return new JdbcCocoAuditRecorder(jdbcOperations, properties, schemaInitializer.getIfUnique());
    }
}
