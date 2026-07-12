package io.github.coco.feature.web.context;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Coco Web 请求匹配规则。
 * <p>
 * 一条规则可以同时声明 HTTP 方法和请求路径模式；两者同时存在时必须同时匹配，任意一侧为空时表示不限制该侧。
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
public class CocoWebRequestMatchRule {

    private Set<String> methods = Set.of();

    private Set<String> pathPatterns = Set.of();

    /**
     * <p>
     * 返回当前规则匹配的 HTTP 方法集合。
     * </p>
     * @return HTTP 方法集合
     */
    public Set<String> getMethods() {
        return this.methods;
    }

    /**
     * <p>
     * 设置当前规则匹配的 HTTP 方法集合。
     * </p>
     * @param methods HTTP 方法集合
     */
    public void setMethods(Set<String> methods) {
        this.methods = normalizeMethods(methods);
    }

    /**
     * <p>
     * 返回当前规则匹配的请求路径模式集合。
     * </p>
     * @return 请求路径模式集合
     */
    public Set<String> getPathPatterns() {
        return this.pathPatterns;
    }

    /**
     * <p>
     * 设置当前规则匹配的请求路径模式集合。
     * </p>
     * @param pathPatterns 请求路径模式集合
     */
    public void setPathPatterns(Set<String> pathPatterns) {
        this.pathPatterns = normalizePathPatterns(pathPatterns);
    }

    /**
     * <p>
     * 判断当前规则是否为空规则。
     * </p>
     * @return 未配置方法和路径模式时返回 {@code true}
     */
    public boolean isEmpty() {
        return this.methods.isEmpty() && this.pathPatterns.isEmpty();
    }

    private static Set<String> normalizeMethods(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim().toUpperCase(Locale.ROOT));
            }
        }
        return normalized.isEmpty() ? Set.of() : Collections.unmodifiableSet(normalized);
    }

    private static Set<String> normalizePathPatterns(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String pattern = normalizePathPattern(value);
            if (pattern != null) {
                normalized.add(pattern);
            }
        }
        return normalized.isEmpty() ? Set.of() : Collections.unmodifiableSet(normalized);
    }

    private static String normalizePathPattern(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = value.trim();
        if ("*".equals(pattern) || "/".equals(pattern)) {
            return pattern;
        }
        return pattern.startsWith("/") ? pattern : "/" + pattern;
    }
}
