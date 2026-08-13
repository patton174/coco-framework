package io.github.coco.feature.idempotency;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the caller-specific idempotency scope for a Servlet request.
 * <p>
 * Implementations must return a non-blank value. The filter hashes this value
 * before composing the storage key and never stores or logs the raw scope.
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoIdempotencyScopeResolver {

    /**
     * Resolves the idempotency scope of the current request.
     * @param request current Servlet request
     * @return non-blank caller scope
     */
    String resolve(HttpServletRequest request);
}
