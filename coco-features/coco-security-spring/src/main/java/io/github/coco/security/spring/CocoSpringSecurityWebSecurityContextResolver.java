package io.github.coco.security.spring;

import java.util.Objects;
import java.util.Optional;

import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import io.github.coco.feature.security.web.CocoWebSecurityContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolves the current Spring Security authentication as a Coco Web security context.
 *
 * @author patton174
 * @since 1.0.0
 */
public final class CocoSpringSecurityWebSecurityContextResolver implements CocoWebSecurityContextResolver {

    private final CocoSpringSecurityPrincipalConverter principalConverter;

    /**
     * Creates a Spring Security context resolver.
     * @param principalConverter Coco principal conversion SPI
     */
    public CocoSpringSecurityWebSecurityContextResolver(CocoSpringSecurityPrincipalConverter principalConverter) {
        this.principalConverter = Objects.requireNonNull(principalConverter, "principalConverter must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<CocoSecurityContext> resolve(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        CocoSecurityPrincipal principal = this.principalConverter.convert(authentication);
        return Optional.of(CocoSecurityContext.authenticated(principal));
    }
}
