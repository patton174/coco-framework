package io.github.coco.feature.datapermission.sql;

import java.util.List;
import java.util.Objects;

import io.github.coco.feature.datapermission.context.CocoDataPermissionRule;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

/**
 * 默认数据权限 SQL 谓词提供器。
 * <p>
 * 默认实现只提供框架级通用策略：全部数据不追加条件，拒绝访问或缺少必要列配置时追加永假条件，
 * 自定义范围和本人范围按配置列生成 {@code IN} 条件。复杂业务模型应替换该 SPI。
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
public final class DefaultCocoDataPermissionSqlPredicateProvider implements CocoDataPermissionSqlPredicateProvider {

    /**
     * {@inheritDoc}
     */
    @Override
    public java.util.Optional<Expression> predicate(CocoDataPermissionSqlPredicateContext context) {
        Objects.requireNonNull(context, "context must not be null");
        CocoDataPermissionRule rule = context.rule();
        if (rule.allData()) {
            return java.util.Optional.empty();
        }
        if (rule.denied() || rule.values().isEmpty() || !hasText(context.resourceProperties().getColumn())) {
            return java.util.Optional.of(denyExpression());
        }
        Column column = new Column(qualifier(context.table()), context.resourceProperties().getColumn());
        List<Expression> values = rule.values().stream()
                .sorted()
                .map(StringValue::new)
                .map(Expression.class::cast)
                .toList();
        return java.util.Optional.of(new InExpression(column, new ParenthesedExpressionList<>(values)));
    }

    /**
     * <p>
     * 创建永假 SQL 表达式。
     * </p>
     * @return 永假 SQL 表达式
     */
    public static Expression denyExpression() {
        return new EqualsTo(new LongValue(1L), new LongValue(0L));
    }

    private static Table qualifier(Table table) {
        if (table.getAlias() != null && hasText(table.getAlias().getName())) {
            return new Table(table.getAlias().getName());
        }
        return new Table(table.getName());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
