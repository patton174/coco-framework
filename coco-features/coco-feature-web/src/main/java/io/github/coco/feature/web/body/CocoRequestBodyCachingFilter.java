package io.github.coco.feature.web.body;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import io.github.coco.feature.web.encryption.CocoEncryptionProperties;
import io.github.coco.feature.web.signature.CocoSignatureProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Coco 请求体缓存过滤器。
 * <p>
 * 在请求进入 Trace 和业务处理前缓存可复读请求体，为后续 AES 解密、Sign 验签和请求体摘要解析提供统一入口。
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
public final class CocoRequestBodyCachingFilter extends OncePerRequestFilter {

    private static final int BUFFER_SIZE = 4096;

    private static final int STATUS_PAYLOAD_TOO_LARGE = 413;

    private final CocoRequestBodyProperties properties;

    private final Set<String> triggerHeaderNames;

    /**
     * <p>
     * 创建 Coco 请求体缓存过滤器。
     * </p>
     * @param properties 请求体缓存配置属性
     */
    public CocoRequestBodyCachingFilter(CocoRequestBodyProperties properties) {
        this(properties, null, null);
    }

    /**
     * <p>
     * 创建 Coco 请求体缓存过滤器。
     * </p>
     * @param properties 请求体缓存配置属性
     * @param signatureProperties 请求签名配置属性
     * @param encryptionProperties 请求加密配置属性
     */
    public CocoRequestBodyCachingFilter(CocoRequestBodyProperties properties,
            CocoSignatureProperties signatureProperties, CocoEncryptionProperties encryptionProperties) {
        this.properties = properties == null ? new CocoRequestBodyProperties() : properties;
        this.triggerHeaderNames = triggerHeaderNames(this.properties, signatureProperties, encryptionProperties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        if (!shouldCache(checkedRequest) || CocoCachedBodyHttpServletRequest.cachedBody(checkedRequest).isPresent()) {
            filterChain.doFilter(checkedRequest, response);
            return;
        }
        if (isKnownOversized(checkedRequest)) {
            response.sendError(STATUS_PAYLOAD_TOO_LARGE, "Coco request body exceeds max cache bytes");
            return;
        }
        try {
            CocoCachedRequestBody cachedBody = readBody(checkedRequest);
            filterChain.doFilter(new CocoCachedBodyHttpServletRequest(checkedRequest, cachedBody), response);
        }
        catch (RequestBodyOverflowException ex) {
            response.sendError(STATUS_PAYLOAD_TOO_LARGE, ex.getMessage());
        }
    }

    private boolean shouldCache(HttpServletRequest request) {
        return this.properties.isEnabled()
                && isCacheMethod(request)
                && isCacheableContentType(request)
                && isTriggered(request);
    }

    private boolean isCacheMethod(HttpServletRequest request) {
        String method = request.getMethod();
        return method != null && this.properties.getCacheMethods().contains(method.trim().toUpperCase(Locale.ROOT));
    }

    private boolean isCacheableContentType(HttpServletRequest request) {
        String contentType = normalizeMediaType(request.getContentType());
        if (contentType == null) {
            return false;
        }
        for (String excludedPrefix : this.properties.getExcludedContentTypePrefixes()) {
            if (contentType.startsWith(excludedPrefix)) {
                return false;
            }
        }
        for (String includedContentType : this.properties.getIncludedContentTypes()) {
            if (matchesMediaType(includedContentType, contentType)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTriggered(HttpServletRequest request) {
        if (CocoRequestBodyCachingMode.ALWAYS.equals(this.properties.getMode())) {
            return true;
        }
        for (String headerName : this.triggerHeaderNames) {
            String value = request.getHeader(headerName);
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> triggerHeaderNames(CocoRequestBodyProperties properties,
            CocoSignatureProperties signatureProperties, CocoEncryptionProperties encryptionProperties) {
        LinkedHashSet<String> headerNames = new LinkedHashSet<>(properties.getTriggerHeaderNames());
        CocoSignatureProperties signature = signatureProperties == null
                ? new CocoSignatureProperties()
                : signatureProperties;
        CocoEncryptionProperties encryption = encryptionProperties == null
                ? new CocoEncryptionProperties()
                : encryptionProperties;
        add(headerNames, signature.getSignatureHeaderName());
        add(headerNames, signature.getSignatureFallbackHeaderName());
        add(headerNames, encryption.getEncryptedHeaderName());
        return Set.copyOf(headerNames);
    }

    private static void add(Set<String> headerNames, String headerName) {
        String normalizedName = headerName == null || headerName.isBlank()
                ? null
                : headerName.trim().toLowerCase(Locale.ROOT);
        if (normalizedName != null) {
            headerNames.add(normalizedName);
        }
    }

    private boolean isKnownOversized(HttpServletRequest request) {
        long contentLength = request.getContentLengthLong();
        return contentLength > this.properties.getMaxCacheBytes();
    }

    private CocoCachedRequestBody readBody(HttpServletRequest request) throws IOException {
        int maxCacheBytes = this.properties.getMaxCacheBytes();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(Math.min(maxCacheBytes, BUFFER_SIZE));
        ServletInputStream inputStream = request.getInputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int totalBytes = 0;
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            totalBytes += bytesRead;
            if (totalBytes > maxCacheBytes) {
                throw new RequestBodyOverflowException("Coco request body exceeds max cache bytes");
            }
            outputStream.write(buffer, 0, bytesRead);
        }
        return CocoCachedRequestBody.cached(outputStream.toByteArray());
    }

    private static boolean matchesMediaType(String pattern, String contentType) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        if ("*/*".equals(pattern)) {
            return true;
        }
        int wildcardIndex = pattern.indexOf('*');
        if (wildcardIndex < 0) {
            return contentType.equals(pattern);
        }
        String prefix = pattern.substring(0, wildcardIndex);
        String suffix = pattern.substring(wildcardIndex + 1);
        return contentType.startsWith(prefix) && contentType.endsWith(suffix);
    }

    private static String normalizeMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        int parameterIndex = contentType.indexOf(';');
        String value = parameterIndex < 0 ? contentType : contentType.substring(0, parameterIndex);
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class RequestBodyOverflowException extends IOException {

        private RequestBodyOverflowException(String message) {
            super(message);
        }
    }
}
