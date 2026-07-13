package io.github.coco.feature.datapermission.sql;

/**
 * 数据权限 SQL 缺少上下文时的处理策略。
 * <p>
 * 数据权限属于访问控制边界，默认应当快速失败，业务明确需要后台任务或公开查询时再选择放行。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public enum CocoDataPermissionMissingContextPolicy {

    /**
     * 抛出框架异常。
     */
    THROW,

    /**
     * 追加永假 SQL 条件。
     */
    DENY,

    /**
     * 忽略数据权限 SQL 条件。
     */
    IGNORE
}
