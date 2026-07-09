package io.github.coco.feature.web.trace;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 默认 Coco TraceId 校验器。
 * <p>
 * 根据 {@link CocoTraceProperties} 中的长度和正则表达式限制校验外部传入的 TraceId。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class DefaultCocoTraceIdValidator implements CocoTraceIdValidator {

    private final int maxLength;

    private final Pattern allowedPattern;

    /**
     * <p>
     * 创建默认 TraceId 校验器。
     * </p>
     * @param properties Trace 配置属性
     */
    public DefaultCocoTraceIdValidator(CocoTraceProperties properties) {
        CocoTraceProperties checkedProperties = properties == null ? new CocoTraceProperties() : properties;
        this.maxLength = checkedProperties.getMaxLength();
        this.allowedPattern = compileAllowedPattern(checkedProperties.getAllowedPattern());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isValid(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return false;
        }
        String checkedTraceId = traceId.trim();
        return checkedTraceId.length() <= this.maxLength
                && this.allowedPattern.matcher(checkedTraceId).matches();
    }

    private static Pattern compileAllowedPattern(String allowedPattern) {
        try {
            return Pattern.compile(allowedPattern);
        }
        catch (PatternSyntaxException ex) {
            return Pattern.compile(CocoTraceProperties.DEFAULT_ALLOWED_PATTERN);
        }
    }
}
