package io.github.coco.feature.web.logging;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Coco 接口访问日志打印配置属性。
 * <p>
 * 绑定 {@code coco.web.access-log} 命名空间，控制接口访问日志是否打印、打印级别、输出样式、隔离 logger 名称和请求参数采集策略。
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
public class CocoAccessLogProperties {

    /**
     * 默认接口访问日志 logger 名称。
     */
    public static final String DEFAULT_LOGGER_NAME = "io.github.coco.access";

    private static final int DEFAULT_MAX_PARAMETER_VALUE_LENGTH = 256;

    private static final Set<String> DEFAULT_MASKED_PARAMETER_NAMES = Set.of(
            "password", "passwd", "pwd", "secret", "token", "access_token", "refresh_token", "authorization");

    private boolean enabled = true;

    private CocoAccessLogLevel level = CocoAccessLogLevel.INFO;

    private CocoAccessLogStyle style = CocoAccessLogStyle.TEXT;

    private String loggerName = DEFAULT_LOGGER_NAME;

    private boolean includeParameters = true;

    private int maxParameterValueLength = DEFAULT_MAX_PARAMETER_VALUE_LENGTH;

    private Set<String> maskedParameterNames = DEFAULT_MASKED_PARAMETER_NAMES;

    /**
     * <p>
     * 返回是否启用接口访问日志打印。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用接口访问日志打印。
     * </p>
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回接口访问日志打印级别。
     * </p>
     * @return 打印级别
     */
    public CocoAccessLogLevel getLevel() {
        return this.level;
    }

    /**
     * <p>
     * 设置接口访问日志打印级别。
     * </p>
     * @param level 打印级别
     */
    public void setLevel(CocoAccessLogLevel level) {
        this.level = level == null ? CocoAccessLogLevel.INFO : level;
    }

    /**
     * <p>
     * 返回接口访问日志输出样式。
     * </p>
     * @return 输出样式
     */
    public CocoAccessLogStyle getStyle() {
        return this.style;
    }

    /**
     * <p>
     * 设置接口访问日志输出样式。
     * </p>
     * @param style 输出样式
     */
    public void setStyle(CocoAccessLogStyle style) {
        this.style = style == null ? CocoAccessLogStyle.TEXT : style;
    }

    /**
     * <p>
     * 返回接口访问日志隔离 logger 名称。
     * </p>
     * @return logger 名称
     */
    public String getLoggerName() {
        return this.loggerName;
    }

    /**
     * <p>
     * 设置接口访问日志隔离 logger 名称。
     * </p>
     * @param loggerName logger 名称
     */
    public void setLoggerName(String loggerName) {
        this.loggerName = loggerName == null || loggerName.isBlank()
                ? DEFAULT_LOGGER_NAME
                : loggerName.trim();
    }

    /**
     * <p>
     * 返回是否记录请求参数。
     * </p>
     * @return 记录请求参数时返回 {@code true}
     */
    public boolean isIncludeParameters() {
        return this.includeParameters;
    }

    /**
     * <p>
     * 设置是否记录请求参数。
     * </p>
     * @param includeParameters 是否记录请求参数
     */
    public void setIncludeParameters(boolean includeParameters) {
        this.includeParameters = includeParameters;
    }

    /**
     * <p>
     * 返回单个请求参数值最大记录长度。
     * </p>
     * @return 单个请求参数值最大记录长度
     */
    public int getMaxParameterValueLength() {
        return this.maxParameterValueLength;
    }

    /**
     * <p>
     * 设置单个请求参数值最大记录长度。
     * </p>
     * @param maxParameterValueLength 单个请求参数值最大记录长度
     */
    public void setMaxParameterValueLength(int maxParameterValueLength) {
        this.maxParameterValueLength = maxParameterValueLength <= 0
                ? DEFAULT_MAX_PARAMETER_VALUE_LENGTH
                : maxParameterValueLength;
    }

    /**
     * <p>
     * 返回需要掩码的请求参数名集合。
     * </p>
     * @return 需要掩码的请求参数名集合
     */
    public Set<String> getMaskedParameterNames() {
        return this.maskedParameterNames;
    }

    /**
     * <p>
     * 设置需要掩码的请求参数名集合。
     * </p>
     * @param maskedParameterNames 需要掩码的请求参数名集合
     */
    public void setMaskedParameterNames(Set<String> maskedParameterNames) {
        if (maskedParameterNames == null || maskedParameterNames.isEmpty()) {
            this.maskedParameterNames = DEFAULT_MASKED_PARAMETER_NAMES;
            return;
        }
        Set<String> normalizedNames = new LinkedHashSet<>();
        for (String name : maskedParameterNames) {
            if (name != null && !name.isBlank()) {
                normalizedNames.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        this.maskedParameterNames = normalizedNames.isEmpty()
                ? DEFAULT_MASKED_PARAMETER_NAMES
                : Set.copyOf(normalizedNames);
    }
}
