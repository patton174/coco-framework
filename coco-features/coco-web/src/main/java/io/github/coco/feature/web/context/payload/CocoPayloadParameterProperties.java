package io.github.coco.feature.web.context.payload;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Coco 请求体参数配置属性。
 * <p>
 * 控制从已缓存请求体中解析 payload 参数的默认策略，供日志上下文、签名规范化、AES 和指纹等能力复用。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public class CocoPayloadParameterProperties {

    private static final int DEFAULT_MAX_JSON_DEPTH = 8;

    private static final int DEFAULT_MAX_PARAMETER_COUNT = 128;

    private static final Set<String> DEFAULT_INCLUDED_CONTENT_TYPES = Set.of(
            "application/json", "application/*+json", "application/x-www-form-urlencoded");

    private boolean enabled = true;

    private Set<String> includedContentTypes = DEFAULT_INCLUDED_CONTENT_TYPES;

    private int maxJsonDepth = DEFAULT_MAX_JSON_DEPTH;

    private int maxParameterCount = DEFAULT_MAX_PARAMETER_COUNT;

    /**
     * <p>
     * 返回是否启用请求体参数解析。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用请求体参数解析。
     * </p>
     * @param enabled 是否启用请求体参数解析
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回允许解析为请求体参数的内容类型集合。
     * </p>
     * @return 内容类型集合
     */
    public Set<String> getIncludedContentTypes() {
        return this.includedContentTypes;
    }

    /**
     * <p>
     * 设置允许解析为请求体参数的内容类型集合。
     * </p>
     * @param includedContentTypes 内容类型集合
     */
    public void setIncludedContentTypes(Set<String> includedContentTypes) {
        this.includedContentTypes = normalizeMediaTypes(includedContentTypes, DEFAULT_INCLUDED_CONTENT_TYPES);
    }

    /**
     * <p>
     * 返回 JSON 请求体最大递归解析深度。
     * </p>
     * @return JSON 最大递归解析深度
     */
    public int getMaxJsonDepth() {
        return this.maxJsonDepth;
    }

    /**
     * <p>
     * 设置 JSON 请求体最大递归解析深度。
     * </p>
     * @param maxJsonDepth JSON 最大递归解析深度
     */
    public void setMaxJsonDepth(int maxJsonDepth) {
        this.maxJsonDepth = maxJsonDepth <= 0 ? DEFAULT_MAX_JSON_DEPTH : maxJsonDepth;
    }

    /**
     * <p>
     * 返回单次请求最多解析的请求体参数数量。
     * </p>
     * @return 请求体参数数量上限
     */
    public int getMaxParameterCount() {
        return this.maxParameterCount;
    }

    /**
     * <p>
     * 设置单次请求最多解析的请求体参数数量。
     * </p>
     * @param maxParameterCount 请求体参数数量上限
     */
    public void setMaxParameterCount(int maxParameterCount) {
        this.maxParameterCount = maxParameterCount <= 0 ? DEFAULT_MAX_PARAMETER_COUNT : maxParameterCount;
    }

    private static Set<String> normalizeMediaTypes(Set<String> mediaTypes, Set<String> defaults) {
        if (mediaTypes == null || mediaTypes.isEmpty()) {
            return defaults;
        }
        Set<String> normalizedMediaTypes = new LinkedHashSet<>();
        for (String mediaType : mediaTypes) {
            String normalizedMediaType = normalizeMediaType(mediaType);
            if (normalizedMediaType != null) {
                normalizedMediaTypes.add(normalizedMediaType);
            }
        }
        return normalizedMediaTypes.isEmpty() ? defaults : Set.copyOf(normalizedMediaTypes);
    }

    private static String normalizeMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return null;
        }
        int parameterIndex = mediaType.indexOf(';');
        String value = parameterIndex < 0 ? mediaType : mediaType.substring(0, parameterIndex);
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
