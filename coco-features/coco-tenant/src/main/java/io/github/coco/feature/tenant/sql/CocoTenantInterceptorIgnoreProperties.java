package io.github.coco.feature.tenant.sql;

import java.util.LinkedHashSet;
import java.util.Set;

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

    private Set<String> allowedMappedStatements = new LinkedHashSet<>();

    /**
     * <p>
     * 返回是否阻断未进入白名单的租户隔离绕过。
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
     * @param blockUnlisted 是否阻断未授权绕过
     */
    public void setBlockUnlisted(boolean blockUnlisted) {
        this.blockUnlisted = blockUnlisted;
    }

    /**
     * <p>
     * 返回允许跳过租户隔离的完整 MyBatis MappedStatement ID 集合。
     * </p>
     * @return 允许跳过租户隔离的完整 MappedStatement ID 集合
     */
    public Set<String> getAllowedMappedStatements() {
        return this.allowedMappedStatements;
    }

    /**
     * <p>
     * 设置允许跳过租户隔离的完整 MyBatis MappedStatement ID 集合。
     * <p>
     * 每项必须与实际执行的语句 ID 完全相等，例如
     * {@code com.example.AdminMapper.selectShared}。该配置不支持通配符、前缀或包级授权。
     * </p>
     * </p>
     * @param allowedMappedStatements 允许跳过租户隔离的完整 MappedStatement ID 集合
     */
    public void setAllowedMappedStatements(Set<String> allowedMappedStatements) {
        this.allowedMappedStatements = allowedMappedStatements == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(allowedMappedStatements);
    }
}
