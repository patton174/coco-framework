package io.github.coco.feature.lock;

import java.lang.reflect.Method;
import java.util.Objects;

import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/** 使用 Spring Expression 解析锁键，拒绝缺失、空白或过长的结果。 */
public final class DefaultCocoLockKeyResolver implements CocoLockKeyResolver {
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();
    private final int maxKeyLength;

    /** 创建默认解析器。 */
    public DefaultCocoLockKeyResolver(CocoLockProperties properties) {
        this.maxKeyLength = positive(Objects.requireNonNull(properties, "properties must not be null").getMaxKeyLength());
    }

    @Override
    public String resolve(String keyExpression, Object target, Method method, Object[] arguments) {
        if (keyExpression == null || keyExpression.isBlank()) { throw CocoLockErrorCode.INVALID_KEY.request(); }
        String key = isExpression(keyExpression) ? evaluate(keyExpression, target, method, arguments) : keyExpression;
        if (key == null || key.isBlank() || key.length() > this.maxKeyLength) {
            throw CocoLockErrorCode.INVALID_KEY.request();
        }
        return key;
    }

    private String evaluate(String source, Object target, Method method, Object[] arguments) {
        try {
            String expressionText = source.startsWith("#{") && source.endsWith("}")
                    ? source.substring(2, source.length() - 1) : source;
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(target, method,
                    arguments == null ? new Object[0] : arguments, this.parameterNames);
            context.setVariable("target", target);
            Expression expression = this.expressionParser.parseExpression(expressionText);
            Object value = expression.getValue(context);
            return value == null ? null : String.valueOf(value);
        }
        catch (RuntimeException exception) {
            throw CocoLockErrorCode.INVALID_KEY.request();
        }
    }

    private static boolean isExpression(String value) {
        return value.startsWith("#");
    }

    private static int positive(int value) {
        if (value < 1) { throw new IllegalArgumentException("coco.lock.max-key-length must be positive"); }
        return value;
    }
}
