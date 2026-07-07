package io.github.coco.feature.web.context;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import io.github.coco.feature.web.context.payload.CocoPayloadParameterProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco Web 请求参数配置属性。
 * <p>
 * 控制 Web 请求上下文中的参数采集、脱敏和裁剪策略，不控制访问日志是否最终输出这些参数。
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
public class CocoWebParameterProperties {

    private static final int DEFAULT_MAX_PARAMETER_VALUE_LENGTH = 256;

    private static final Set<String> DEFAULT_MASKED_PARAMETER_NAMES = Set.of(
            "password", "passwd", "pwd", "secret", "token", "access_token", "refresh_token", "authorization");

    private boolean includeParameters = true;

    private int maxParameterValueLength = DEFAULT_MAX_PARAMETER_VALUE_LENGTH;

    private Set<String> maskedParameterNames = DEFAULT_MASKED_PARAMETER_NAMES;

    @NestedConfigurationProperty
    private CocoPayloadParameterProperties payload = new CocoPayloadParameterProperties();

    /**
     * <p>
     * 返回是否采集请求参数到 Web 请求上下文。
     * </p>
     * @return 采集请求参数时返回 {@code true}
     */
    public boolean isIncludeParameters() {
        return this.includeParameters;
    }

    /**
     * <p>
     * 设置是否采集请求参数到 Web 请求上下文。
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

    /**
     * <p>
     * 返回请求体参数解析配置。
     * </p>
     * @return 请求体参数解析配置
     */
    public CocoPayloadParameterProperties getPayload() {
        return this.payload;
    }

    /**
     * <p>
     * 设置请求体参数解析配置。
     * </p>
     * @param payload 请求体参数解析配置
     */
    public void setPayload(CocoPayloadParameterProperties payload) {
        this.payload = payload == null ? new CocoPayloadParameterProperties() : payload;
    }
}
