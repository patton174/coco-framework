package io.github.coco.feature.mybatisplus.datasource;

import java.util.Map;
import javax.sql.DataSource;
import org.springframework.core.Ordered;

/**
 * 动态多数据源提供者 SPI。
 * <p>
 * 用于在启动阶段向 {@link CocoRoutingDataSource} 提供初始数据源集合及其显式分组映射。
 * 业务模块可通过注册多个 {@code CocoDataSourceProvider} Bean 从配置文件、配置中心或元数据库动态加载数据源。
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
@FunctionalInterface
public interface CocoDataSourceProvider extends Ordered {

    /**
     * <p>
     * 加载并返回数据源名称到物理 {@link DataSource} 的映射。
     * </p>
     * @return 数据源映射（键为数据源名称，值为数据源实例）
     */
    Map<String, DataSource> loadDataSources();

    /**
     * <p>
     * 返回数据源名称到显式分组名称的映射。
     * </p>
     * @return 数据源显式分组映射（键为数据源名称，值为分组名称）
     */
    default Map<String, String> loadDataSourceGroups() {
        return Map.of();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default int getOrder() {
        return 0;
    }
}
