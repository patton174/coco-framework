package io.github.coco.feature.datapermission.sql;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Coco 数据权限 SQL 接入配置。
 * <p>
 * 描述数据权限模块是否接入 SQL 拦截、缺省处理策略以及业务资源到数据表的基础映射。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public class CocoDataPermissionSqlProperties {

    private boolean enabled;

    private CocoDataPermissionMissingContextPolicy missingContextPolicy =
            CocoDataPermissionMissingContextPolicy.THROW;

    private CocoDataPermissionMissingRulePolicy missingRulePolicy =
            CocoDataPermissionMissingRulePolicy.DENY;

    private Map<String, CocoDataPermissionSqlResourceProperties> resources = new LinkedHashMap<>();

    /**
     * <p>
     * 判断是否启用数据权限 SQL 拦截。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用数据权限 SQL 拦截。
     * </p>
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回缺少数据权限上下文时的处理策略。
     * </p>
     * @return 缺少上下文策略
     */
    public CocoDataPermissionMissingContextPolicy getMissingContextPolicy() {
        return this.missingContextPolicy;
    }

    /**
     * <p>
     * 设置缺少数据权限上下文时的处理策略。
     * </p>
     * @param missingContextPolicy 缺少上下文策略
     */
    public void setMissingContextPolicy(CocoDataPermissionMissingContextPolicy missingContextPolicy) {
        this.missingContextPolicy = missingContextPolicy == null
                ? CocoDataPermissionMissingContextPolicy.THROW
                : missingContextPolicy;
    }

    /**
     * <p>
     * 返回缺少资源规则时的处理策略。
     * </p>
     * @return 缺少规则策略
     */
    public CocoDataPermissionMissingRulePolicy getMissingRulePolicy() {
        return this.missingRulePolicy;
    }

    /**
     * <p>
     * 设置缺少资源规则时的处理策略。
     * </p>
     * @param missingRulePolicy 缺少规则策略
     */
    public void setMissingRulePolicy(CocoDataPermissionMissingRulePolicy missingRulePolicy) {
        this.missingRulePolicy = missingRulePolicy == null
                ? CocoDataPermissionMissingRulePolicy.DENY
                : missingRulePolicy;
    }

    /**
     * <p>
     * 返回业务资源到表的映射配置。
     * </p>
     * @return 业务资源映射配置
     */
    public Map<String, CocoDataPermissionSqlResourceProperties> getResources() {
        return this.resources;
    }

    /**
     * <p>
     * 设置业务资源到表的映射配置。
     * </p>
     * @param resources 业务资源映射配置
     */
    public void setResources(Map<String, CocoDataPermissionSqlResourceProperties> resources) {
        this.resources = resources == null ? new LinkedHashMap<>() : new LinkedHashMap<>(resources);
    }

    /**
     * <p>
     * 根据资源标识返回资源 SQL 配置。
     * </p>
     * @param resource 资源标识
     * @return 资源 SQL 配置；不存在时返回空配置
     */
    public CocoDataPermissionSqlResourceProperties resource(String resource) {
        CocoDataPermissionSqlResourceProperties properties = this.resources.get(resource);
        return properties == null ? new CocoDataPermissionSqlResourceProperties() : properties;
    }
}
