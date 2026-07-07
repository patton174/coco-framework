package io.github.coco.feature.web.body;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Coco 请求体缓存配置属性。
 * <p>
 * 控制请求体缓存过滤器的触发方式、允许方法、内容类型和最大缓存长度。
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
public class CocoRequestBodyProperties {

    private static final int DEFAULT_MAX_CACHE_BYTES = 1024 * 1024;

    private static final Set<String> DEFAULT_CACHE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private static final Set<String> DEFAULT_TRIGGER_HEADER_NAMES = Set.of(
            "content-md5", "x-coco-sign", "x-coco-signature", "x-coco-encrypted");

    private static final Set<String> DEFAULT_INCLUDED_CONTENT_TYPES = Set.of(
            "application/json", "application/*+json", "text/plain", "application/xml", "text/xml");

    private static final Set<String> DEFAULT_EXCLUDED_CONTENT_TYPE_PREFIXES = Set.of(
            "multipart/", "application/octet-stream");

    private boolean enabled = true;

    private CocoRequestBodyCachingMode mode = CocoRequestBodyCachingMode.SECURITY_HEADERS;

    private int maxCacheBytes = DEFAULT_MAX_CACHE_BYTES;

    private Set<String> cacheMethods = DEFAULT_CACHE_METHODS;

    private Set<String> triggerHeaderNames = DEFAULT_TRIGGER_HEADER_NAMES;

    private Set<String> includedContentTypes = DEFAULT_INCLUDED_CONTENT_TYPES;

    private Set<String> excludedContentTypePrefixes = DEFAULT_EXCLUDED_CONTENT_TYPE_PREFIXES;

    /**
     * <p>
     * 返回是否启用请求体缓存设施。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用请求体缓存设施。
     * </p>
     * @param enabled 是否启用请求体缓存设施
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回请求体缓存模式。
     * </p>
     * @return 请求体缓存模式
     */
    public CocoRequestBodyCachingMode getMode() {
        return this.mode;
    }

    /**
     * <p>
     * 设置请求体缓存模式。
     * </p>
     * @param mode 请求体缓存模式
     */
    public void setMode(CocoRequestBodyCachingMode mode) {
        this.mode = mode == null ? CocoRequestBodyCachingMode.SECURITY_HEADERS : mode;
    }

    /**
     * <p>
     * 返回最大请求体缓存字节数。
     * </p>
     * @return 最大请求体缓存字节数
     */
    public int getMaxCacheBytes() {
        return this.maxCacheBytes;
    }

    /**
     * <p>
     * 设置最大请求体缓存字节数。
     * </p>
     * @param maxCacheBytes 最大请求体缓存字节数
     */
    public void setMaxCacheBytes(int maxCacheBytes) {
        this.maxCacheBytes = maxCacheBytes <= 0 ? DEFAULT_MAX_CACHE_BYTES : maxCacheBytes;
    }

    /**
     * <p>
     * 返回允许缓存请求体的 HTTP 方法集合。
     * </p>
     * @return HTTP 方法集合
     */
    public Set<String> getCacheMethods() {
        return this.cacheMethods;
    }

    /**
     * <p>
     * 设置允许缓存请求体的 HTTP 方法集合。
     * </p>
     * @param cacheMethods HTTP 方法集合
     */
    public void setCacheMethods(Set<String> cacheMethods) {
        this.cacheMethods = normalizeUppercaseValues(cacheMethods, DEFAULT_CACHE_METHODS);
    }

    /**
     * <p>
     * 返回触发请求体缓存的请求头名称集合。
     * </p>
     * @return 请求头名称集合
     */
    public Set<String> getTriggerHeaderNames() {
        return this.triggerHeaderNames;
    }

    /**
     * <p>
     * 设置触发请求体缓存的请求头名称集合。
     * </p>
     * @param triggerHeaderNames 请求头名称集合
     */
    public void setTriggerHeaderNames(Set<String> triggerHeaderNames) {
        this.triggerHeaderNames = normalizeLowercaseValues(triggerHeaderNames, DEFAULT_TRIGGER_HEADER_NAMES);
    }

    /**
     * <p>
     * 返回允许缓存请求体的内容类型集合。
     * </p>
     * @return 内容类型集合
     */
    public Set<String> getIncludedContentTypes() {
        return this.includedContentTypes;
    }

    /**
     * <p>
     * 设置允许缓存请求体的内容类型集合。
     * </p>
     * @param includedContentTypes 内容类型集合
     */
    public void setIncludedContentTypes(Set<String> includedContentTypes) {
        this.includedContentTypes = normalizeMediaTypes(includedContentTypes, DEFAULT_INCLUDED_CONTENT_TYPES);
    }

    /**
     * <p>
     * 返回排除缓存请求体的内容类型前缀集合。
     * </p>
     * @return 内容类型前缀集合
     */
    public Set<String> getExcludedContentTypePrefixes() {
        return this.excludedContentTypePrefixes;
    }

    /**
     * <p>
     * 设置排除缓存请求体的内容类型前缀集合。
     * </p>
     * @param excludedContentTypePrefixes 内容类型前缀集合
     */
    public void setExcludedContentTypePrefixes(Set<String> excludedContentTypePrefixes) {
        this.excludedContentTypePrefixes = normalizeMediaTypes(excludedContentTypePrefixes,
                DEFAULT_EXCLUDED_CONTENT_TYPE_PREFIXES);
    }

    private static Set<String> normalizeUppercaseValues(Set<String> values, Set<String> defaults) {
        if (values == null || values.isEmpty()) {
            return defaults;
        }
        Set<String> normalizedValues = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalizedValues.add(value.trim().toUpperCase(Locale.ROOT));
            }
        }
        return normalizedValues.isEmpty() ? defaults : Set.copyOf(normalizedValues);
    }

    private static Set<String> normalizeLowercaseValues(Set<String> values, Set<String> defaults) {
        if (values == null || values.isEmpty()) {
            return defaults;
        }
        Set<String> normalizedValues = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalizedValues.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return normalizedValues.isEmpty() ? defaults : Set.copyOf(normalizedValues);
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
