package io.github.coco.security.jwt;

import java.util.Objects;
import java.util.Optional;

import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import io.github.coco.feature.security.web.CocoWebSecurityContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Spring Security JWT 到 Coco Web 安全上下文的桥接解析器。

 * @author patton174
 * @since 1.0.0
 */
public final class CocoJwtWebSecurityContextResolver implements CocoWebSecurityContextResolver {

    private final CocoJwtPrincipalConverter principalConverter;

    /**
     * 创建 JWT 安全上下文解析器。
     * @param principalConverter Coco 主体转换 SPI
     */
    public CocoJwtWebSecurityContextResolver(CocoJwtPrincipalConverter principalConverter) {
        this.principalConverter = Objects.requireNonNull(principalConverter,
                "principalConverter must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<CocoSecurityContext> resolve(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !jwtAuthentication.isAuthenticated()
                || !(jwtAuthentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        CocoSecurityPrincipal principal = this.principalConverter.convert(jwt, jwtAuthentication.getAuthorities());
        return Optional.of(CocoSecurityContext.authenticated(principal));
    }
}
