package io.github.coco.feature.mybatisplus.datasource;

/**
 * 活动事务内切换数据源时的保护策略。
 * <p>
 * Spring 本地事务开启后会缓存首次获取的物理连接，事务内再次切换数据源无法改变当前事务绑定的连接。
 * 默认使用 {@link #REJECT} 显式拦截跨源事务切换。
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
public enum CocoDataSourceTransactionPolicy {

    /**
     * 拒绝在已激活的本地事务内切换到其他数据源并抛出冲突异常。
     */
    REJECT,

    /**
     * 记录告警日志并继续执行。
     */
    WARN,

    /**
     * 允许切换且不记录告警日志（适用于挂起事务或自定义事务管理器场景）。
     */
    ALLOW
}
