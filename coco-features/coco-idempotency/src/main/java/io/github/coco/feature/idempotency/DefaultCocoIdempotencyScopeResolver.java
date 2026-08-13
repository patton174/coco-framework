package io.github.coco.feature.idempotency;

import java.util.Objects;

import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityContextHolder;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Default idempotency scope resolver backed by the verified Coco security context.
 *
 * @author patton174
 * @since 1.0.0
 */
final class DefaultCocoIdempotencyScopeResolver implements CocoIdempotencyScopeResolver {

    /**
     * {@inheritDoc}
     */
    @Override
    public String resolve(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return CocoSecurityContextHolder.current()
                .filter(CocoSecurityContext::authenticated)
                .map(context -> context.principal().principalId())
                .filter(scope -> !scope.isBlank())
                .orElseThrow(() -> CocoIdempotencyErrorCode.SCOPE_REQUIRED.unauthorized());
    }
}
