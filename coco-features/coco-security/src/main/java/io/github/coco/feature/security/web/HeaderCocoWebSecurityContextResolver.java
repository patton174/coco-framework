package io.github.coco.feature.security.web;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 基于可信请求头的 Coco Web 安全上下文解析器。
 * <p>
 * 仅在 {@link CocoSecurityWebHeaderProperties#isEnabled()} 启用时读取请求头。该实现不负责认证，只消费可信上游已经写入的主体信息。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-security}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class HeaderCocoWebSecurityContextResolver implements CocoWebSecurityContextResolver {

    private final CocoSecurityWebHeaderProperties properties;

    /**
     * <p>
     * 创建可信请求头安全上下文解析器。
     * </p>
     * @param properties 可信请求头配置
     */
    public HeaderCocoWebSecurityContextResolver(CocoSecurityWebHeaderProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<CocoSecurityContext> resolve(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (!this.properties.isEnabled()) {
            return Optional.empty();
        }
        HeaderValue principalIdHeader = singleHeaderValue(request, this.properties.getPrincipalIdHeaderName());
        HeaderValue principalNameHeader = singleHeaderValue(request, this.properties.getPrincipalNameHeaderName());
        HeaderValue rolesHeader = singleHeaderValue(request, this.properties.getRolesHeaderName());
        HeaderValue permissionsHeader = singleHeaderValue(request, this.properties.getPermissionsHeaderName());
        if (!principalIdHeader.valid() || !principalNameHeader.valid()
                || !rolesHeader.valid() || !permissionsHeader.valid()) {
            return Optional.empty();
        }
        String principalId = normalize(principalIdHeader.value());
        if (principalId == null) {
            return Optional.empty();
        }
        String principalName = normalize(principalNameHeader.value());
        Set<String> roles = splitAuthorities(rolesHeader.value());
        Set<String> permissions = splitAuthorities(permissionsHeader.value());
        CocoSecurityPrincipal principal = new CocoSecurityPrincipal(principalId,
                principalName == null ? principalId : principalName, roles, permissions, Map.of());
        return Optional.of(CocoSecurityContext.authenticated(principal));
    }

    private static HeaderValue singleHeaderValue(HttpServletRequest request, String headerName) {
        Enumeration<String> values = request.getHeaders(headerName);
        if (values == null || !values.hasMoreElements()) {
            return HeaderValue.valid(null);
        }
        String value = values.nextElement();
        if (values.hasMoreElements()) {
            return HeaderValue.invalid();
        }
        return HeaderValue.valid(value);
    }

    private Set<String> splitAuthorities(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return Set.of();
        }
        String delimiter = this.properties.getAuthorityDelimiter();
        return Arrays.stream(normalized.split(java.util.regex.Pattern.quote(delimiter)))
                .map(HeaderCocoWebSecurityContextResolver::normalize)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record HeaderValue(boolean valid, String value) {

        private static HeaderValue valid(String value) {
            return new HeaderValue(true, value);
        }

        private static HeaderValue invalid() {
            return new HeaderValue(false, null);
        }
    }
}
