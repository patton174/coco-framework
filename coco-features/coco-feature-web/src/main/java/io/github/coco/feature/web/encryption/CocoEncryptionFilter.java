package io.github.coco.feature.web.encryption;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import io.github.coco.common.context.CocoRequestContext;
import io.github.coco.common.context.CocoRequestContextHolder;
import io.github.coco.common.exception.CocoBusinessExceptions;
import io.github.coco.common.exception.CocoException;
import io.github.coco.common.trace.CocoTraceContext;
import io.github.coco.feature.web.body.CocoCachedBodyHttpServletRequest;
import io.github.coco.feature.web.body.CocoCachedRequestBody;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import io.github.coco.feature.web.context.CocoWebRequestSecurityMetadata;
import io.github.coco.feature.web.context.CocoWebRequestSecurityMetadataResolver;
import io.github.coco.feature.web.context.CocoWebRequestSecurityInput;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.feature.web.context.CocoWebRequestSnapshotAttributes;
import io.github.coco.feature.web.context.CocoWebSecurityMetadataSource;
import io.github.coco.feature.web.context.DefaultCocoWebRequestMatcher;
import io.github.coco.feature.web.context.DefaultCocoWebRequestSecurityMetadataResolver;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Coco 请求解密过滤器。
 * <p>
 * 在业务处理前根据 AES 请求头解密请求体，并使用可复读请求体包装器把明文交给后续 Controller。
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
public final class CocoEncryptionFilter extends OncePerRequestFilter {

    private final CocoEncryptionProperties properties;

    private final CocoEncryptionKeyResolver keyResolver;

    private final CocoRequestDecryptor requestDecryptor;

    private final CocoWebRequestContextResolver requestContextResolver;

    private final CocoWebRequestMatcher requestMatcher;

    private final CocoWebRequestSecurityMetadataResolver securityMetadataResolver;

    private final CocoFilterExceptionResponseWriter exceptionResponseWriter;

    /**
     * <p>
     * 创建 Coco 请求解密过滤器。
     * </p>
     * @param properties 请求加密配置属性
     * @param keyResolver AES 解密密钥解析器
     * @param requestDecryptor 请求解密器
     * @param requestContextResolver Web 请求上下文解析器
     * @param exceptionResponseWriter 过滤器异常响应写出器
     */
    public CocoEncryptionFilter(CocoEncryptionProperties properties, CocoEncryptionKeyResolver keyResolver,
            CocoRequestDecryptor requestDecryptor, CocoWebRequestContextResolver requestContextResolver,
            CocoFilterExceptionResponseWriter exceptionResponseWriter) {
        this(properties, keyResolver, requestDecryptor, requestContextResolver, exceptionResponseWriter, null);
    }

    /**
     * <p>
     * 创建 Coco 请求解密过滤器。
     * </p>
     * @param properties 请求加密配置属性
     * @param keyResolver AES 解密密钥解析器
     * @param requestDecryptor 请求解密器
     * @param requestContextResolver Web 请求上下文解析器
     * @param exceptionResponseWriter 过滤器异常响应写出器
     * @param securityMetadataResolver 请求安全元数据解析器
     */
    public CocoEncryptionFilter(CocoEncryptionProperties properties, CocoEncryptionKeyResolver keyResolver,
            CocoRequestDecryptor requestDecryptor, CocoWebRequestContextResolver requestContextResolver,
            CocoFilterExceptionResponseWriter exceptionResponseWriter,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver) {
        this(properties, keyResolver, requestDecryptor, requestContextResolver, exceptionResponseWriter,
                securityMetadataResolver, null);
    }

    /**
     * <p>
     * 创建 Coco 请求解密过滤器。
     * </p>
     * @param properties 请求加密配置属性
     * @param keyResolver AES 解密密钥解析器
     * @param requestDecryptor 请求解密器
     * @param requestContextResolver Web 请求上下文解析器
     * @param exceptionResponseWriter 过滤器异常响应写出器
     * @param securityMetadataResolver 请求安全元数据解析器
     * @param requestMatcher Web 请求匹配器
     */
    public CocoEncryptionFilter(CocoEncryptionProperties properties, CocoEncryptionKeyResolver keyResolver,
            CocoRequestDecryptor requestDecryptor, CocoWebRequestContextResolver requestContextResolver,
            CocoFilterExceptionResponseWriter exceptionResponseWriter,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver, CocoWebRequestMatcher requestMatcher) {
        this.properties = properties == null ? new CocoEncryptionProperties() : properties;
        this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver must not be null");
        this.requestDecryptor = Objects.requireNonNull(requestDecryptor, "requestDecryptor must not be null");
        this.requestContextResolver = Objects.requireNonNull(requestContextResolver,
                "requestContextResolver must not be null");
        this.requestMatcher = requestMatcher == null ? new DefaultCocoWebRequestMatcher() : requestMatcher;
        this.securityMetadataResolver = securityMetadataResolver == null
                ? new DefaultCocoWebRequestSecurityMetadataResolver(null, this.properties)
                : securityMetadataResolver;
        this.exceptionResponseWriter = Objects.requireNonNull(exceptionResponseWriter,
                "exceptionResponseWriter must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!this.properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (matchesIgnoredRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        boolean encrypted = encrypted(request);
        boolean encryptionRequired = encryptionRequired(request);
        if (!encrypted && !encryptionRequired) {
            filterChain.doFilter(request, response);
            return;
        }
        Optional<CocoRequestContext> previousContext = CocoRequestContextHolder.current();
        Optional<String> previousTraceId = CocoTraceContext.currentTraceId();
        try {
            HttpServletRequest decryptedRequest = decryptRequest(request, encrypted);
            CocoWebRequestSnapshot effectiveSnapshot = resolveEffectiveRequestSnapshot(decryptedRequest);
            CocoRequestContextHolder.set(effectiveSnapshot.toRequestContext());
            filterChain.doFilter(decryptedRequest, response);
        }
        catch (CocoException ex) {
            this.exceptionResponseWriter.write(ex, request, response);
        }
        finally {
            restoreRequestContext(previousContext, previousTraceId);
        }
    }

    private boolean matchesIgnoredRequest(HttpServletRequest request) {
        return this.requestMatcher.matches(request, this.properties.getMatcher().getIgnored());
    }

    private boolean encryptionRequired(HttpServletRequest request) {
        return this.properties.isRequired()
                || this.requestMatcher.matches(request, this.properties.getMatcher().getRequired());
    }

    private HttpServletRequest decryptRequest(HttpServletRequest request, boolean encrypted) {
        if (!encrypted) {
            throw CocoBusinessExceptions.unauthorized("coco.web.encryption.missing-encrypted");
        }
        ResolvedEncryptedRequest resolvedRequest = resolveEncryptedRequest(request);
        CocoEncryptedRequest encryptedRequest = resolvedRequest.request();
        validateRequiredFields(encryptedRequest);
        CocoEncryptionKey key = this.keyResolver.resolve(encryptedRequest)
                .orElseThrow(() -> CocoBusinessExceptions.unauthorized("coco.web.encryption.key-not-found"));
        try {
            byte[] decryptedPayload = this.requestDecryptor.decrypt(new CocoRequestDecryptionContext(
                    encryptedRequest, key, resolvedRequest.associatedData()));
            return new CocoCachedBodyHttpServletRequest(request, CocoCachedRequestBody.cached(decryptedPayload));
        }
        catch (RuntimeException ex) {
            throw CocoBusinessExceptions.unauthorized("coco.web.encryption.decrypt-failed");
        }
    }

    private ResolvedEncryptedRequest resolveEncryptedRequest(HttpServletRequest request) {
        CocoWebRequestSnapshot snapshot = this.requestContextResolver.resolve(CocoTraceContext.getOrCreateTraceId(),
                request);
        CocoWebRequestSecurityInput securityInput = snapshot.securityInput();
        CocoWebRequestSecurityMetadata metadata = this.securityMetadataResolver.resolve(securityInput);
        byte[] payload = CocoCachedBodyHttpServletRequest.cachedBody(request)
                .map(CocoCachedRequestBody::content)
                .orElseGet(() -> readRawBody(request));
        CocoEncryptedRequest encryptedRequest = new CocoEncryptedRequest(
                metadata.encryptionAppId(),
                metadata.encryptionKeyId(),
                metadata.encryptionIv(),
                metadata.encryptionAlgorithm(),
                metadata.encrypted(),
                payload);
        return new ResolvedEncryptedRequest(encryptedRequest,
                CocoEncryptionAssociatedData.from(encryptedRequest, snapshot, metadata));
    }

    /**
     * <p>
     * 解析解密后业务实际可见的请求快照。
     * </p>
     * <p>
     * 解密会替换请求体，因此需要清理旧快照并基于明文请求体重新解析上下文，供业务、日志和审计能力读取一致视图。
     * </p>
     * @param request 已解密请求
     * @return 解密后请求快照
     */
    private CocoWebRequestSnapshot resolveEffectiveRequestSnapshot(HttpServletRequest request) {
        CocoWebRequestSnapshotAttributes.clear(request);
        return this.requestContextResolver.resolve(CocoTraceContext.getOrCreateTraceId(), request);
    }

    /**
     * <p>
     * 恢复进入解密过滤器前的请求上下文。
     * </p>
     * @param previousContext 之前的请求上下文
     * @param previousTraceId 之前的 TraceId
     */
    private static void restoreRequestContext(Optional<CocoRequestContext> previousContext,
            Optional<String> previousTraceId) {
        if (previousContext.isPresent()) {
            CocoRequestContextHolder.set(previousContext.get());
            return;
        }
        CocoRequestContextHolder.clear();
        previousTraceId.ifPresent(CocoTraceContext::setTraceId);
    }

    private static byte[] readRawBody(HttpServletRequest request) {
        try {
            return request.getInputStream().readAllBytes();
        }
        catch (IOException ex) {
            throw CocoBusinessExceptions.request("coco.web.encryption.body-read-failed");
        }
    }

    private void validateRequiredFields(CocoEncryptedRequest request) {
        if (request.appId() == null) {
            throw CocoBusinessExceptions.unauthorized("coco.web.encryption.missing-app-id");
        }
        if (request.iv() == null) {
            throw CocoBusinessExceptions.unauthorized("coco.web.encryption.missing-iv");
        }
        if (request.algorithm() == null) {
            throw CocoBusinessExceptions.unauthorized("coco.web.encryption.missing-algorithm");
        }
        if (request.payload().length == 0) {
            throw CocoBusinessExceptions.unauthorized("coco.web.encryption.missing-payload");
        }
    }

    private boolean encrypted(HttpServletRequest request) {
        CocoWebSecurityMetadataSource source = this.properties.getMetadataSource();
        return encrypted(source.supportsHeader() ? request.getHeader(this.properties.getEncryptedHeaderName()) : null)
                || encrypted(source.supportsParameter()
                        ? request.getParameter(this.properties.getEncryptedParameterName())
                        : null);
    }

    private static boolean encrypted(String value) {
        return value != null && ("true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim()));
    }

    private record ResolvedEncryptedRequest(CocoEncryptedRequest request, byte[] associatedData) {

        private ResolvedEncryptedRequest {
            request = Objects.requireNonNull(request, "request must not be null");
            associatedData = associatedData == null ? new byte[0] : associatedData.clone();
        }

        @Override
        public byte[] associatedData() {
            return this.associatedData.clone();
        }
    }
}
