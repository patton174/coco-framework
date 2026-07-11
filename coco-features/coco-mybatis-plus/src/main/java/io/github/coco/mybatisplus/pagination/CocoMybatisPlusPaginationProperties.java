package io.github.coco.mybatisplus.pagination;

/**
 * Coco MyBatis-Plus 分页拦截器配置属性。
 * <p>
 * 控制框架默认注册的 MyBatis-Plus 分页内置拦截器，为后续注解分页、租户和数据权限 SQL 扩展提供统一基础。
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
public class CocoMybatisPlusPaginationProperties {

    private boolean enabled = true;

    private String dbType;

    private boolean overflow;

    private Long maxLimit;

    private boolean optimizeJoin = true;

    /**
     * <p>
     * 返回是否启用默认分页内置拦截器。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用默认分页内置拦截器。
     * </p>
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回数据库类型。
     * </p>
     * <p>
     * 为空时由 MyBatis-Plus 根据当前数据源自动推断；可配置为 {@code mysql}、{@code h2}、
     * {@code postgre-sql} 或 MyBatis-Plus {@code DbType} 枚举名称。
     * </p>
     * @return 数据库类型
     */
    public String getDbType() {
        return this.dbType;
    }

    /**
     * <p>
     * 设置数据库类型。
     * </p>
     * @param dbType 数据库类型；为空时自动推断
     */
    public void setDbType(String dbType) {
        this.dbType = dbType == null || dbType.isBlank() ? null : dbType.trim();
    }

    /**
     * <p>
     * 返回页码溢出时是否回到第一页。
     * </p>
     * @return 回到第一页时返回 {@code true}
     */
    public boolean isOverflow() {
        return this.overflow;
    }

    /**
     * <p>
     * 设置页码溢出时是否回到第一页。
     * </p>
     * @param overflow 是否回到第一页
     */
    public void setOverflow(boolean overflow) {
        this.overflow = overflow;
    }

    /**
     * <p>
     * 返回单页最大记录数限制。
     * </p>
     * @return 单页最大记录数；未限制时为空
     */
    public Long getMaxLimit() {
        return this.maxLimit;
    }

    /**
     * <p>
     * 设置单页最大记录数限制。
     * </p>
     * @param maxLimit 单页最大记录数；小于等于零时表示不限制
     */
    public void setMaxLimit(Long maxLimit) {
        this.maxLimit = maxLimit == null || maxLimit <= 0 ? null : maxLimit;
    }

    /**
     * <p>
     * 返回生成分页统计 SQL 时是否优化关联查询。
     * </p>
     * @return 优化关联查询时返回 {@code true}
     */
    public boolean isOptimizeJoin() {
        return this.optimizeJoin;
    }

    /**
     * <p>
     * 设置生成分页统计 SQL 时是否优化关联查询。
     * </p>
     * @param optimizeJoin 是否优化关联查询
     */
    public void setOptimizeJoin(boolean optimizeJoin) {
        this.optimizeJoin = optimizeJoin;
    }
}
