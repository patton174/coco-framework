package io.github.coco.feature.mybatisplus.datasource;

import java.util.List;

/**
 * 数据源分组负载均衡器 SPI。
 * <p>
 * 当目标路由键指向一个数据源分组（例如 {@code slave} 包含 {@code slave_1}、{@code slave_2}）时，
 * 由负载均衡器从候选数据源名称列表中选择本次请求使用的物理数据源名称。
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
public interface CocoDataSourceLoadBalancer {

    /**
     * <p>
     * 从分组候选数据源列表中选择一个目标数据源名称。
     * </p>
     * @param group 数据源分组名称
     * @param candidates 非空的候选数据源名称列表
     * @return 选中的数据源名称
     */
    String choose(String group, List<String> candidates);
}
