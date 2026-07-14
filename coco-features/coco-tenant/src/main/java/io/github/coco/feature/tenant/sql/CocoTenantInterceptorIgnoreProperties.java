package io.github.coco.feature.tenant.sql;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * Coco 租户拦截器忽略治理配置属性。
 * <p>
 * 用于治理 MyBatis-Plus {@code @InterceptorIgnore(tenantLine = true)} 和线程级忽略策略，避免租户 SQL 隔离被静默绕过。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-tenant}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public class CocoTenantInterceptorIgnoreProperties {

    private boolean blockUnlisted = true;

    private Set<String> exactMappedStatements = new LinkedHashSet<>();

    private Set<String> allowedMappedStatements = new LinkedHashSet<>();

    private boolean strictMode;

    public CocoTenantInterceptorIgnoreProperties() {
    }

    public CocoTenantInterceptorIgnoreProperties(CocoTenantInterceptorIgnoreProperties source) {
        CocoTenantInterceptorIgnoreProperties checkedSource = source == null
                ? new CocoTenantInterceptorIgnoreProperties() : source;
        this.blockUnlisted = checkedSource.blockUnlisted;
        this.exactMappedStatements = new LinkedHashSet<>(checkedSource.exactMappedStatements);
        this.allowedMappedStatements = new LinkedHashSet<>(checkedSource.allowedMappedStatements);
        this.strictMode = checkedSource.strictMode;
    }

    /**
     * <p>
     * 返回拦截器忽略配置的深复制快照。
     * </p>
     * @return 拦截器忽略配置快照
     */
    public CocoTenantInterceptorIgnoreProperties snapshot() {
        return new CocoTenantInterceptorIgnoreProperties(this);
    }

    /**
     * <p>
     * 返回是否阻断未进入白名单的租户隔离绕过。
     * </p>
     * <p>
     * 启用严格模式时，无论该值如何配置，未授权旁路都会被阻断。
     * </p>
     * @return 阻断未授权绕过时返回 {@code true}
     */
    public boolean isBlockUnlisted() {
        return this.blockUnlisted;
    }

    /**
     * <p>
     * 设置是否阻断未进入白名单的租户隔离绕过。
     * </p>
     * <p>
     * 启用严格模式时，无论该值如何配置，未授权旁路都会被阻断。
     * </p>
     * @param blockUnlisted 是否阻断未授权绕过
     */
    public void setBlockUnlisted(boolean blockUnlisted) {
        this.blockUnlisted = blockUnlisted;
    }

    /**
     * <p>
     * 返回允许跳过租户隔离的完整 MyBatis MappedStatement ID 精确白名单。
     * </p>
     * @return 允许跳过租户隔离的完整 MappedStatement ID 精确白名单
     */
    @SuppressWarnings("EI_EXPOSE_REP")
    public Set<String> getExactMappedStatements() {
        return this.exactMappedStatements;
    }

    /**
     * <p>
     * 设置允许跳过租户隔离的完整 MyBatis MappedStatement ID 精确白名单。
     * </p>
     * <p>
     * 每项必须与实际执行的语句 ID 完全相等，例如
     * {@code com.example.AdminMapper.selectShared}。该配置不支持通配符、前缀或包级授权。
     * </p>
     * @param exactMappedStatements 允许跳过租户隔离的完整 MappedStatement ID 精确白名单
     */
    public void setExactMappedStatements(Set<String> exactMappedStatements) {
        this.exactMappedStatements = exactMappedStatements == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(exactMappedStatements);
    }

    /**
     * <p>
     * 返回兼容旧版本的 MyBatis MappedStatement ID 模式白名单。
     * </p>
     * <p>
     * 该集合继续使用 Spring 简单通配符语义。新配置应使用 {@link #getExactMappedStatements()}，
     * 以完整语句 ID 进行精确授权。
     * </p>
     * @return 兼容旧版本的 MappedStatement ID 模式白名单
     * @deprecated 使用 {@link #getExactMappedStatements()}；旧模式仅用于迁移兼容
     */
    @Deprecated(since = "1.0.0", forRemoval = false)
    @DeprecatedConfigurationProperty(
            replacement = "coco.tenant.sql.interceptor-ignore.exact-mapped-statements",
            reason = "通配模式授权范围过宽，请迁移到完整 MappedStatement ID 精确白名单。")
    @SuppressWarnings("EI_EXPOSE_REP")
    public Set<String> getAllowedMappedStatements() {
        return this.allowedMappedStatements;
    }

    /**
     * <p>
     * 设置兼容旧版本的 MyBatis MappedStatement ID 模式白名单。
     * </p>
     * @param allowedMappedStatements 兼容旧版本的 MappedStatement ID 模式白名单
     * @deprecated 使用 {@link #setExactMappedStatements(Set)}；旧模式仅用于迁移兼容
     */
    @Deprecated(since = "1.0.0", forRemoval = false)
    public void setAllowedMappedStatements(Set<String> allowedMappedStatements) {
        this.allowedMappedStatements = allowedMappedStatements == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(allowedMappedStatements);
    }

    /**
     * <p>
     * 返回是否启用严格模式。
     * </p>
     * <p>
     * 严格模式会在启动时拒绝已废弃的模式白名单，强制阻断未授权旁路，
     * 并且运行时只接受完整语句 ID 精确白名单。
     * </p>
     * @return 启用严格模式时返回 {@code true}
     */
    public boolean isStrictMode() {
        return this.strictMode;
    }

    /**
     * <p>
     * 设置是否启用严格模式。
     * </p>
     * @param strictMode 是否启用严格模式
     */
    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }
}
