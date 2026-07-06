package io.github.coco.feature.web.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 默认 Coco 请求 Cookie 解析器。
 * <p>
 * 按 Web 上下文配置中的 Cookie 白名单采集请求 Cookie，并对敏感 Cookie 执行脱敏和长度裁剪。
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
public final class DefaultCocoRequestCookieResolver implements CocoRequestCookieResolver {

    private static final String MASKED_VALUE = "******";

    private final CocoWebContextProperties properties;

    /**
     * <p>
     * 创建默认 Coco 请求 Cookie 解析器。
     * </p>
     * @param properties Web 请求上下文配置属性
     */
    public DefaultCocoRequestCookieResolver(CocoWebContextProperties properties) {
        this.properties = properties == null ? new CocoWebContextProperties() : properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> resolveIncludedCookies(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        if (this.properties.getIncludedCookieNames().isEmpty()) {
            return Map.of();
        }
        Cookie[] cookies = checkedRequest.getCookies();
        if (cookies == null || cookies.length == 0) {
            return Map.of();
        }
        Map<String, String> resolvedCookies = new LinkedHashMap<>();
        for (Cookie cookie : cookies) {
            String name = normalizeString(cookie == null ? null : cookie.getName());
            if (name != null && this.properties.getIncludedCookieNames().contains(name)
                    && !resolvedCookies.containsKey(name)) {
                resolvedCookies.put(name, sanitizeCookieValue(name, cookie.getValue()));
            }
        }
        return resolvedCookies.isEmpty() ? Map.of() : Collections.unmodifiableMap(resolvedCookies);
    }

    private String sanitizeCookieValue(String name, String value) {
        if (this.properties.getMaskedCookieNames().contains(name)
                || this.properties.getMaskedCookieNames().contains(name.toLowerCase(Locale.ROOT))) {
            return MASKED_VALUE;
        }
        return trimValue(value, this.properties.getMaxCookieValueLength());
    }

    private static String trimValue(String value, int maxLength) {
        String normalized = normalizeString(value);
        if (normalized == null) {
            return "";
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }

    private static String normalizeString(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
