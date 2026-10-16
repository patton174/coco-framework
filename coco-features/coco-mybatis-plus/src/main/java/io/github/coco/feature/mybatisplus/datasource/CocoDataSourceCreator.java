package io.github.coco.feature.mybatisplus.datasource;

import javax.sql.DataSource;

/**
 * 物理数据源创建器 SPI。
 * <p>
 * 负责根据 {@link CocoDataSourceDefinition} 构造并初始化底层 {@link DataSource} 实例（如 HikariCP、Druid 等）。
 * 业务应用或扩展模块可注册自定义 {@code CocoDataSourceCreator} Bean 替换默认创建逻辑。
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
public interface CocoDataSourceCreator {

    /**
     * <p>
     * 根据数据源定义创建物理 {@link DataSource}。
     * </p>
     * @param definition 数据源定义
     * @return 物理数据源实例
     */
    DataSource createDataSource(CocoDataSourceDefinition definition);
}
