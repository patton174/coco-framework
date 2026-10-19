package io.github.coco.feature.mybatisplus.datasource;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.Ordered;

/**
 * Coco MyBatis-Plus 多数据源动态路由配置属性。
 * <p>
 * 绑定 {@code coco.mybatis-plus.datasource} 命名空间，集中管理主数据源名称、缺失策略、事务冲突策略、
 * 分组负载均衡策略、切面优先级及声明式数据源集合。
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
public class CocoMybatisPlusDataSourceProperties {

    private boolean enabled;

    private String primary = "master";

    private CocoDataSourceMissingPolicy missingPolicy = CocoDataSourceMissingPolicy.FAIL;

    private CocoDataSourceTransactionPolicy transactionPolicy = CocoDataSourceTransactionPolicy.REJECT;

    private CocoDataSourceLoadBalanceStrategy loadBalanceStrategy = CocoDataSourceLoadBalanceStrategy.ROUND_ROBIN;

    private String groupSeparator = "_";

    private int aspectOrder = Ordered.HIGHEST_PRECEDENCE + 100;

    private int maxKeyLength = 128;

    private Map<String, CocoDataSourceDefinitionProperties> sources = new LinkedHashMap<>();

    /**
     * <p>
     * 返回是否启用 Coco 动态多数据源路由。
     * </p>
     * @return {@code true} 表示启用
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用 Coco 动态多数据源路由。
     * </p>
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回默认主数据源名称。
     * </p>
     * @return 默认主数据源名称
     */
    public String getPrimary() {
        return this.primary;
    }

    /**
     * <p>
     * 设置默认主数据源名称。
     * </p>
     * @param primary 默认主数据源名称
     */
    public void setPrimary(String primary) {
        this.primary = (primary == null || primary.isBlank()) ? "master" : primary.trim();
    }

    /**
     * <p>
     * 返回请求的数据源未注册时的处理策略。
     * </p>
     * @return 数据源缺失策略
     */
    public CocoDataSourceMissingPolicy getMissingPolicy() {
        return this.missingPolicy;
    }

    /**
     * <p>
     * 设置请求的数据源未注册时的处理策略。
     * </p>
     * @param missingPolicy 数据源缺失策略
     */
    public void setMissingPolicy(CocoDataSourceMissingPolicy missingPolicy) {
        this.missingPolicy = missingPolicy == null ? CocoDataSourceMissingPolicy.FAIL : missingPolicy;
    }

    /**
     * <p>
     * 返回活动事务内切换数据源时的保护策略。
     * </p>
     * @return 事务冲突策略
     */
    public CocoDataSourceTransactionPolicy getTransactionPolicy() {
        return this.transactionPolicy;
    }

    /**
     * <p>
     * 设置活动事务内切换数据源时的保护策略。
     * </p>
     * @param transactionPolicy 事务冲突策略
     */
    public void setTransactionPolicy(CocoDataSourceTransactionPolicy transactionPolicy) {
        this.transactionPolicy = transactionPolicy == null ? CocoDataSourceTransactionPolicy.REJECT : transactionPolicy;
    }

    /**
     * <p>
     * 返回数据源分组负载均衡策略。
     * </p>
     * @return 分组负载均衡策略
     */
    public CocoDataSourceLoadBalanceStrategy getLoadBalanceStrategy() {
        return this.loadBalanceStrategy;
    }

    /**
     * <p>
     * 设置数据源分组负载均衡策略。
     * </p>
     * @param loadBalanceStrategy 分组负载均衡策略
     */
    public void setLoadBalanceStrategy(CocoDataSourceLoadBalanceStrategy loadBalanceStrategy) {
        this.loadBalanceStrategy = loadBalanceStrategy == null
                ? CocoDataSourceLoadBalanceStrategy.ROUND_ROBIN
                : loadBalanceStrategy;
    }

    /**
     * <p>
     * 返回自动分组推导的名称分隔符（例如 {@code "_"} 会将 {@code slave_1} 自动归入 {@code slave} 组）。
     * </p>
     * @return 分组名称分隔符
     */
    public String getGroupSeparator() {
        return this.groupSeparator;
    }

    /**
     * <p>
     * 设置自动分组推导的名称分隔符；设为空字符串可禁用基于前缀的自动分组。
     * </p>
     * @param groupSeparator 分组名称分隔符
     */
    public void setGroupSeparator(String groupSeparator) {
        this.groupSeparator = groupSeparator == null ? "" : groupSeparator;
    }

    /**
     * <p>
     * 返回 {@link CocoDataSource} 切面的执行顺序（默认高于 {@code @Transactional}）。
     * </p>
     * @return 切面顺序值
     */
    public int getAspectOrder() {
        return this.aspectOrder;
    }

    /**
     * <p>
     * 设置 {@link CocoDataSource} 切面的执行顺序。
     * </p>
     * @param aspectOrder 切面顺序值
     */
    public void setAspectOrder(int aspectOrder) {
        this.aspectOrder = aspectOrder;
    }

    /**
     * <p>
     * 返回解析后的数据源键最大允许长度。
     * </p>
     * @return 数据源键最大长度
     */
    public int getMaxKeyLength() {
        return this.maxKeyLength;
    }

    /**
     * <p>
     * 设置解析后的数据源键最大允许长度。
     * </p>
     * @param maxKeyLength 数据源键最大长度
     */
    public void setMaxKeyLength(int maxKeyLength) {
        this.maxKeyLength = maxKeyLength;
    }

    /**
     * <p>
     * 返回声明式数据源定义集合副本。
     * </p>
     * @return 数据源名称到配置属性的映射
     */
    public Map<String, CocoDataSourceDefinitionProperties> getSources() {
        return new LinkedHashMap<>(this.sources);
    }

    /**
     * <p>
     * 设置声明式数据源定义集合。
     * </p>
     * @param sources 数据源名称到配置属性的映射
     */
    public void setSources(Map<String, CocoDataSourceDefinitionProperties> sources) {
        this.sources = sources == null ? new LinkedHashMap<>() : new LinkedHashMap<>(sources);
    }
}
