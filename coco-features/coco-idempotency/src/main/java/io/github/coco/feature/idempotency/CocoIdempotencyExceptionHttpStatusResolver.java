package io.github.coco.feature.idempotency;

import java.util.Map;
import java.util.Objects;

import io.github.coco.exception.CocoException;
import io.github.coco.feature.web.exception.CocoExceptionHttpStatusResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Adds the idempotency error status contract to Coco's standard exception resolver.
 * <p>
 * The delegate remains responsible for all non-idempotency exceptions, so an application can replace the
 * resolver with its own policy without changing the response writer contract.
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
public final class CocoIdempotencyExceptionHttpStatusResolver implements CocoExceptionHttpStatusResolver {

    private static final Map<String, HttpStatusCode> IDEMPOTENCY_STATUSES = Map.ofEntries(
            Map.entry(CocoIdempotencyErrorCode.SCOPE_REQUIRED.messageCode(), HttpStatus.UNAUTHORIZED),
            Map.entry(CocoIdempotencyErrorCode.KEY_REQUIRED.messageCode(), HttpStatus.BAD_REQUEST),
            Map.entry(CocoIdempotencyErrorCode.KEY_INVALID.messageCode(), HttpStatus.BAD_REQUEST),
            Map.entry(CocoIdempotencyErrorCode.IN_PROGRESS.messageCode(), HttpStatus.CONFLICT),
            Map.entry(CocoIdempotencyErrorCode.PAYLOAD_MISMATCH.messageCode(), HttpStatusCode.valueOf(422)),
            Map.entry(CocoIdempotencyErrorCode.REQUEST_TOO_LARGE.messageCode(), HttpStatusCode.valueOf(413)),
            Map.entry(CocoIdempotencyErrorCode.RESPONSE_TOO_LARGE.messageCode(), HttpStatus.INTERNAL_SERVER_ERROR),
            Map.entry(CocoIdempotencyErrorCode.RESPONSE_HEADERS_TOO_LARGE.messageCode(), HttpStatus.INTERNAL_SERVER_ERROR),
            Map.entry(CocoIdempotencyErrorCode.UNSAFE_RESPONSE_HEADER.messageCode(), HttpStatus.INTERNAL_SERVER_ERROR),
            Map.entry(CocoIdempotencyErrorCode.UNSUPPORTED_IO.messageCode(), HttpStatus.INTERNAL_SERVER_ERROR),
            Map.entry(CocoIdempotencyErrorCode.CAPACITY_EXCEEDED.messageCode(), HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry(CocoIdempotencyErrorCode.STORE_UNAVAILABLE.messageCode(), HttpStatus.SERVICE_UNAVAILABLE));

    private final CocoExceptionHttpStatusResolver delegate;

    /**
     * Creates an idempotency-aware resolver.
     * @param delegate resolver for non-idempotency exceptions
     */
    public CocoIdempotencyExceptionHttpStatusResolver(CocoExceptionHttpStatusResolver delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpStatusCode resolve(CocoException exception) {
        CocoException checkedException = Objects.requireNonNull(exception, "exception must not be null");
        HttpStatusCode status = IDEMPOTENCY_STATUSES.get(checkedException.messageCode());
        return status == null ? this.delegate.resolve(checkedException) : status;
    }
}
