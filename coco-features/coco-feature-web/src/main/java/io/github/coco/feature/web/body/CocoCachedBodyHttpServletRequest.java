package io.github.coco.feature.web.body;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * Coco 可复读请求体 Servlet 请求包装器。
 * <p>
 * 使用已缓存请求体替换原始输入流，使签名、解密和业务层都可以读取同一份请求体内容。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoCachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    /**
     * 已缓存请求体请求属性名。
     */
    public static final String ATTRIBUTE_NAME = CocoCachedBodyHttpServletRequest.class.getName()
            + ".cachedRequestBody";

    /**
     * 传输态请求体属性名。
     */
    public static final String TRANSPORT_ATTRIBUTE_NAME = CocoCachedBodyHttpServletRequest.class.getName()
            + ".transportRequestBody";

    /**
     * 业务态请求体属性名。
     */
    public static final String EFFECTIVE_ATTRIBUTE_NAME = CocoCachedBodyHttpServletRequest.class.getName()
            + ".effectiveRequestBody";

    private final CocoCachedRequestBody cachedRequestBody;

    /**
     * <p>
     * 创建可复读请求体 Servlet 请求包装器。
     * </p>
     * @param request 原始请求
     * @param cachedRequestBody 已缓存请求体
     */
    public CocoCachedBodyHttpServletRequest(HttpServletRequest request,
            CocoCachedRequestBody cachedRequestBody) {
        super(Objects.requireNonNull(request, "request must not be null"));
        this.cachedRequestBody = cachedRequestBody == null ? CocoCachedRequestBody.empty() : cachedRequestBody;
        setAttribute(ATTRIBUTE_NAME, this.cachedRequestBody);
        setAttribute(EFFECTIVE_ATTRIBUTE_NAME, this.cachedRequestBody);
        if (this.cachedRequestBody.cached() && cachedAttribute(request, TRANSPORT_ATTRIBUTE_NAME).isEmpty()) {
            setAttribute(TRANSPORT_ATTRIBUTE_NAME, this.cachedRequestBody);
        }
    }

    /**
     * <p>
     * 从当前请求中读取已缓存请求体。
     * </p>
     * @param request 当前请求
     * @return 已缓存请求体；未缓存时为空
     */
    public static Optional<CocoCachedRequestBody> cachedBody(HttpServletRequest request) {
        return effectiveBody(request);
    }

    /**
     * <p>
     * 从当前请求中读取传输态请求体。
     * </p>
     * @param request 当前请求
     * @return 传输态请求体；未缓存时为空
     */
    public static Optional<CocoCachedRequestBody> transportBody(HttpServletRequest request) {
        return cachedAttribute(request, TRANSPORT_ATTRIBUTE_NAME)
                .or(() -> effectiveBody(request));
    }

    /**
     * <p>
     * 从当前请求中读取业务态请求体。
     * </p>
     * @param request 当前请求
     * @return 业务态请求体；未缓存时为空
     */
    public static Optional<CocoCachedRequestBody> effectiveBody(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        if (request instanceof CocoCachedBodyHttpServletRequest cachedRequest
                && cachedRequest.cachedRequestBody.cached()) {
            return Optional.of(cachedRequest.cachedRequestBody);
        }
        return cachedAttribute(request, EFFECTIVE_ATTRIBUTE_NAME)
                .or(() -> cachedAttribute(request, ATTRIBUTE_NAME));
    }

    /**
     * <p>
     * 清理当前请求上的请求体缓存属性。
     * </p>
     * @param request 当前请求
     */
    public static void clear(HttpServletRequest request) {
        if (request != null) {
            request.removeAttribute(ATTRIBUTE_NAME);
            request.removeAttribute(TRANSPORT_ATTRIBUTE_NAME);
            request.removeAttribute(EFFECTIVE_ATTRIBUTE_NAME);
        }
    }

    private static Optional<CocoCachedRequestBody> cachedAttribute(HttpServletRequest request, String attributeName) {
        if (request == null) {
            return Optional.empty();
        }
        Object attribute = request.getAttribute(attributeName);
        if (attribute instanceof CocoCachedRequestBody cachedBody && cachedBody.cached()) {
            return Optional.of(cachedBody);
        }
        return Optional.empty();
    }

    /**
     * <p>
     * 返回已缓存请求体。
     * </p>
     * @return 已缓存请求体
     */
    public CocoCachedRequestBody cachedRequestBody() {
        return this.cachedRequestBody;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(this.cachedRequestBody.content());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), requestCharset()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getContentLength() {
        long length = getContentLengthLong();
        return length > Integer.MAX_VALUE ? -1 : (int) length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getContentLengthLong() {
        return this.cachedRequestBody.cached() ? this.cachedRequestBody.length() : super.getContentLengthLong();
    }

    private Charset requestCharset() {
        String encoding = getCharacterEncoding();
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding.trim());
        }
        catch (IllegalArgumentException ex) {
            return StandardCharsets.UTF_8;
        }
    }

    private static final class CachedBodyServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream inputStream;

        private CachedBodyServletInputStream(byte[] content) {
            this.inputStream = new ByteArrayInputStream(content == null ? new byte[0] : content);
        }

        @Override
        public int read() {
            return this.inputStream.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return this.inputStream.read(buffer, offset, length);
        }

        @Override
        public boolean isFinished() {
            return this.inputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            Objects.requireNonNull(readListener, "readListener must not be null");
            try {
                if (isFinished()) {
                    readListener.onAllDataRead();
                }
                else {
                    readListener.onDataAvailable();
                }
            }
            catch (IOException ex) {
                readListener.onError(ex);
            }
        }
    }
}
