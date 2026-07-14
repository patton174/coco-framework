package io.github.coco.feature.datapermission.sql;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import io.github.coco.feature.datapermission.context.CocoDataPermissionRule;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
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
 *   <li>模块：{@code coco-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class DefaultCocoDataPermissionSqlPredicateProvider implements CocoDataPermissionSqlPredicateProvider {

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Expression> predicate(CocoDataPermissionSqlPredicateContext context) {
        Objects.requireNonNull(context, "context must not be null");
        CocoDataPermissionRule rule = context.rule();
        if (rule.allData()) {
            return Optional.empty();
        }
        if (rule.denied() || rule.values().isEmpty() || !hasText(context.resourceProperties().getColumn())) {
            return Optional.of(denyExpression());
        }
        Column column = new Column(qualifier(context.table()), context.resourceProperties().getColumn());
        List<Expression> values = rule.values().stream()
                .sorted()
                .map(value -> valueExpression(value, context.resourceProperties().getColumnType()))
                .flatMap(Optional::stream)
                .toList();
        if (values.size() != rule.values().size()) {
            return Optional.of(denyExpression());
        }
        return Optional.of(new InExpression(column, new ParenthesedExpressionList<>(values)));
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

    private static Optional<Expression> valueExpression(String value, CocoDataPermissionSqlColumnType columnType) {
        return switch (columnType) {
            case STRING -> stringValueExpression(value).map(Expression.class::cast);
            case LONG -> longValueExpression(value).map(Expression.class::cast);
            case INTEGER -> integerValueExpression(value).map(Expression.class::cast);
            case DECIMAL -> decimalValueExpression(value).map(Expression.class::cast);
            case BOOLEAN -> booleanValueExpression(value);
        };
    }

    private static Optional<StringValue> stringValueExpression(String value) {
        return hasText(value) ? Optional.of(new StringValue(value.replace("'", "''"))) : Optional.empty();
    }

    private static Optional<LongValue> longValueExpression(String value) {
        String normalizedValue = normalize(value);
        if (normalizedValue == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new LongValue(Long.parseLong(normalizedValue)));
        }
        catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static Optional<LongValue> integerValueExpression(String value) {
        String normalizedValue = normalize(value);
        if (normalizedValue == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new LongValue(Integer.parseInt(normalizedValue)));
        }
        catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static Optional<DoubleValue> decimalValueExpression(String value) {
        String normalizedValue = normalize(value);
        if (normalizedValue == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new DoubleValue(new BigDecimal(normalizedValue).toPlainString()));
        }
        catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static Optional<Expression> booleanValueExpression(String value) {
        String normalizedValue = normalize(value);
        if (normalizedValue == null
                || (!"true".equalsIgnoreCase(normalizedValue) && !"false".equalsIgnoreCase(normalizedValue))) {
            return Optional.empty();
        }
        try {
            return Optional.of(CCJSqlParserUtil.parseExpression(normalizedValue.toUpperCase(Locale.ROOT)));
        }
        catch (JSQLParserException ex) {
            throw new IllegalStateException("Unable to create a boolean SQL literal", ex);
        }
    }

    private static String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
