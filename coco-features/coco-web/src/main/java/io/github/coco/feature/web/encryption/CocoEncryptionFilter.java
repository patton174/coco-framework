package io.github.coco.feature.web.encryption;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.github.coco.context.CocoRequestContext;
import io.github.coco.context.CocoRequestContextAttributes;
import io.github.coco.context.CocoRequestContextHolder;
import io.github.coco.exception.CocoBusinessExceptions;
import io.github.coco.exception.CocoException;
import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.feature.web.body.CocoCachedBodyHttpServletRequest;
import io.github.coco.feature.web.body.CocoCachedRequestBody;
import io.github.coco.feature.web.body.CocoRequestBodyResolver;
import io.github.coco.feature.web.body.CocoResolvedRequestBody;
import io.github.coco.feature.web.body.DefaultCocoRequestBodyResolver;
import io.github.coco.feature.web.context.CocoWebRequestContextPhase;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadata;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadataResolver;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityInput;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.feature.web.context.CocoWebRequestSnapshotAttributes;
import io.github.coco.feature.web.request.metadata.CocoWebSecurityMetadataSource;
import io.github.coco.feature.web.context.DefaultCocoWebRequestMatcher;
import io.github.coco.feature.web.request.metadata.DefaultCocoWebRequestSecurityMetadataResolver;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Coco 请求解密过滤器�? * <p>
 * 在业务处理前根据 AES 请求头解密请求体，并使用可复读请求体包装器把明文交给后续 Controller�? * </p>
 * <p>
 * 项目信息�? * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库�?a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoEncryptionFilter extends OncePerRequestFilter {

    private static final String ASSOCIATED_DATA_VERSION = CocoEncryptionAssociatedData.version();

    private final CocoEncryptionProperties properties;

    private final CocoEncryptionKeyResolver keyResolver;

    private final CocoRequestDecryptor requestDecryptor;

    private final CocoWebRequestContextResolver requestContextResolver;

    private final CocoWebRequestMatcher requestMatcher;

    private final CocoWebRequestSecurityMetadataResolver securityMetadataResolver;

    private final CocoFilterExceptionResponseWriter exceptionResponseWriter;

    private final CocoRequestBodyResolver requestBodyResolver;

    /**
     * <p>
     * 创建 Coco 请求解密过滤器�?     * </p>
     * @param properties 请求加密配置属�?     * @param keyResolver AES 解密密钥解析�?     * @param requestDecryptor 请求解密�?     * @param requestContextResolver Web 请求上下文解析器
     * @param exceptionResponseWriter 过滤器异常响应写出器
     */
    public CocoEncryptionFilter(CocoEncryptionProperties properties, CocoEncryptionKeyResolver keyResolver,
            CocoRequestDecryptor requestDecryptor, CocoWebRequestContextResolver requestContextResolver,
            CocoFilterExceptionResponseWriter exceptionResponseWriter) {
        this(properties, keyResolver, requestDecryptor, requestContextResolver, exceptionResponseWriter, null);
    }

    /**
     * <p>
     * 创建 Coco 请求解密过滤器�?     * </p>
     * @param properties 请求加密配置属�?     * @param keyResolver AES 解密密钥解析�?     * @param requestDecryptor 请求解密�?     * @param requestContextResolver Web 请求上下文解析器
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
     * 创建 Coco 请求解密过滤器�?     * </p>
     * @param properties 请求加密配置属�?     * @param keyResolver AES 解密密钥解析�?     * @param requestDecryptor 请求解密�?     * @param requestContextResolver Web 请求上下文解析器
     * @param exceptionResponseWriter 过滤器异常响应写出器
     * @param securityMetadataResolver 请求安全元数据解析器
     * @param requestMatcher Web 请求匹配�?     */
    public CocoEncryptionFilter(CocoEncryptionProperties properties, CocoEncryptionKeyResolver keyResolver,
            CocoRequestDecryptor requestDecryptor, CocoWebRequestContextResolver requestContextResolver,
            CocoFilterExceptionResponseWriter exceptionResponseWriter,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver, CocoWebRequestMatcher requestMatcher) {
        this(properties, keyResolver, requestDecryptor, requestContextResolver, exceptionResponseWriter,
                securityMetadataResolver, requestMatcher, null);
    }

    /**
     * <p>
     * 创建 Coco 请求解密过滤器�?     * </p>
     * @param properties 请求加密配置属�?     * @param keyResolver AES 解密密钥解析�?     * @param requestDecryptor 请求解密�?     * @param requestContextResolver Web 请求上下文解析器
     * @param exceptionResponseWriter 过滤器异常响应写出器
     * @param securityMetadataResolver 请求安全元数据解析器
     * @param requestMatcher Web 请求匹配�?     * @param requestBodyResolver 请求体解析器
     */
    public CocoEncryptionFilter(CocoEncryptionProperties properties, CocoEncryptionKeyResolver keyResolver,
            CocoRequestDecryptor requestDecryptor, CocoWebRequestContextResolver requestContextResolver,
            CocoFilterExceptionResponseWriter exceptionResponseWriter,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver, CocoWebRequestMatcher requestMatcher,
            CocoRequestBodyResolver requestBodyResolver) {
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
        this.requestBodyResolver = requestBodyResolver == null
                ? new DefaultCocoRequestBodyResolver()
                : requestBodyResolver;
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
        ResolvedEncryptedRequest resolvedRequest = null;
        boolean encrypted = encrypted(request);
        if (!encrypted && parameterMetadataMayAppearInCachedBody(request)) {
            resolvedRequest = resolveEncryptedRequest(request);
            encrypted = resolvedRequest.metadata().encrypted();
        }
        boolean encryptionRequired = encryptionRequired(request);
        if (!encrypted && !encryptionRequired) {
            filterChain.doFilter(request, response);
            return;
        }
        Optional<CocoRequestContext> previousContext = CocoRequestContextHolder.current();
        Optional<String> previousTraceId = CocoTraceContext.currentTraceId();
        try {
            DecryptedRequest decryptedRequest = decryptRequest(request, encrypted, resolvedRequest);
            CocoWebRequestSnapshot effectiveSnapshot = resolveEffectiveRequestSnapshot(decryptedRequest.request())
                    .withSecurityMetadata(decryptedRequest.metadata())
                    .withContextAttributes(decryptedRequest.contextAttributes())
                    .withContextAttributes(decryptionEvidence(decryptedRequest.associatedData()))
                    .withContextPhase(CocoWebRequestContextPhase.DECRYPTED);
            CocoWebRequestSnapshotAttributes.set(decryptedRequest.request(), effectiveSnapshot);
            CocoRequestContextHolder.set(effectiveSnapshot.toRequestContext());
            filterChain.doFilter(decryptedRequest.request(), response);
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

    private DecryptedRequest decryptRequest(HttpServletRequest request, boolean encrypted,
            ResolvedEncryptedRequest resolvedRequest) {
        if (!encrypted) {
            throw CocoBusinessExceptions.unauthorized("coco.web.encryption.missing-encrypted");
        }
        ResolvedEncryptedRequest effectiveResolvedRequest = resolvedRequest == null
                ? resolveEncryptedRequest(request)
                : resolvedRequest;
        CocoEncryptedRequest encryptedRequest = effectiveResolvedRequest.request();
        validateRequiredFields(encryptedRequest);
        CocoEncryptionKey key = this.keyResolver.resolve(encryptedRequest)
                .orElseThrow(() -> CocoBusinessExceptions.unauthorized("coco.web.encryption.key-not-found"));
        try {
            byte[] decryptedPayload = this.requestDecryptor.decrypt(new CocoRequestDecryptionContext(
                    encryptedRequest, key, effectiveResolvedRequest.associatedData()));
            return new DecryptedRequest(
                    new CocoCachedBodyHttpServletRequest(request, CocoCachedRequestBody.cached(decryptedPayload)),
                    effectiveResolvedRequest.associatedData(),
                    effectiveResolvedRequest.metadata(),
                    effectiveResolvedRequest.snapshot().contextAttributes());
        }
        catch (CocoRequestDecryptException ex) {
            if (ex.failureKind() == CocoRequestDecryptException.FailureKind.MALFORMED_REQUEST) {
                throw CocoBusinessExceptions.request(ex.messageCode());
            }
            throw CocoBusinessExceptions.unauthorized(ex.messageCode());
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
        CocoResolvedRequestBody resolvedBody = this.requestBodyResolver.resolve(request);
        byte[] payload = encryptedPayload(securityInput)
                .or(() -> cachedPayload(resolvedBody))
                .orElseGet(() -> readRawBody(request));
        CocoEncryptedRequest encryptedRequest = new CocoEncryptedRequest(
                metadata.encryptionAppId(),
                metadata.encryptionKeyId(),
                metadata.encryptionIv(),
                metadata.encryptionAlgorithm(),
                metadata.encrypted(),
                payload);
        return new ResolvedEncryptedRequest(encryptedRequest,
                CocoEncryptionAssociatedData.from(encryptedRequest, snapshot, metadata), metadata, snapshot);
    }

    /**
     * <p>
     * 判断加密协议元数据是否可能来自已缓存的请求体参数�?     * </p>
     * @param request 当前 Servlet 请求
     * @return 需要从请求体参数解析加密元数据时返�?{@code true}
     */
    private boolean parameterMetadataMayAppearInCachedBody(HttpServletRequest request) {
        return this.properties.getMetadataSource().supportsParameter()
                && Optional.ofNullable(this.requestBodyResolver.resolve(request))
                        .map(CocoResolvedRequestBody::effectiveCached)
                        .orElse(false);
    }

    /**
     * <p>
     * 返回当前请求体解析结果中的业务态密文载荷�?     * </p>
     * @param resolvedBody 已解析请求体
     * @return 业务态密文载荷；未缓存时为空
     */
    private static Optional<byte[]> cachedPayload(CocoResolvedRequestBody resolvedBody) {
        CocoResolvedRequestBody body = resolvedBody == null
                ? new CocoResolvedRequestBody(null, null, null, null, null)
                : resolvedBody;
        return body.effectiveCached() ? Optional.of(body.effectiveBody().content()) : Optional.empty();
    }

    /**
     * <p>
     * 从请求体参数中读取加密信封密文字段�?     * </p>
     * @param securityInput 请求安全输入
     * @return 加密信封密文字段；未提供时为�?     */
    private Optional<byte[]> encryptedPayload(CocoWebRequestSecurityInput securityInput) {
        if (!this.properties.getMetadataSource().supportsParameter()) {
            return Optional.empty();
        }
        return firstNonBlankValue(securityInput.payloadParameter(this.properties.getPayloadParameterName()))
                .map(value -> value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * <p>
     * 从多值参数中返回第一个非空白原始值�?     * </p>
     * @param values 多值参�?     * @return 第一个非空白原始值；不存在时为空
     */
    private static Optional<String> firstNonBlankValue(Optional<List<String>> values) {
        return values.flatMap(parameterValues -> parameterValues.stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst());
    }

    /**
     * <p>
     * 解析解密后业务实际可见的请求快照�?     * </p>
     * <p>
     * 解密会替换请求体，因此需要清理旧快照并基于明文请求体重新解析上下文，供业务、日志和审计能力读取一致视图�?     * </p>
     * @param request 已解密请�?     * @return 解密后请求快�?     */
    private CocoWebRequestSnapshot resolveEffectiveRequestSnapshot(HttpServletRequest request) {
        CocoWebRequestSnapshotAttributes.clear(request);
        return this.requestContextResolver.resolve(CocoTraceContext.getOrCreateTraceId(), request);
    }

    /**
     * <p>
     * 恢复进入解密过滤器前的请求上下文�?     * </p>
     * @param previousContext 之前的请求上下文
     * @param previousTraceId 之前�?TraceId
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

    private Map<String, String> decryptionEvidence(byte[] associatedData) {
        Map<String, String> evidence = new LinkedHashMap<>();
        putEvidence(evidence, CocoRequestContextAttributes.ENCRYPTION_METADATA_SOURCE,
                this.properties.getMetadataSource().name());
        putEvidence(evidence, CocoRequestContextAttributes.REQUEST_DECRYPTED, Boolean.TRUE.toString());
        putEvidence(evidence, CocoRequestContextAttributes.ENCRYPTION_ASSOCIATED_DATA_VERSION,
                ASSOCIATED_DATA_VERSION);
        putEvidence(evidence, CocoRequestContextAttributes.ENCRYPTION_ASSOCIATED_DATA_SHA256,
                sha256(associatedData));
        return evidence;
    }

    private static void putEvidence(Map<String, String> attributes, String name, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(name, value);
        }
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content == null ? new byte[0] : content));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private record DecryptedRequest(HttpServletRequest request, byte[] associatedData,
            CocoWebRequestSecurityMetadata metadata, Map<String, String> contextAttributes) {

        private DecryptedRequest {
            request = Objects.requireNonNull(request, "request must not be null");
            associatedData = associatedData == null ? new byte[0] : associatedData.clone();
            metadata = metadata == null ? CocoWebRequestSecurityMetadata.empty() : metadata;
            contextAttributes = contextAttributes == null ? Map.of() : Map.copyOf(contextAttributes);
        }

        @Override
        public byte[] associatedData() {
            return this.associatedData.clone();
        }
    }

    private record ResolvedEncryptedRequest(CocoEncryptedRequest request, byte[] associatedData,
            CocoWebRequestSecurityMetadata metadata, CocoWebRequestSnapshot snapshot) {

        private ResolvedEncryptedRequest {
            request = Objects.requireNonNull(request, "request must not be null");
            associatedData = associatedData == null ? new byte[0] : associatedData.clone();
            metadata = metadata == null ? CocoWebRequestSecurityMetadata.empty() : metadata;
            snapshot = Objects.requireNonNull(snapshot, "snapshot must not be null");
        }

        @Override
        public byte[] associatedData() {
            return this.associatedData.clone();
        }
    }
}
