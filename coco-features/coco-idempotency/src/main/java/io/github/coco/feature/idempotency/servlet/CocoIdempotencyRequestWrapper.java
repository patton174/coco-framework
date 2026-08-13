package io.github.coco.feature.idempotency.servlet;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

final class CocoIdempotencyRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] body;

    private CocoIdempotencyUnsupportedIoException unsupportedFailure;

    CocoIdempotencyRequestWrapper(HttpServletRequest request, byte[] body) {
        super(request);
        this.body = body.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedServletInputStream(this.body);
    }

    @Override
    public BufferedReader getReader() {
        String encoding = getCharacterEncoding();
        Charset charset = encoding == null || encoding.isBlank()
                ? StandardCharsets.UTF_8
                : Charset.forName(encoding);
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    @Override
    public int getContentLength() {
        return this.body.length;
    }

    @Override
    public long getContentLengthLong() {
        return this.body.length;
    }

    @Override
    public boolean isAsyncSupported() {
        return false;
    }

    @Override
    public jakarta.servlet.AsyncContext startAsync() {
        throw rejectUnsupported("Async Servlet execution is not supported by Coco idempotency v1");
    }

    @Override
    public jakarta.servlet.AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) {
        throw rejectUnsupported("Async Servlet execution is not supported by Coco idempotency v1");
    }

    @Override
    public jakarta.servlet.AsyncContext getAsyncContext() {
        throw rejectUnsupported("Async Servlet execution is not supported by Coco idempotency v1");
    }

    void requireSynchronous() {
        if (this.unsupportedFailure != null) {
            throw this.unsupportedFailure;
        }
    }

    private CocoIdempotencyUnsupportedIoException rejectUnsupported(String message) {
        CocoIdempotencyUnsupportedIoException failure = new CocoIdempotencyUnsupportedIoException(message);
        this.unsupportedFailure = failure;
        return failure;
    }

    private final class CachedServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream input;

        private CachedServletInputStream(byte[] body) {
            this.input = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            return this.input.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw CocoIdempotencyRequestWrapper.this.rejectUnsupported(
                    "Non-blocking request input is not supported by Coco idempotency v1");
        }

        @Override
        public int read() {
            return this.input.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            return this.input.read(bytes, offset, length);
        }
    }
}
