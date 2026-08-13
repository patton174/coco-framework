package io.github.coco.feature.idempotency.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import io.github.coco.feature.idempotency.CocoIdempotencyProperties;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStoredResponse;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

final class CocoIdempotencyResponseWrapper extends HttpServletResponseWrapper {

    private final HttpServletResponse delegate;

    private final CocoIdempotencyProperties properties;

    private final LimitedBodyOutputStream body;

    private final Map<String, List<String>> headers = new LinkedHashMap<>();

    private ServletOutputStream outputStream;

    private CapturingPrintWriter writer;

    private int status;

    private String characterEncoding;

    private String contentType;

    private Locale locale;

    private int bufferSize;

    private boolean committed;

    private CocoIdempotencyUnsupportedIoException unsupportedFailure;

    private RuntimeException rejectedFailure;

    CocoIdempotencyResponseWrapper(HttpServletResponse response, CocoIdempotencyProperties properties) {
        super(response);
        this.delegate = response;
        this.properties = properties;
        this.status = response.getStatus();
        this.characterEncoding = defaultEncoding(response.getCharacterEncoding());
        this.contentType = response.getContentType();
        this.locale = response.getLocale();
        this.bufferSize = response.getBufferSize();
        this.body = new LimitedBodyOutputStream(properties.getMaxResponseBodyBytes());
        Map<String, List<String>> existingHeaders = new LinkedHashMap<>();
        for (String name : response.getHeaderNames()) {
            existingHeaders.put(name, new ArrayList<>(response.getHeaders(name)));
        }
        this.headers.putAll(CocoIdempotencyResponseHeaders.copy(existingHeaders, properties));
    }

    @Override
    public void addCookie(Cookie cookie) {
        throw rejectUnsupported("Cookies are not supported by Coco idempotency v1");
    }

    @Override
    public boolean containsHeader(String name) {
        return existingHeaderName(name) != null;
    }

    @Override
    public String encodeURL(String url) {
        return this.delegate.encodeURL(url);
    }

    @Override
    public String encodeRedirectURL(String url) {
        return this.delegate.encodeRedirectURL(url);
    }

    @Override
    public void sendError(int statusCode, String message) {
        throw rejectUnsupported("sendError is not supported by Coco idempotency v1");
    }

    @Override
    public void sendError(int statusCode) {
        throw rejectUnsupported("sendError is not supported by Coco idempotency v1");
    }

    @Override
    public void sendRedirect(String location) {
        throw rejectUnsupported("sendRedirect is not supported by Coco idempotency v1");
    }

    @Override
    public void setDateHeader(String name, long date) {
        setHeader(name, Long.toString(date));
    }

    @Override
    public void addDateHeader(String name, long date) {
        addHeader(name, Long.toString(date));
    }

    @Override
    public void setHeader(String name, String value) {
        requireNotCommitted();
        Map<String, List<String>> candidate = copyHeaders();
        String existingName = existingHeaderName(candidate, name);
        if (value == null) {
            if (existingName != null) {
                candidate.remove(existingName);
            }
        }
        else {
            if (existingName != null && !existingName.equals(name)) {
                candidate.remove(existingName);
            }
            candidate.put(name, new ArrayList<>(List.of(value)));
        }
        replaceHeaders(candidate);
        if ("content-type".equalsIgnoreCase(name)) {
            this.contentType = value;
        }
    }

    @Override
    public void addHeader(String name, String value) {
        requireNotCommitted();
        if (value == null) {
            return;
        }
        Map<String, List<String>> candidate = copyHeaders();
        String existingName = existingHeaderName(candidate, name);
        String targetName = existingName == null ? name : existingName;
        candidate.computeIfAbsent(targetName, ignored -> new ArrayList<>()).add(value);
        replaceHeaders(candidate);
        if ("content-type".equalsIgnoreCase(name)) {
            this.contentType = value;
        }
    }

    @Override
    public void setIntHeader(String name, int value) {
        setHeader(name, Integer.toString(value));
    }

    @Override
    public void addIntHeader(String name, int value) {
        addHeader(name, Integer.toString(value));
    }

    @Override
    public void setStatus(int statusCode) {
        requireNotCommitted();
        this.status = statusCode;
    }

    @Override
    public int getStatus() {
        return this.status;
    }

    @Override
    public String getHeader(String name) {
        String existingName = existingHeaderName(name);
        if (existingName == null || this.headers.get(existingName).isEmpty()) {
            return null;
        }
        return this.headers.get(existingName).get(0);
    }

    @Override
    public Collection<String> getHeaders(String name) {
        String existingName = existingHeaderName(name);
        return existingName == null ? List.of() : List.copyOf(this.headers.get(existingName));
    }

    @Override
    public Collection<String> getHeaderNames() {
        return List.copyOf(this.headers.keySet());
    }

    @Override
    public String getCharacterEncoding() {
        return this.characterEncoding;
    }

    @Override
    public String getContentType() {
        return this.contentType;
    }

    @Override
    public ServletOutputStream getOutputStream() {
        requireNotCommitted();
        if (this.writer != null) {
            throw new IllegalStateException("getWriter() has already been called");
        }
        if (this.outputStream == null) {
            this.outputStream = new CapturingServletOutputStream(this);
        }
        return this.outputStream;
    }

    @Override
    public PrintWriter getWriter() {
        requireNotCommitted();
        if (this.outputStream != null) {
            throw new IllegalStateException("getOutputStream() has already been called");
        }
        if (this.writer == null) {
            this.writer = new CapturingPrintWriter(
                    new OutputStreamWriter(this.body, Charset.forName(this.characterEncoding)), this);
        }
        return this.writer;
    }

    @Override
    public void setCharacterEncoding(String encoding) {
        requireNotCommitted();
        if (this.writer == null && encoding != null && !encoding.isBlank()) {
            this.characterEncoding = encoding;
        }
    }

    @Override
    public void setContentLength(int length) {
        setContentLengthLong(length);
    }

    @Override
    public void setContentLengthLong(long length) {
        requireNotCommitted();
    }

    @Override
    public void setContentType(String type) {
        setHeader("Content-Type", type);
    }

    @Override
    public void setBufferSize(int size) {
        requireNotCommitted();
        if (this.body.size() > 0) {
            throw new IllegalStateException("Response body has already been written");
        }
        this.bufferSize = size;
    }

    @Override
    public int getBufferSize() {
        return this.bufferSize;
    }

    @Override
    public void flushBuffer() {
        throw rejectUnsupported("flushBuffer is not supported by Coco idempotency v1");
    }

    @Override
    public void resetBuffer() {
        requireResetSupported();
        this.body.reset();
    }

    @Override
    public boolean isCommitted() {
        return this.committed || this.delegate.isCommitted();
    }

    @Override
    public void reset() {
        requireResetSupported();
        this.body.reset();
        this.headers.clear();
        this.status = HttpServletResponse.SC_OK;
        this.characterEncoding = StandardCharsets.ISO_8859_1.name();
        this.contentType = null;
        this.locale = Locale.getDefault();
    }

    @Override
    public void setLocale(Locale locale) {
        requireNotCommitted();
        if (locale != null) {
            this.locale = locale;
        }
    }

    @Override
    public Locale getLocale() {
        return this.locale;
    }

    @Override
    public void setTrailerFields(Supplier<Map<String, String>> supplier) {
        throw rejectUnsupported("Response trailers are not supported by Coco idempotency v1");
    }

    @Override
    public Supplier<Map<String, String>> getTrailerFields() {
        throw rejectUnsupported("Response trailers are not supported by Coco idempotency v1");
    }

    CocoIdempotencyStoredResponse snapshot() throws CocoIdempotencyResponseTooLargeException {
        requireSupported();
        flushWriter();
        this.body.requireWithinLimit();
        return new CocoIdempotencyStoredResponse(this.status,
                CocoIdempotencyResponseHeaders.copy(this.headers, this.properties), this.body.toByteArray());
    }

    void requireDelegateUncommitted() {
        if (this.delegate.isCommitted()) {
            throw rejectUnsupported("Underlying response was committed outside Coco idempotency");
        }
    }

    void commitToDelegate(CocoIdempotencyStoredResponse response) throws IOException {
        requireSupported();
        requireDelegateUncommitted();
        this.delegate.reset();
        this.delegate.setStatus(response.status());
        CocoIdempotencyResponseHeaders.apply(this.delegate,
                CocoIdempotencyResponseHeaders.copy(response.headers(), this.properties));
        byte[] responseBody = response.body();
        this.delegate.setContentLength(responseBody.length);
        if (responseBody.length > 0) {
            this.delegate.getOutputStream().write(responseBody);
        }
        this.delegate.flushBuffer();
        this.committed = true;
    }

    private void replaceHeaders(Map<String, List<String>> candidate) {
        try {
            Map<String, List<String>> checked = CocoIdempotencyResponseHeaders.copy(candidate, this.properties);
            this.headers.clear();
            this.headers.putAll(checked);
        }
        catch (CocoIdempotencyUnsafeResponseHeaderException
                | CocoIdempotencyResponseHeadersTooLargeException ex) {
            this.rejectedFailure = ex;
            throw ex;
        }
    }

    private Map<String, List<String>> copyHeaders() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        this.headers.forEach((name, values) -> copy.put(name, new ArrayList<>(values)));
        return copy;
    }

    private String existingHeaderName(String name) {
        return existingHeaderName(this.headers, name);
    }

    private static String existingHeaderName(Map<String, List<String>> source, String name) {
        if (name == null) {
            return null;
        }
        return source.keySet().stream().filter(candidate -> candidate.equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    private void requireNotCommitted() {
        if (isCommitted()) {
            throw new IllegalStateException("Response is already committed");
        }
    }

    private void requireResetSupported() {
        requireNotCommitted();
        if (this.outputStream != null || this.writer != null) {
            throw rejectUnsupported("reset after response output has been obtained is not supported by Coco idempotency v1");
        }
    }

    private void requireSupported() {
        if (this.rejectedFailure != null) {
            throw this.rejectedFailure;
        }
        if (this.unsupportedFailure != null) {
            throw this.unsupportedFailure;
        }
    }

    private CocoIdempotencyUnsupportedIoException rejectUnsupported(String message) {
        if (this.unsupportedFailure == null) {
            this.unsupportedFailure = new CocoIdempotencyUnsupportedIoException(message);
        }
        return this.unsupportedFailure;
    }

    private void flushWriter() {
        if (this.writer != null) {
            this.writer.flushForSnapshot();
        }
    }

    private static String defaultEncoding(String encoding) {
        return encoding == null || encoding.isBlank() ? StandardCharsets.ISO_8859_1.name() : encoding;
    }

    private static final class LimitedBodyOutputStream extends ByteArrayOutputStream {

        private final int maxBytes;

        private boolean overflowed;

        private LimitedBodyOutputStream(int maxBytes) {
            super(Math.min(Math.max(maxBytes, 0), 1_024));
            this.maxBytes = maxBytes;
        }

        @Override
        public synchronized void write(int value) {
            if (this.count >= this.maxBytes) {
                this.overflowed = true;
                return;
            }
            super.write(value);
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            int available = this.maxBytes - this.count;
            if (length > available) {
                if (available > 0) {
                    super.write(bytes, offset, available);
                }
                this.overflowed = true;
                return;
            }
            super.write(bytes, offset, length);
        }

        private synchronized void requireWithinLimit() throws CocoIdempotencyResponseTooLargeException {
            if (this.overflowed) {
                throw new CocoIdempotencyResponseTooLargeException();
            }
        }

        @Override
        public synchronized void reset() {
            super.reset();
            this.overflowed = false;
        }
    }

    private static final class CapturingPrintWriter extends PrintWriter {

        private final CocoIdempotencyResponseWrapper response;

        private CapturingPrintWriter(OutputStreamWriter writer, CocoIdempotencyResponseWrapper response) {
            super(writer);
            this.response = response;
        }

        @Override
        public void flush() {
            throw this.response.rejectUnsupported("PrintWriter.flush is not supported by Coco idempotency v1");
        }

        @Override
        public void close() {
            throw this.response.rejectUnsupported("PrintWriter.close is not supported by Coco idempotency v1");
        }

        private void flushForSnapshot() {
            super.flush();
        }
    }

    private static final class CapturingServletOutputStream extends ServletOutputStream {

        private final CocoIdempotencyResponseWrapper response;

        private CapturingServletOutputStream(CocoIdempotencyResponseWrapper response) {
            this.response = response;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            throw this.response.rejectUnsupported(
                    "Non-blocking response output is not supported by Coco idempotency v1");
        }

        @Override
        public void write(int value) throws IOException {
            this.response.body.write(value);
            this.response.body.requireWithinLimit();
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            this.response.body.write(bytes, offset, length);
            this.response.body.requireWithinLimit();
        }

        @Override
        public void flush() {
            throw this.response.rejectUnsupported("ServletOutputStream.flush is not supported by Coco idempotency v1");
        }

        @Override
        public void close() {
            throw this.response.rejectUnsupported("ServletOutputStream.close is not supported by Coco idempotency v1");
        }
    }
}
