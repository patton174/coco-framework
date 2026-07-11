package io.github.coco.mybatisplus.sqlguard;

/**
 * Coco MyBatis-Plus SQL 防护配置属性。
 * <p>
 * 控制框架是否注册 MyBatis-Plus 官方 SQL 防护拦截器。默认关闭，避免在业务未明确启用时改变既有 SQL 行为。
 * 生产环境建议先使用真实 SQL 回放或预发布验证，再按需启用全表更新/删除防护和非法 SQL 防护。
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
public class CocoMybatisPlusSqlGuardProperties {

    private boolean blockAttackEnabled;

    private boolean illegalSqlEnabled;

    /**
     * <p>
     * 返回是否启用全表更新和全表删除防护。
     * 生产环境建议启用，用于拦截无有效条件或恒真条件的批量更新、删除语句。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isBlockAttackEnabled() {
        return this.blockAttackEnabled;
    }

    /**
     * <p>
     * 设置是否启用全表更新和全表删除防护。
     * 生产环境建议在确认批量维护 SQL 已显式豁免或改写后设置为 {@code true}。
     * </p>
     * @param blockAttackEnabled 是否启用全表更新和全表删除防护
     */
    public void setBlockAttackEnabled(boolean blockAttackEnabled) {
        this.blockAttackEnabled = blockAttackEnabled;
    }

    /**
     * <p>
     * 返回是否启用非法 SQL 防护。
     * 该拦截器规则较严格，生产环境启用前应先验证索引、JOIN、OR、函数条件等正常 SQL。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isIllegalSqlEnabled() {
        return this.illegalSqlEnabled;
    }

    /**
     * <p>
     * 设置是否启用非法 SQL 防护。
     * 生产环境建议在完成 SQL 兼容性验证后设置为 {@code true}。
     * </p>
     * @param illegalSqlEnabled 是否启用非法 SQL 防护
     */
    public void setIllegalSqlEnabled(boolean illegalSqlEnabled) {
        this.illegalSqlEnabled = illegalSqlEnabled;
    }
}
