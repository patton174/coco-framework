package io.github.coco.feature.web.signature;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import io.github.coco.common.exception.CocoBusinessExceptions;
import io.github.coco.common.exception.CocoException;
import io.github.coco.common.trace.CocoTraceContext;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalForm;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizationContext;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizationPurpose;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizer;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import io.github.coco.feature.web.context.CocoWebRequestSecurityMetadata;
import io.github.coco.feature.web.context.CocoWebRequestSecurityMetadataResolver;
import io.github.coco.feature.web.context.CocoWebRequestSecurityInput;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
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
 * Coco 请求签名过滤器。
 * <p>
 * 在业务处理前解析规范化请求文本并执行 Sign 验签；验签异常会写出 Coco 统一响应。
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
public final class CocoSignatureFilter extends OncePerRequestFilter {

    private final CocoSignatureProperties properties;

    private final CocoSignatureSecretResolver secretResolver;

    private final CocoSignatureVerifier signatureVerifier;

    private final CocoWebRequestContextResolver requestContextResolver;

    private final CocoWebRequestCanonicalizer requestCanonicalizer;

    private final CocoWebRequestMatcher requestMatcher;

    private final CocoWebRequestSecurityMetadataResolver securityMetadataResolver;

    private final CocoFilterExceptionResponseWriter exceptionResponseWriter;

    private final Clock clock;

    /**
     * <p>
     * 创建 Coco 请求签名过滤器。
     * </p>
     * @param properties 请求签名配置属性
     * @param secretResolver 请求签名密钥解析器
     * @param signatureVerifier 请求签名验证器
     * @param requestContextResolver Web 请求上下文解析器
     * @param requestCanonicalizer Web 请求规范化器
     * @param exceptionResponseWriter 过滤器异常响应写出器
     */
    public CocoSignatureFilter(CocoSignatureProperties properties, CocoSignatureSecretResolver secretResolver,
            CocoSignatureVerifier signatureVerifier, CocoWebRequestContextResolver requestContextResolver,
            CocoWebRequestCanonicalizer requestCanonicalizer, CocoFilterExceptionResponseWriter exceptionResponseWriter) {
        this(properties, secretResolver, signatureVerifier, requestContextResolver, requestCanonicalizer,
                exceptionResponseWriter, null, null, Clock.systemUTC());
    }

    /**
     * <p>
     * 创建 Coco 请求签名过滤器。
     * </p>
     * @param properties 请求签名配置属性
     * @param secretResolver 请求签名密钥解析器
     * @param signatureVerifier 请求签名验证器
     * @param requestContextResolver Web 请求上下文解析器
     * @param requestCanonicalizer Web 请求规范化器
     * @param exceptionResponseWriter 过滤器异常响应写出器
     * @param clock 时钟
     */
    public CocoSignatureFilter(CocoSignatureProperties properties, CocoSignatureSecretResolver secretResolver,
            CocoSignatureVerifier signatureVerifier, CocoWebRequestContextResolver requestContextResolver,
            CocoWebRequestCanonicalizer requestCanonicalizer, CocoFilterExceptionResponseWriter exceptionResponseWriter,
            Clock clock) {
        this(properties, secretResolver, signatureVerifier, requestContextResolver, requestCanonicalizer,
                exceptionResponseWriter, null, null, clock);
    }

    /**
     * <p>
     * 创建 Coco 请求签名过滤器。
     * </p>
     * @param properties 请求签名配置属性
     * @param secretResolver 请求签名密钥解析器
     * @param signatureVerifier 请求签名验证器
     * @param requestContextResolver Web 请求上下文解析器
     * @param requestCanonicalizer Web 请求规范化器
     * @param exceptionResponseWriter 过滤器异常响应写出器
     * @param securityMetadataResolver 请求安全元数据解析器
     */
    public CocoSignatureFilter(CocoSignatureProperties properties, CocoSignatureSecretResolver secretResolver,
            CocoSignatureVerifier signatureVerifier, CocoWebRequestContextResolver requestContextResolver,
            CocoWebRequestCanonicalizer requestCanonicalizer, CocoFilterExceptionResponseWriter exceptionResponseWriter,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver) {
        this(properties, secretResolver, signatureVerifier, requestContextResolver, requestCanonicalizer,
                exceptionResponseWriter, securityMetadataResolver, null, Clock.systemUTC());
    }

    /**
     * <p>
     * 创建 Coco 请求签名过滤器。
     * </p>
     * @param properties 请求签名配置属性
     * @param secretResolver 请求签名密钥解析器
     * @param signatureVerifier 请求签名验证器
     * @param requestContextResolver Web 请求上下文解析器
     * @param requestCanonicalizer Web 请求规范化器
     * @param exceptionResponseWriter 过滤器异常响应写出器
     * @param securityMetadataResolver 请求安全元数据解析器
     * @param clock 时钟
     */
    public CocoSignatureFilter(CocoSignatureProperties properties, CocoSignatureSecretResolver secretResolver,
            CocoSignatureVerifier signatureVerifier, CocoWebRequestContextResolver requestContextResolver,
            CocoWebRequestCanonicalizer requestCanonicalizer, CocoFilterExceptionResponseWriter exceptionResponseWriter,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver, Clock clock) {
        this(properties, secretResolver, signatureVerifier, requestContextResolver, requestCanonicalizer,
                exceptionResponseWriter, securityMetadataResolver, null, clock);
    }

    /**
     * <p>
     * 创建 Coco 请求签名过滤器。
     * </p>
     * @param properties 请求签名配置属性
     * @param secretResolver 请求签名密钥解析器
     * @param signatureVerifier 请求签名验证器
     * @param requestContextResolver Web 请求上下文解析器
     * @param requestCanonicalizer Web 请求规范化器
     * @param exceptionResponseWriter 过滤器异常响应写出器
     * @param securityMetadataResolver 请求安全元数据解析器
     * @param requestMatcher Web 请求匹配器
     * @param clock 时钟
     */
    public CocoSignatureFilter(CocoSignatureProperties properties, CocoSignatureSecretResolver secretResolver,
            CocoSignatureVerifier signatureVerifier, CocoWebRequestContextResolver requestContextResolver,
            CocoWebRequestCanonicalizer requestCanonicalizer, CocoFilterExceptionResponseWriter exceptionResponseWriter,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver, CocoWebRequestMatcher requestMatcher,
            Clock clock) {
        this.properties = properties == null ? new CocoSignatureProperties() : properties;
        this.secretResolver = Objects.requireNonNull(secretResolver, "secretResolver must not be null");
        this.signatureVerifier = Objects.requireNonNull(signatureVerifier, "signatureVerifier must not be null");
        this.requestContextResolver = Objects.requireNonNull(requestContextResolver,
                "requestContextResolver must not be null");
        this.requestCanonicalizer = Objects.requireNonNull(requestCanonicalizer,
                "requestCanonicalizer must not be null");
        this.securityMetadataResolver = securityMetadataResolver == null
                ? new DefaultCocoWebRequestSecurityMetadataResolver(this.properties, null)
                : securityMetadataResolver;
        this.requestMatcher = requestMatcher == null ? new DefaultCocoWebRequestMatcher() : requestMatcher;
        this.exceptionResponseWriter = Objects.requireNonNull(exceptionResponseWriter,
                "exceptionResponseWriter must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
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
        boolean signatureExpected = hasSignatureHeader(request);
        boolean signatureRequired = signatureRequired(request);
        if (!signatureRequired && !signatureExpected) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            verifyRequest(request, signatureExpected, signatureRequired);
        }
        catch (CocoException ex) {
            this.exceptionResponseWriter.write(ex, request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean hasSignatureHeader(HttpServletRequest request) {
        CocoWebSecurityMetadataSource source = this.properties.getMetadataSource();
        return hasSignatureHeader(request, source) || hasSignatureParameter(request, source);
    }

    private boolean hasSignatureHeader(HttpServletRequest request, CocoWebSecurityMetadataSource source) {
        if (!source.supportsHeader()) {
            return false;
        }
        return request.getHeader(this.properties.getSignatureHeaderName()) != null
                || request.getHeader(this.properties.getSignatureFallbackHeaderName()) != null;
    }

    private boolean hasSignatureParameter(HttpServletRequest request, CocoWebSecurityMetadataSource source) {
        if (!source.supportsParameter()) {
            return false;
        }
        return hasRequestParameter(request, this.properties.getSignatureParameterName())
                || hasRequestParameter(request, this.properties.getSignatureFallbackParameterName());
    }

    private static boolean hasRequestParameter(HttpServletRequest request, String parameterName) {
        String value = parameterName == null || parameterName.isBlank() ? null : request.getParameter(parameterName);
        return value != null && !value.isBlank();
    }

    private boolean matchesIgnoredRequest(HttpServletRequest request) {
        return this.requestMatcher.matches(request, this.properties.getMatcher().getIgnored());
    }

    private boolean signatureRequired(HttpServletRequest request) {
        return this.properties.isRequired()
                || this.requestMatcher.matches(request, this.properties.getMatcher().getRequired());
    }

    private void verifyRequest(HttpServletRequest request, boolean signatureExpected, boolean signatureRequired) {
        String traceId = CocoTraceContext.currentTraceId().orElseGet(CocoTraceContext::getOrCreateTraceId);
        CocoWebRequestSnapshot snapshot = this.requestContextResolver.resolve(traceId, request);
        CocoWebRequestSecurityInput securityInput = snapshot.securityInput();
        CocoWebRequestSecurityMetadata metadata = this.securityMetadataResolver.resolve(securityInput);
        CocoWebRequestSecurityInput canonicalInput = canonicalSecurityInput(securityInput);
        CocoWebRequestCanonicalForm canonicalForm = this.requestCanonicalizer.canonicalize(
                new CocoWebRequestCanonicalizationContext(CocoWebRequestCanonicalizationPurpose.SIGNATURE,
                        canonicalInput, metadata, snapshot.browserFingerprint()));
        CocoSignatureRequest signatureRequest = resolveSignatureRequest(metadata, canonicalForm);
        if (!signatureRequest.signed()) {
            if (signatureRequired || signatureExpected) {
                throw CocoBusinessExceptions.unauthorized("coco.web.signature.missing-signature");
            }
            return;
        }
        validateBodyHash(request, snapshot);
        validateRequiredFields(signatureRequest);
        validateTimestamp(signatureRequest);
        CocoSignatureSecret secret = this.secretResolver.resolve(signatureRequest)
                .orElseThrow(() -> CocoBusinessExceptions.unauthorized("coco.web.signature.secret-not-found"));
        if (!this.signatureVerifier.verify(new CocoSignatureVerificationContext(signatureRequest, secret))) {
            throw CocoBusinessExceptions.unauthorized("coco.web.signature.invalid");
        }
    }

    private CocoSignatureRequest resolveSignatureRequest(CocoWebRequestSecurityMetadata metadata,
            CocoWebRequestCanonicalForm canonicalForm) {
        return new CocoSignatureRequest(
                metadata.signatureAppId(),
                metadata.signatureKeyId(),
                metadata.signatureTimestamp(),
                metadata.signatureNonce(),
                metadata.signatureAlgorithm(),
                metadata.signature(),
                canonicalForm.text(),
                canonicalForm.sha256());
    }

    private static void validateBodyHash(HttpServletRequest request, CocoWebRequestSnapshot snapshot) {
        if (hasRequestBody(request) && !snapshot.securityInput().bodyCached()) {
            throw CocoBusinessExceptions.unauthorized("coco.web.signature.missing-body-hash");
        }
    }

    private static boolean hasRequestBody(HttpServletRequest request) {
        long contentLength = request.getContentLengthLong();
        if (contentLength > 0L) {
            return true;
        }
        String contentLengthHeader = request.getHeader("Content-Length");
        if (contentLengthHeader != null && !contentLengthHeader.isBlank()) {
            try {
                return Long.parseLong(contentLengthHeader.trim()) > 0L;
            }
            catch (NumberFormatException ignored) {
                return true;
            }
        }
        String transferEncoding = request.getHeader("Transfer-Encoding");
        return transferEncoding != null && !transferEncoding.isBlank();
    }

    private void validateRequiredFields(CocoSignatureRequest request) {
        if (request.appId() == null) {
            throw CocoBusinessExceptions.unauthorized("coco.web.signature.missing-app-id");
        }
        if (request.algorithm() == null) {
            throw CocoBusinessExceptions.unauthorized("coco.web.signature.missing-algorithm");
        }
        if (this.properties.isTimestampRequired() && request.timestamp() == null) {
            throw CocoBusinessExceptions.unauthorized("coco.web.signature.missing-timestamp");
        }
    }

    private void validateTimestamp(CocoSignatureRequest request) {
        if (!this.properties.isTimestampValidationEnabled() || request.timestamp() == null) {
            return;
        }
        Instant requestTime = parseTimestamp(request.timestamp());
        long skewMillis = Math.abs(Instant.now(this.clock).toEpochMilli() - requestTime.toEpochMilli());
        if (skewMillis > this.properties.getMaxClockSkewSeconds() * 1000L) {
            throw CocoBusinessExceptions.unauthorized("coco.web.signature.expired");
        }
    }

    private static Instant parseTimestamp(String timestamp) {
        try {
            long value = Long.parseLong(timestamp);
            return value < 10_000_000_000L ? Instant.ofEpochSecond(value) : Instant.ofEpochMilli(value);
        }
        catch (NumberFormatException ex) {
            try {
                return Instant.parse(timestamp);
            }
            catch (RuntimeException ignored) {
                throw CocoBusinessExceptions.unauthorized("coco.web.signature.invalid-timestamp");
            }
        }
    }

    private CocoWebRequestSecurityInput canonicalSecurityInput(CocoWebRequestSecurityInput securityInput) {
        if (!this.properties.getMetadataSource().supportsParameter()) {
            return securityInput;
        }
        CocoWebRequestSecurityInput canonicalInput = securityInput.withoutParameters(Set.of(
                this.properties.getSignatureParameterName(),
                this.properties.getSignatureFallbackParameterName()));
        if (payloadSignatureParameterPresent(securityInput)) {
            return canonicalInput.withoutBodyMetadata();
        }
        return canonicalInput;
    }

    private boolean payloadSignatureParameterPresent(CocoWebRequestSecurityInput securityInput) {
        return hasPayloadParameter(securityInput, this.properties.getSignatureParameterName())
                || hasPayloadParameter(securityInput, this.properties.getSignatureFallbackParameterName());
    }

    private static boolean hasPayloadParameter(CocoWebRequestSecurityInput securityInput, String parameterName) {
        return securityInput.payloadParameter(parameterName)
                .filter(values -> values.stream().anyMatch(value -> value != null && !value.isBlank()))
                .isPresent();
    }

}
