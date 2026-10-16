package io.github.coco.feature.mybatisplus.datasource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;

/**
 * 动态多数据源运行期注册表接口。
 * <p>
 * 支持在运行期动态注册、替换、注销数据源以及查询当前已注册的数据源与分组拓扑。
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
public interface CocoDataSourceRegistry {

    /**
     * <p>
     * 返回默认主数据源名称。
     * </p>
     * @return 默认主数据源名称
     */
    String getPrimary();

    /**
     * <p>
     * 在运行期动态注册或替换指定名称的数据源（按默认分隔符自动推导分组）。
     * </p>
     * @param name 数据源名称
     * @param dataSource 物理数据源实例
     */
    default void registerDataSource(String name, DataSource dataSource) {
        registerDataSource(name, null, dataSource);
    }

    /**
     * <p>
     * 在运行期动态注册或替换指定名称和显式分组的数据源。
     * </p>
     * @param name 数据源名称
     * @param group 显式分组名称（可为 {@code null}）
     * @param dataSource 物理数据源实例
     */
    void registerDataSource(String name, String group, DataSource dataSource);

    /**
     * <p>
     * 在运行期动态移除指定名称的数据源；主数据源不允许移除。
     * </p>
     * @param name 数据源名称
     * @return {@code true} 表示成功移除已存在的数据源
     */
    boolean unregisterDataSource(String name);

    /**
     * <p>
     * 判断指定名称的物理数据源是否已注册。
     * </p>
     * @param name 数据源名称
     * @return {@code true} 表示已注册
     */
    boolean containsDataSource(String name);

    /**
     * <p>
     * 判断指定名称的数据源分组是否存在且包含可用候选源。
     * </p>
     * @param group 数据源分组名称
     * @return {@code true} 表示分组存在
     */
    boolean containsGroup(String group);

    /**
     * <p>
     * 按名称获取已注册的物理 {@link DataSource}。
     * </p>
     * @param name 数据源名称
     * @return 物理数据源实例
     */
    Optional<DataSource> getDataSource(String name);

    /**
     * <p>
     * 返回所有已注册物理数据源名称的不可变集合快照。
     * </p>
     * @return 数据源名称集合
     */
    Set<String> getDataSourceNames();

    /**
     * <p>
     * 返回所有数据源分组及其候选数据源名称列表的不可变映射快照。
     * </p>
     * @return 分组名称到成员列表的映射
     */
    Map<String, List<String>> getGroupDataSourceNames();

    /**
     * <p>
     * 将请求的路由键（具体数据源名称或分组名称）解析为最终命中的物理数据源名称。
     * </p>
     * @param requestedKey 请求的数据源或分组名称
     * @return 最终命中的物理数据源名称
     */
    String resolveEffectiveDataSourceName(String requestedKey);
}
