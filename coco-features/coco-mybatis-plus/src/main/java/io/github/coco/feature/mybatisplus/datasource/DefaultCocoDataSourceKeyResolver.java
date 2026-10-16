package io.github.coco.feature.mybatisplus.datasource;

import io.github.coco.feature.mybatisplus.CocoMybatisPlusErrorCode;
import java.lang.reflect.Method;
import java.util.Objects;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * 默认数据源路由键解析器实现。
 * <p>
 * 支持固定数据源名称与以 {@code #} 或 {@code #{...}} 开头的 Spring SpEL 表达式，拒绝缺失、空白或超长的路由键。
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
public final class DefaultCocoDataSourceKeyResolver implements CocoDataSourceKeyResolver {

    private final ExpressionParser expressionParser = new SpelExpressionParser();

    private final DefaultParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();

    private final int maxKeyLength;

    /**
     * <p>
     * 根据多数据源配置属性创建默认路由键解析器。
     * </p>
     * @param properties 多数据源配置属性
     */
    public DefaultCocoDataSourceKeyResolver(CocoMybatisPlusDataSourceProperties properties) {
        this.maxKeyLength = positive(
                Objects.requireNonNull(properties, "properties must not be null").getMaxKeyLength());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String resolve(String keyExpression, Object target, Method method, Object[] arguments) {
        if (keyExpression == null || keyExpression.isBlank()) {
            throw CocoMybatisPlusErrorCode.INVALID_DATASOURCE_KEY.request();
        }
        String trimmedExpression = keyExpression.trim();
        String key = isExpression(trimmedExpression)
                ? evaluate(trimmedExpression, target, method, arguments)
                : trimmedExpression;
        if (key == null || key.isBlank()) {
            throw CocoMybatisPlusErrorCode.INVALID_DATASOURCE_KEY.request();
        }
        String normalizedKey = key.trim();
        if (normalizedKey.length() > this.maxKeyLength) {
            throw CocoMybatisPlusErrorCode.INVALID_DATASOURCE_KEY.request();
        }
        return normalizedKey;
    }

    private String evaluate(String source, Object target, Method method, Object[] arguments) {
        try {
            String expressionText = source.startsWith("#{") && source.endsWith("}")
                    ? source.substring(2, source.length() - 1)
                    : source;
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                    target,
                    method,
                    arguments == null ? new Object[0] : arguments,
                    this.parameterNames);
            if (target != null) {
                context.setVariable("target", target);
            }
            Expression expression = this.expressionParser.parseExpression(expressionText);
            Object value = expression.getValue(context);
            return value == null ? null : String.valueOf(value);
        }
        catch (RuntimeException exception) {
            throw CocoMybatisPlusErrorCode.INVALID_DATASOURCE_KEY.request();
        }
    }

    private static boolean isExpression(String value) {
        return value.startsWith("#");
    }

    private static int positive(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("coco.mybatis-plus.datasource.max-key-length must be positive");
        }
        return value;
    }
}
