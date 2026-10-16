package io.github.coco.feature.mybatisplus.datasource;

/**
 * 数据源分组负载均衡策略。
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
public enum CocoDataSourceLoadBalanceStrategy {

    /**
     * 按分组轮询选择数据源。
     */
    ROUND_ROBIN,

    /**
     * 在分组候选数据源中随机选择。
     */
    RANDOM
}
