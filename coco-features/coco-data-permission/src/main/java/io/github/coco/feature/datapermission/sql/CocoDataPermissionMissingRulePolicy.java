package io.github.coco.feature.datapermission.sql;

/**
 * 数据权限 SQL 缺少资源规则时的处理策略。
 * <p>
 * 当表已经映射到业务资源但当前上下文没有对应规则时，默认拒绝访问，避免静默扩大数据范围。
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
public enum CocoDataPermissionMissingRulePolicy {

    /**
     * 追加永假 SQL 条件。
     */
    DENY,

    /**
     * 忽略数据权限 SQL 条件。
     */
    IGNORE
}
