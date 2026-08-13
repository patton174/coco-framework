package io.github.coco.security.spring;

import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import org.springframework.security.core.Authentication;

/**
 * Converts an authenticated Spring Security identity to a Coco security principal.
 * <p>
 * Implementations must not copy authentication credentials, details, or principal objects to the Coco principal.
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoSpringSecurityPrincipalConverter {

    /**
     * Converts a previously authenticated identity.
     * @param authentication authenticated Spring Security identity
     * @return Coco security principal
     */
    CocoSecurityPrincipal convert(Authentication authentication);
}
