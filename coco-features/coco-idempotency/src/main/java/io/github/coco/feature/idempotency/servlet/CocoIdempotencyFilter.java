package io.github.coco.feature.idempotency.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.coco.exception.CocoException;
import io.github.coco.feature.idempotency.CocoIdempotencyErrorCode;
import io.github.coco.feature.idempotency.CocoIdempotencyProperties;
import io.github.coco.feature.idempotency.CocoIdempotencyRouteMatcher;
import io.github.coco.feature.idempotency.CocoIdempotencyScopeResolver;
import io.github.coco.feature.idempotency.store.CocoIdempotencyAcquireResult;
import io.github.coco.feature.idempotency.store.CocoIdempotencyLease;
import io.github.coco.feature.idempotency.store.CocoIdempotencyRequest;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStore;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStoredResponse;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.feature.web.exception.CocoPayloadTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Explicit-route Servlet idempotency filter for bounded synchronous responses.
 *
 * @author patton174
 * @since 1.0.0
 */
public final class CocoIdempotencyFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CocoIdempotencyFilter.class);

    private final CocoIdempotencyProperties properties;

    private final CocoIdempotencyRouteMatcher routeMatcher;

    private final CocoIdempotencyStore store;

    private final CocoIdempotencyScopeResolver scopeResolver;

    private final CocoFilterExceptionResponseWriter exceptionResponseWriter;

    private final Clock clock;

    /**
     * Creates an idempotency filter.
     * @param properties idempotency properties
     * @param routeMatcher explicit route matcher
     * @param store idempotency store
     * @param scopeResolver caller scope resolver
     * @param exceptionResponseWriter Coco exception response writer
     */
    public CocoIdempotencyFilter(CocoIdempotencyProperties properties,
            CocoIdempotencyRouteMatcher routeMatcher, CocoIdempotencyStore store,
            CocoIdempotencyScopeResolver scopeResolver,
            CocoFilterExceptionResponseWriter exceptionResponseWriter) {
        this(properties, routeMatcher, store, scopeResolver, exceptionResponseWriter, Clock.systemUTC());
    }

    /**
     * Creates an idempotency filter with a specific clock.
     * @param properties idempotency properties
     * @param routeMatcher explicit route matcher
     * @param store idempotency store
     * @param scopeResolver caller scope resolver
     * @param exceptionResponseWriter Coco exception response writer
     * @param clock lifecycle clock
     */
    public CocoIdempotencyFilter(CocoIdempotencyProperties properties,
            CocoIdempotencyRouteMatcher routeMatcher, CocoIdempotencyStore store,
            CocoIdempotencyScopeResolver scopeResolver,
            CocoFilterExceptionResponseWriter exceptionResponseWriter, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.routeMatcher = Objects.requireNonNull(routeMatcher, "routeMatcher must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.scopeResolver = Objects.requireNonNull(scopeResolver, "scopeResolver must not be null");
        this.exceptionResponseWriter = Objects.requireNonNull(exceptionResponseWriter,
                "exceptionResponseWriter must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!this.routeMatcher.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (response.isCommitted()) {
            throw new CocoIdempotencyUnsupportedIoException(
                    "Coco idempotency cannot capture an already committed response");
        }

        String key = resolveKey(request, response);
        if (key == null) {
            return;
        }
        String scope = resolveScope(request, response);
        if (scope == null) {
            return;
        }
        byte[] requestBody;
        try {
            requestBody = readRequestBody(request);
        }
        catch (CocoIdempotencyRequestTooLargeException ex) {
            writeError(request, response,
                    new CocoPayloadTooLargeException(CocoIdempotencyErrorCode.REQUEST_TOO_LARGE.messageCode()));
            return;
        }

        String scopeDigest = CocoIdempotencyDigests.scopeDigest(scope);
        String keyHash = CocoIdempotencyDigests.scopedKeyHash(scopeDigest, key);
        String requestHash = CocoIdempotencyDigests.requestHash(request, requestBody);
        CocoIdempotencyRequest idempotencyRequest = new CocoIdempotencyRequest(keyHash, requestHash);
        Instant now = this.clock.instant();
        CocoIdempotencyAcquireResult acquireResult;
        try {
            acquireResult = this.store.acquire(idempotencyRequest, now,
                    now.plusSeconds(this.properties.getTtlSeconds()));
        }
        catch (RuntimeException ex) {
            LOGGER.warn("Coco idempotency acquire failed: keyHashPrefix={}, requestHashPrefix={}, error={}",
                    CocoIdempotencyDigests.prefix(keyHash), CocoIdempotencyDigests.prefix(requestHash),
                    ex.getClass().getSimpleName());
            writeError(request, response, CocoIdempotencyErrorCode.STORE_UNAVAILABLE.system());
            return;
        }

        switch (acquireResult.status()) {
            case IN_PROGRESS -> writeError(request, response, CocoIdempotencyErrorCode.IN_PROGRESS.conflict());
            case PAYLOAD_MISMATCH -> writeError(request, response, CocoIdempotencyErrorCode.PAYLOAD_MISMATCH.request());
            case CAPACITY_EXCEEDED -> writeError(request, response, CocoIdempotencyErrorCode.CAPACITY_EXCEEDED.system());
            case REPLAY -> replaySafely(request, response, acquireResult.response().orElseThrow());
            case ACQUIRED -> executeAcquired(request, response, filterChain, requestBody,
                    acquireResult.lease().orElseThrow());
            default -> throw new IllegalStateException("Unsupported Coco idempotency acquire status");
        }
    }

    private void executeAcquired(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain, byte[] requestBody, CocoIdempotencyLease lease)
            throws ServletException, IOException {
        CocoIdempotencyResponseWrapper responseWrapper;
        try {
            responseWrapper = new CocoIdempotencyResponseWrapper(response, this.properties);
        }
        catch (CocoIdempotencyUnsafeResponseHeaderException ex) {
            release(lease, "unsafe-initial-header");
            writeError(request, response, CocoIdempotencyErrorCode.UNSAFE_RESPONSE_HEADER.system());
            return;
        }
        catch (CocoIdempotencyResponseHeadersTooLargeException ex) {
            release(lease, "initial-headers-too-large");
            writeError(request, response, CocoIdempotencyErrorCode.RESPONSE_HEADERS_TOO_LARGE.system());
            return;
        }
        CocoIdempotencyRequestWrapper requestWrapper = new CocoIdempotencyRequestWrapper(request, requestBody);
        CocoIdempotencyStoredResponse storedResponse;
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
            requestWrapper.requireSynchronous();
            responseWrapper.requireDelegateUncommitted();
            storedResponse = responseWrapper.snapshot();
        }
        catch (CocoIdempotencyResponseTooLargeException ex) {
            release(lease, "response-too-large");
            writeError(request, response, CocoIdempotencyErrorCode.RESPONSE_TOO_LARGE.system());
            return;
        }
        catch (CocoIdempotencyResponseHeadersTooLargeException ex) {
            release(lease, "response-headers-too-large");
            writeError(request, response, CocoIdempotencyErrorCode.RESPONSE_HEADERS_TOO_LARGE.system());
            return;
        }
        catch (CocoIdempotencyUnsafeResponseHeaderException ex) {
            release(lease, "unsafe-response-header");
            writeError(request, response, CocoIdempotencyErrorCode.UNSAFE_RESPONSE_HEADER.system());
            return;
        }
        catch (CocoIdempotencyUnsupportedIoException ex) {
            release(lease, "unsupported-lifecycle");
            if (response.isCommitted()) {
                throw ex;
            }
            writeError(request, response, CocoIdempotencyErrorCode.UNSUPPORTED_IO.system());
            return;
        }
        catch (IOException | ServletException ex) {
            release(lease, "downstream-failure");
            throw ex;
        }
        catch (RuntimeException | Error ex) {
            release(lease, "downstream-failure");
            throw ex;
        }
        catch (Throwable ex) {
            release(lease, "downstream-failure");
            throw new ServletException(ex);
        }

        try {
            if (!this.store.complete(lease, storedResponse, this.clock.instant())) {
                release(lease, "lease-completion-rejected");
                writeError(request, response, CocoIdempotencyErrorCode.STORE_UNAVAILABLE.system());
                return;
            }
        }
        catch (RuntimeException ex) {
            release(lease, "completion-failure");
            LOGGER.warn("Coco idempotency completion failed: error={}", ex.getClass().getSimpleName());
            writeError(request, response, CocoIdempotencyErrorCode.STORE_UNAVAILABLE.system());
            return;
        }
        responseWrapper.commitToDelegate(storedResponse);
    }

    private String resolveKey(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Enumeration<String> values = request.getHeaders(this.properties.getHeaderName());
        List<String> keys = new ArrayList<>(2);
        while (values != null && values.hasMoreElements() && keys.size() < 2) {
            keys.add(values.nextElement());
        }
        if (keys.isEmpty()) {
            writeError(request, response, CocoIdempotencyErrorCode.KEY_REQUIRED.request());
            return null;
        }
        String key = keys.get(0);
        if (keys.size() != 1 || !validKey(key)) {
            writeError(request, response, CocoIdempotencyErrorCode.KEY_INVALID.request());
            return null;
        }
        return key;
    }

    private String resolveScope(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String scope;
        try {
            scope = this.scopeResolver.resolve(request);
        }
        catch (RuntimeException ex) {
            LOGGER.warn("Coco idempotency scope resolution failed: error={}", ex.getClass().getSimpleName());
            writeError(request, response, CocoIdempotencyErrorCode.SCOPE_REQUIRED.unauthorized());
            return null;
        }
        if (scope == null || scope.isBlank()) {
            writeError(request, response, CocoIdempotencyErrorCode.SCOPE_REQUIRED.unauthorized());
            return null;
        }
        return scope;
    }

    private boolean validKey(String key) {
        if (key == null || key.isBlank() || !key.equals(key.trim())
                || key.length() > this.properties.getMaxKeyLength()) {
            return false;
        }
        return key.chars().allMatch(character -> character >= 0x21 && character <= 0x7e && character != ',');
    }

    private byte[] readRequestBody(HttpServletRequest request) throws IOException {
        int maxBytes = this.properties.getMaxRequestBodyBytes();
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBytes) {
            throw new CocoIdempotencyRequestTooLargeException();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                Math.min(maxBytes, contentLength > 0 ? (int) contentLength : 1_024));
        byte[] buffer = new byte[8_192];
        int read;
        int total = 0;
        while ((read = request.getInputStream().read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new CocoIdempotencyRequestTooLargeException();
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void replaySafely(HttpServletRequest request, HttpServletResponse response,
            CocoIdempotencyStoredResponse storedResponse) throws IOException {
        try {
            byte[] body = storedResponse.body();
            if (body.length > this.properties.getMaxResponseBodyBytes()) {
                throw new CocoIdempotencyResponseTooLargeException();
            }
            Map<String, List<String>> headers = CocoIdempotencyResponseHeaders.copy(
                    storedResponse.headers(), this.properties);
            if (response.isCommitted()) {
                throw new CocoIdempotencyUnsupportedIoException(
                        "Cannot replay onto an already committed response");
            }
            response.reset();
            response.setStatus(storedResponse.status());
            CocoIdempotencyResponseHeaders.apply(response, headers);
            response.setContentLength(body.length);
            if (body.length > 0) {
                response.getOutputStream().write(body);
            }
            response.flushBuffer();
        }
        catch (CocoIdempotencyResponseTooLargeException ex) {
            writeError(request, response, CocoIdempotencyErrorCode.RESPONSE_TOO_LARGE.system());
        }
        catch (CocoIdempotencyResponseHeadersTooLargeException ex) {
            writeError(request, response, CocoIdempotencyErrorCode.RESPONSE_HEADERS_TOO_LARGE.system());
        }
        catch (CocoIdempotencyUnsafeResponseHeaderException ex) {
            writeError(request, response, CocoIdempotencyErrorCode.UNSAFE_RESPONSE_HEADER.system());
        }
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
            CocoException exception) throws IOException {
        if (response.isCommitted()) {
            throw exception;
        }
        response.reset();
        this.exceptionResponseWriter.write(exception, request, response);
    }

    private void release(CocoIdempotencyLease lease, String reason) {
        try {
            if (!this.store.fail(lease, this.clock.instant())) {
                LOGGER.debug("Coco idempotency lease was not released: reason={}", reason);
            }
        }
        catch (RuntimeException ex) {
            LOGGER.warn("Coco idempotency lease release failed: reason={}, error={}",
                    reason, ex.getClass().getSimpleName());
        }
    }

}
