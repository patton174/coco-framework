package io.github.coco.datapermission.sql;

import java.util.Optional;

import net.sf.jsqlparser.expression.Expression;

/**
 * 数据权限 SQL 谓词提供器。
 * <p>
 * 将数据权限上下文和业务规则转换为 JSqlParser 表达式，复杂组织树、部门层级或 EXISTS 规则可以通过该 SPI 自定义。
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
@FunctionalInterface
public interface CocoDataPermissionSqlPredicateProvider {

    /**
     * <p>
     * 生成当前表对应的数据权限 SQL 谓词。
     * </p>
     * @param context SQL 谓词生成上下文
     * @return 数据权限 SQL 谓词；不需要追加条件时为空
     */
    Optional<Expression> predicate(CocoDataPermissionSqlPredicateContext context);
}
