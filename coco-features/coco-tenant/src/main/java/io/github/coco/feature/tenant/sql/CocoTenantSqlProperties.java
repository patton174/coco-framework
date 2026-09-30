package io.github.coco.feature.tenant.sql;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Coco 租户 SQL 隔离配置属性。
 * <p>
 * 描述租户 SQL 拦截器是否启用、租户字段名和需要跳过租户条件的表。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-tenant}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public class CocoTenantSqlProperties {

    private static final String DEFAULT_TENANT_ID_COLUMN = "tenant_id";

    private boolean enabled = true;

    private String tenantIdColumn = DEFAULT_TENANT_ID_COLUMN;

    private Set<String> ignoreTables = new LinkedHashSet<>();

    private boolean failOnMissingContext = true;

    private CocoTenantInterceptorIgnoreProperties interceptorIgnore = new CocoTenantInterceptorIgnoreProperties();

    /**
     * <p>
     * 返回是否启用租户 SQL 隔离。
     * </p>
     * @return 启用租户 SQL 隔离时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用租户 SQL 隔离。
     * </p>
     * @param enabled 是否启用租户 SQL 隔离
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回租户字段名。
     * </p>
     * @return 租户字段名
     */
    public String getTenantIdColumn() {
        return this.tenantIdColumn;
    }

    /**
     * <p>
     * 设置租户字段名；为空时恢复默认 {@code tenant_id}。
     * </p>
     * @param tenantIdColumn 租户字段名
     */
    public void setTenantIdColumn(String tenantIdColumn) {
        this.tenantIdColumn = tenantIdColumn == null || tenantIdColumn.isBlank()
                ? DEFAULT_TENANT_ID_COLUMN
                : tenantIdColumn.trim();
    }

    /**
     * <p>
     * 返回无需追加租户条件的表名集合。
     * </p>
     * @return 无需追加租户条件的表名集合
     */
    public Set<String> getIgnoreTables() {
        return this.ignoreTables;
    }

    /**
     * <p>
     * 设置无需追加租户条件的表名集合。
     * </p>
     * @param ignoreTables 无需追加租户条件的表名集合
     */
    public void setIgnoreTables(Set<String> ignoreTables) {
        this.ignoreTables = ignoreTables == null ? new LinkedHashSet<>() : new LinkedHashSet<>(ignoreTables);
    }

    /**
     * <p>
     * 返回缺少租户上下文时是否直接抛出异常。
     * </p>
     * @return 缺少租户上下文时抛出异常返回 {@code true}
     */
    public boolean isFailOnMissingContext() {
        return this.failOnMissingContext;
    }

    /**
     * <p>
     * 设置缺少租户上下文时是否直接抛出异常。
     * </p>
     * @param failOnMissingContext 缺少租户上下文时是否直接抛出异常
     */
    public void setFailOnMissingContext(boolean failOnMissingContext) {
        this.failOnMissingContext = failOnMissingContext;
    }

    /**
     * <p>
     * 返回 MyBatis-Plus 拦截器忽略治理配置。
     * </p>
     * @return 拦截器忽略治理配置
     */
    public CocoTenantInterceptorIgnoreProperties getInterceptorIgnore() {
        return this.interceptorIgnore;
    }

    /**
     * <p>
     * 设置 MyBatis-Plus 拦截器忽略治理配置。
     * </p>
     * @param interceptorIgnore 拦截器忽略治理配置
     */
    public void setInterceptorIgnore(CocoTenantInterceptorIgnoreProperties interceptorIgnore) {
        this.interceptorIgnore = interceptorIgnore == null
                ? new CocoTenantInterceptorIgnoreProperties()
                : interceptorIgnore;
    }
}
