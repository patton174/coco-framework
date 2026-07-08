package io.github.coco.feature.mybatisplus.sqlguard;

/**
 * Coco MyBatis-Plus SQL 防护配置属性。
 * <p>
 * 控制框架是否注册 MyBatis-Plus 官方 SQL 防护拦截器。默认关闭，避免在业务未明确启用时改变既有 SQL 行为。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-mybatis-plus}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public class CocoMybatisPlusSqlGuardProperties {

    private boolean blockAttackEnabled;

    private boolean illegalSqlEnabled;

    /**
     * <p>
     * 返回是否启用全表更新和全表删除防护。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isBlockAttackEnabled() {
        return this.blockAttackEnabled;
    }

    /**
     * <p>
     * 设置是否启用全表更新和全表删除防护。
     * </p>
     * @param blockAttackEnabled 是否启用全表更新和全表删除防护
     */
    public void setBlockAttackEnabled(boolean blockAttackEnabled) {
        this.blockAttackEnabled = blockAttackEnabled;
    }

    /**
     * <p>
     * 返回是否启用非法 SQL 防护。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isIllegalSqlEnabled() {
        return this.illegalSqlEnabled;
    }

    /**
     * <p>
     * 设置是否启用非法 SQL 防护。
     * </p>
     * @param illegalSqlEnabled 是否启用非法 SQL 防护
     */
    public void setIllegalSqlEnabled(boolean illegalSqlEnabled) {
        this.illegalSqlEnabled = illegalSqlEnabled;
    }
}
