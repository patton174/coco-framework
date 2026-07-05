package io.github.coco.feature.web.accesslog;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Coco Web 访问日志采集配置属性。
 * <p>
 * 绑定 {@code coco.web.access-log} 命名空间，只控制 Web 入口是否发布访问日志事件以及请求参数采集策略。
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
public class CocoAccessLogCaptureProperties {

    private static final int DEFAULT_MAX_PARAMETER_VALUE_LENGTH = 256;

    private static final Set<String> DEFAULT_MASKED_PARAMETER_NAMES = Set.of(
            "password", "passwd", "pwd", "secret", "token", "access_token", "refresh_token", "authorization");

    private boolean enabled = true;

    private boolean includeParameters = true;

    private int maxParameterValueLength = DEFAULT_MAX_PARAMETER_VALUE_LENGTH;

    private Set<String> maskedParameterNames = DEFAULT_MASKED_PARAMETER_NAMES;

    /**
     * <p>
     * 返回是否发布接口访问日志事件。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否发布接口访问日志事件。
     * </p>
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回是否采集请求参数。
     * </p>
     * @return 采集请求参数时返回 {@code true}
     */
    public boolean isIncludeParameters() {
        return this.includeParameters;
    }

    /**
     * <p>
     * 设置是否采集请求参数。
     * </p>
     * @param includeParameters 是否采集请求参数
     */
    public void setIncludeParameters(boolean includeParameters) {
        this.includeParameters = includeParameters;
    }

    /**
     * <p>
     * 返回单个请求参数值最大采集长度。
     * </p>
     * @return 单个请求参数值最大采集长度
     */
    public int getMaxParameterValueLength() {
        return this.maxParameterValueLength;
    }

    /**
     * <p>
     * 设置单个请求参数值最大采集长度。
     * </p>
     * @param maxParameterValueLength 单个请求参数值最大采集长度
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
