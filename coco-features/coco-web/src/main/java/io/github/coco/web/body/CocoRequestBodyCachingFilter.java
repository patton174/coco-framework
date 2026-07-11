package io.github.coco.web.body;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import io.github.coco.web.context.CocoWebRequestMatcher;
import io.github.coco.web.request.metadata.CocoWebSecurityMetadataSource;
import io.github.coco.web.context.DefaultCocoWebRequestMatcher;
import io.github.coco.web.encryption.CocoEncryptionProperties;
import io.github.coco.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.web.exception.CocoPayloadTooLargeException;
import io.github.coco.web.replay.CocoReplayProperties;
import io.github.coco.web.signature.CocoSignatureProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Coco 请求体缓存过滤器�? * <p>
 * 在请求进�?Trace 和业务处理前缓存可复读请求体，为后续 AES 解密、Sign 验签、防重放和请求体摘要解析提供统一入口�? * </p>
 * <p>
 * 项目信息�? * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库�?a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoRequestBodyCachingFilter extends OncePerRequestFilter {

    private static final int BUFFER_SIZE = 4096;

    private static final String PAYLOAD_TOO_LARGE_CODE = "coco.web.request-body.payload-too-large";

    private static final String FORM_URLENCODED = "application/x-www-form-urlencoded";

    private final CocoRequestBodyProperties properties;

    private final Set<String> triggerHeaderNames;

    private final Set<String> triggerParameterNames;

    private final CocoSignatureProperties signatureProperties;

    private final CocoEncryptionProperties encryptionProperties;

    private final CocoReplayProperties replayProperties;

    private final CocoWebRequestMatcher requestMatcher;

    private final CocoFilterExceptionResponseWriter exceptionResponseWriter;

    /**
     * <p>
     * 创建 Coco 请求体缓存过滤器�?     * </p>
     * @param properties 请求体缓存配置属�?     */
    public CocoRequestBodyCachingFilter(CocoRequestBodyProperties properties) {
        this(properties, null, null, null, null, null);
    }

    /**
     * <p>
     * 创建 Coco 请求体缓存过滤器�?     * </p>
     * @param properties 请求体缓存配置属�?     * @param signatureProperties 请求签名配置属�?     * @param encryptionProperties 请求加密配置属�?     */
    public CocoRequestBodyCachingFilter(CocoRequestBodyProperties properties,
            CocoSignatureProperties signatureProperties, CocoEncryptionProperties encryptionProperties) {
        this(properties, signatureProperties, encryptionProperties, null, null, null);
    }

    /**
     * <p>
     * 创建 Coco 请求体缓存过滤器�?     * </p>
     * @param properties 请求体缓存配置属�?     * @param signatureProperties 请求签名配置属�?     * @param encryptionProperties 请求加密配置属�?     * @param replayProperties 防重放配置属�?     */
    public CocoRequestBodyCachingFilter(CocoRequestBodyProperties properties,
            CocoSignatureProperties signatureProperties, CocoEncryptionProperties encryptionProperties,
            CocoReplayProperties replayProperties) {
        this(properties, signatureProperties, encryptionProperties, replayProperties, null, null);
    }

    /**
     * <p>
     * 创建 Coco 请求体缓存过滤器�?     * </p>
     * @param properties 请求体缓存配置属�?     * @param signatureProperties 请求签名配置属�?     * @param encryptionProperties 请求加密配置属�?     * @param exceptionResponseWriter 过滤器异常响应写出器
     */
    public CocoRequestBodyCachingFilter(CocoRequestBodyProperties properties,
            CocoSignatureProperties signatureProperties, CocoEncryptionProperties encryptionProperties,
            CocoFilterExceptionResponseWriter exceptionResponseWriter) {
        this(properties, signatureProperties, encryptionProperties, null, exceptionResponseWriter, null);
    }

    /**
     * <p>
     * 创建 Coco 请求体缓存过滤器�?     * </p>
     * @param properties 请求体缓存配置属�?     * @param signatureProperties 请求签名配置属�?     * @param encryptionProperties 请求加密配置属�?     * @param replayProperties 防重放配置属�?     * @param exceptionResponseWriter 过滤器异常响应写出器
     */
    public CocoRequestBodyCachingFilter(CocoRequestBodyProperties properties,
            CocoSignatureProperties signatureProperties, CocoEncryptionProperties encryptionProperties,
            CocoReplayProperties replayProperties, CocoFilterExceptionResponseWriter exceptionResponseWriter) {
        this(properties, signatureProperties, encryptionProperties, replayProperties, exceptionResponseWriter, null);
    }

    /**
     * <p>
     * 创建 Coco 请求体缓存过滤器�?     * </p>
     * @param properties 请求体缓存配置属�?     * @param signatureProperties 请求签名配置属�?     * @param encryptionProperties 请求加密配置属�?     * @param exceptionResponseWriter 过滤器异常响应写出器
     * @param requestMatcher Web 请求匹配�?     */
    public CocoRequestBodyCachingFilter(CocoRequestBodyProperties properties,
            CocoSignatureProperties signatureProperties, CocoEncryptionProperties encryptionProperties,
            CocoFilterExceptionResponseWriter exceptionResponseWriter, CocoWebRequestMatcher requestMatcher) {
        this(properties, signatureProperties, encryptionProperties, null, exceptionResponseWriter, requestMatcher);
    }

    /**
     * <p>
     * 创建 Coco 请求体缓存过滤器�?     * </p>
     * @param properties 请求体缓存配置属�?     * @param signatureProperties 请求签名配置属�?     * @param encryptionProperties 请求加密配置属�?     * @param replayProperties 防重放配置属�?     * @param exceptionResponseWriter 过滤器异常响应写出器
     * @param requestMatcher Web 请求匹配�?     */
    public CocoRequestBodyCachingFilter(CocoRequestBodyProperties properties,
            CocoSignatureProperties signatureProperties, CocoEncryptionProperties encryptionProperties,
            CocoReplayProperties replayProperties, CocoFilterExceptionResponseWriter exceptionResponseWriter,
            CocoWebRequestMatcher requestMatcher) {
        this.properties = properties == null ? new CocoRequestBodyProperties() : properties;
        this.signatureProperties = signatureProperties == null ? new CocoSignatureProperties() : signatureProperties;
        this.encryptionProperties = encryptionProperties == null ? new CocoEncryptionProperties() : encryptionProperties;
        this.replayProperties = replayProperties == null ? new CocoReplayProperties() : replayProperties;
        this.triggerHeaderNames = triggerHeaderNames(this.properties, this.signatureProperties,
                this.encryptionProperties, this.replayProperties);
        this.triggerParameterNames = triggerParameterNames(this.signatureProperties, this.encryptionProperties,
                this.replayProperties);
        this.requestMatcher = requestMatcher == null ? new DefaultCocoWebRequestMatcher() : requestMatcher;
        this.exceptionResponseWriter = exceptionResponseWriter;
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
            writePayloadTooLarge(checkedRequest, response);
            return;
        }
        try {
            CocoCachedRequestBody cachedBody = readBody(checkedRequest);
            filterChain.doFilter(new CocoCachedBodyHttpServletRequest(checkedRequest, cachedBody), response);
        }
        catch (RequestBodyOverflowException ex) {
            writePayloadTooLarge(checkedRequest, response);
        }
    }

    private void writePayloadTooLarge(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        CocoPayloadTooLargeException exception = new CocoPayloadTooLargeException(PAYLOAD_TOO_LARGE_CODE);
        if (this.exceptionResponseWriter == null) {
            throw exception;
        }
        this.exceptionResponseWriter.write(exception, request, response);
    }

    private boolean shouldCache(HttpServletRequest request) {
        if (!this.properties.isEnabled() || !isCacheMethod(request) || isExcludedContentType(request)) {
            return false;
        }
        if (isSecurityTriggerPresent(request)) {
            return true;
        }
        if (securityCapabilityRequiresBodyCaching(request)) {
            return true;
        }
        if (parameterMetadataRequiresBodyCaching(request)) {
            return true;
        }
        return CocoRequestBodyCachingMode.ALWAYS.equals(this.properties.getMode())
                && isCacheableContentType(request);
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
        for (String includedContentType : this.properties.getIncludedContentTypes()) {
            if (matchesMediaType(includedContentType, contentType)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExcludedContentType(HttpServletRequest request) {
        String contentType = normalizeMediaType(request.getContentType());
        if (contentType == null) {
            return false;
        }
        for (String excludedPrefix : this.properties.getExcludedContentTypePrefixes()) {
            if (contentType.startsWith(excludedPrefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSecurityTriggerPresent(HttpServletRequest request) {
        return isTriggerHeaderPresent(request) || isTriggerParameterPresent(request);
    }

    private boolean isTriggerHeaderPresent(HttpServletRequest request) {
        for (String headerName : this.triggerHeaderNames) {
            String value = request.getHeader(headerName);
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private boolean isTriggerParameterPresent(HttpServletRequest request) {
        if (this.triggerParameterNames.isEmpty()) {
            return false;
        }
        String queryString = request.getQueryString();
        if (queryString == null || queryString.isBlank()) {
            return false;
        }
        for (String parameterName : queryParameterNames(queryString)) {
            if (this.triggerParameterNames.contains(parameterName)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> queryParameterNames(String queryString) {
        try {
            Set<String> parameterNames = new LinkedHashSet<>();
            for (String parameterName : UriComponentsBuilder.newInstance()
                    .query(queryString)
                    .build()
                    .getQueryParams()
                    .keySet()) {
                parameterNames.add(URLDecoder.decode(parameterName, StandardCharsets.UTF_8));
            }
            return parameterNames;
        }
        catch (IllegalArgumentException ex) {
            return rawQueryParameterNames(queryString);
        }
    }

    private static Set<String> rawQueryParameterNames(String queryString) {
        Set<String> parameterNames = new LinkedHashSet<>();
        for (String pair : queryString.split("&", -1)) {
            int separatorIndex = pair.indexOf('=');
            parameterNames.add(separatorIndex < 0 ? pair : pair.substring(0, separatorIndex));
        }
        return parameterNames;
    }

    private boolean securityCapabilityRequiresBodyCaching(HttpServletRequest request) {
        return signatureRequiresBodyCaching(request) || encryptionRequiresBodyCaching(request);
    }

    private boolean parameterMetadataRequiresBodyCaching(HttpServletRequest request) {
        if (!parameterMetadataMayAppearInBody(request)) {
            return false;
        }
        return isFormUrlencodedRequest(request) || isCacheableContentType(request);
    }

    private boolean signatureRequiresBodyCaching(HttpServletRequest request) {
        if (!this.signatureProperties.isEnabled()
                || this.requestMatcher.matches(request, this.signatureProperties.getMatcher().getIgnored())) {
            return false;
        }
        return this.signatureProperties.isRequired()
                || this.requestMatcher.matches(request, this.signatureProperties.getMatcher().getRequired());
    }

    private boolean encryptionRequiresBodyCaching(HttpServletRequest request) {
        if (!this.encryptionProperties.isEnabled()
                || this.requestMatcher.matches(request, this.encryptionProperties.getMatcher().getIgnored())) {
            return false;
        }
        return this.encryptionProperties.isRequired()
                || this.requestMatcher.matches(request, this.encryptionProperties.getMatcher().getRequired());
    }

    private boolean signatureParameterMetadataMayAppearInBody(HttpServletRequest request) {
        return this.signatureProperties.isEnabled()
                && this.signatureProperties.getMetadataSource().supportsParameter()
                && !this.requestMatcher.matches(request, this.signatureProperties.getMatcher().getIgnored());
    }

    private boolean replayParameterMetadataMayAppearInBody(HttpServletRequest request) {
        return this.replayProperties.isEnabled()
                && this.replayProperties.getMetadataSource().supportsParameter()
                && !this.requestMatcher.matches(request, this.replayProperties.getMatcher().getIgnored());
    }

    private boolean encryptionParameterMetadataMayAppearInBody(HttpServletRequest request) {
        return this.encryptionProperties.isEnabled()
                && this.encryptionProperties.getMetadataSource().supportsParameter()
                && !this.requestMatcher.matches(request, this.encryptionProperties.getMatcher().getIgnored());
    }

    private boolean parameterMetadataMayAppearInBody(HttpServletRequest request) {
        return signatureParameterMetadataMayAppearInBody(request)
                || encryptionParameterMetadataMayAppearInBody(request)
                || replayParameterMetadataMayAppearInBody(request);
    }

    private static Set<String> triggerHeaderNames(CocoRequestBodyProperties properties,
            CocoSignatureProperties signatureProperties, CocoEncryptionProperties encryptionProperties,
            CocoReplayProperties replayProperties) {
        LinkedHashSet<String> headerNames = new LinkedHashSet<>(properties.getTriggerHeaderNames());
        add(headerNames, signatureProperties.getSignatureHeaderName());
        add(headerNames, signatureProperties.getSignatureFallbackHeaderName());
        add(headerNames, encryptionProperties.getEncryptedHeaderName());
        addIfHeaderSource(headerNames, replayProperties.getMetadataSource(), replayProperties.getAppIdHeaderName());
        addIfHeaderSource(headerNames, replayProperties.getMetadataSource(), replayProperties.getKeyIdHeaderName());
        addIfHeaderSource(headerNames, replayProperties.getMetadataSource(), replayProperties.getTimestampHeaderName());
        addIfHeaderSource(headerNames, replayProperties.getMetadataSource(), replayProperties.getNonceHeaderName());
        return Set.copyOf(headerNames);
    }

    private static Set<String> triggerParameterNames(CocoSignatureProperties signatureProperties,
            CocoEncryptionProperties encryptionProperties, CocoReplayProperties replayProperties) {
        LinkedHashSet<String> parameterNames = new LinkedHashSet<>();
        addIfParameterSource(parameterNames, signatureProperties.getMetadataSource(),
                signatureProperties.getSignatureParameterName());
        addIfParameterSource(parameterNames, signatureProperties.getMetadataSource(),
                signatureProperties.getSignatureFallbackParameterName());
        addIfParameterSource(parameterNames, encryptionProperties.getMetadataSource(),
                encryptionProperties.getEncryptedParameterName());
        addIfParameterSource(parameterNames, replayProperties.getMetadataSource(),
                replayProperties.getAppIdParameterName());
        addIfParameterSource(parameterNames, replayProperties.getMetadataSource(),
                replayProperties.getKeyIdParameterName());
        addIfParameterSource(parameterNames, replayProperties.getMetadataSource(),
                replayProperties.getTimestampParameterName());
        addIfParameterSource(parameterNames, replayProperties.getMetadataSource(),
                replayProperties.getNonceParameterName());
        return Set.copyOf(parameterNames);
    }

    private static void addIfHeaderSource(Set<String> headerNames, CocoWebSecurityMetadataSource source,
            String headerName) {
        CocoWebSecurityMetadataSource metadataSource = source == null ? CocoWebSecurityMetadataSource.HEADER : source;
        if (metadataSource.supportsHeader()) {
            add(headerNames, headerName);
        }
    }

    private static void addIfParameterSource(Set<String> parameterNames, CocoWebSecurityMetadataSource source,
            String parameterName) {
        CocoWebSecurityMetadataSource metadataSource = source == null ? CocoWebSecurityMetadataSource.HEADER : source;
        if (metadataSource.supportsParameter()) {
            addParameter(parameterNames, parameterName);
        }
    }

    private static void addParameter(Set<String> parameterNames, String parameterName) {
        String normalizedName = parameterName == null || parameterName.isBlank() ? null : parameterName.trim();
        if (normalizedName != null) {
            parameterNames.add(normalizedName);
        }
    }

    private static void add(Set<String> headerNames, String headerName) {
        String normalizedName = headerName == null || headerName.isBlank()
                ? null
                : headerName.trim().toLowerCase(Locale.ROOT);
        if (normalizedName != null) {
            headerNames.add(normalizedName);
        }
    }

    private static boolean isFormUrlencodedRequest(HttpServletRequest request) {
        return FORM_URLENCODED.equals(normalizeMediaType(request.getContentType()));
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
                throw new RequestBodyOverflowException(PAYLOAD_TOO_LARGE_CODE);
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
