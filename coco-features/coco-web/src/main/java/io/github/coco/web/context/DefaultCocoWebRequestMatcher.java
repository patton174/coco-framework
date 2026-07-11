package io.github.coco.web.context;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.AntPathMatcher;

/**
 * Coco Web 默认请求匹配器。
 * <p>
 * 使用 Spring {@code AntPathMatcher} 匹配 Servlet 应用内路径，并按 HTTP 方法做大小写无关匹配。
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
public final class DefaultCocoWebRequestMatcher implements CocoWebRequestMatcher {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(HttpServletRequest request, List<CocoWebRequestMatchRule> rules) {
        Objects.requireNonNull(request, "request must not be null");
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        String method = requestMethod(request);
        String path = requestPath(request);
        return rules.stream().anyMatch(rule -> matches(rule, method, path));
    }

    private boolean matches(CocoWebRequestMatchRule rule, String method, String path) {
        if (rule == null || rule.isEmpty()) {
            return false;
        }
        boolean methodMatches = rule.getMethods().isEmpty() || rule.getMethods().contains(method);
        boolean pathMatches = rule.getPathPatterns().isEmpty()
                || rule.getPathPatterns().stream().anyMatch(pattern -> this.pathMatcher.match(pattern, path));
        return methodMatches && pathMatches;
    }

    private static String requestMethod(HttpServletRequest request) {
        String method = request.getMethod();
        return method == null || method.isBlank() ? "" : method.trim().toUpperCase(Locale.ROOT);
    }

    private static String requestPath(HttpServletRequest request) {
        String requestUri = normalizePath(request.getRequestURI());
        if (requestUri == null) {
            requestUri = normalizePath(request.getServletPath());
        }
        if (requestUri == null) {
            return "/";
        }
        String contextPath = normalizePath(request.getContextPath());
        if (contextPath != null && !"/".equals(contextPath) && requestUri.startsWith(contextPath)) {
            requestUri = requestUri.substring(contextPath.length());
        }
        return requestUri.isBlank() ? "/" : normalizePath(requestUri);
    }

    private static String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String path = value.trim();
        return path.startsWith("/") ? path : "/" + path;
    }
}
