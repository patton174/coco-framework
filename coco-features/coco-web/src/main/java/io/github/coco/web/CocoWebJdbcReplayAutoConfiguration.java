package io.github.coco.web;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.spring.boot.autoconfigure.feature.condition.ConditionalOnCocoFeature;
import io.github.coco.web.replay.CocoReplayStore;
import io.github.coco.web.replay.JdbcCocoReplayStore;
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
 * Coco Web JDBC 防重放存储自动配置。
 * <p>
 * 在 Boot 完成 JDBC 模板装配后，使用业务项目提供的单候选 {@link JdbcOperations} 注册共享防重放存储，
 * 并在 Web 主自动配置创建防重放过滤器之前完成 Store 注册。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration(after = JdbcTemplateAutoConfiguration.class, before = CocoWebAutoConfiguration.class)
@ConditionalOnCocoFeature(CocoFeature.WEB)
@EnableConfigurationProperties(CocoWebProperties.class)
@ConditionalOnClass(JdbcOperations.class)
@ConditionalOnSingleCandidate(JdbcOperations.class)
@ConditionalOnProperty(prefix = "coco.web.replay", name = "store-type", havingValue = "jdbc")
public class CocoWebJdbcReplayAutoConfiguration {

    /**
     * <p>
     * 创建 JDBC 防重放共享存储。
     * </p>
     * @param properties Coco Web 配置属性
     * @param jdbcOperations 业务项目提供的单候选 JDBC 操作入口
     * @return JDBC 防重放共享存储
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(CocoReplayStore.class)
    @ConditionalOnProperty(prefix = "coco.web.replay", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public JdbcCocoReplayStore jdbcCocoReplayStore(CocoWebProperties properties, JdbcOperations jdbcOperations) {
        return new JdbcCocoReplayStore(jdbcOperations, properties.getReplay());
    }
}
