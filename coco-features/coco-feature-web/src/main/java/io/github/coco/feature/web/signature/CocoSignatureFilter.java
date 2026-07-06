package io.github.coco.feature.web.signature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.common.exception.CocoBusinessExceptions;
import io.github.coco.common.exception.CocoException;
import io.github.coco.common.trace.CocoTraceContext;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalForm;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizer;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestSecurityInput;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;
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

    private final CocoWebExceptionHandler exceptionHandler;

    private final ObjectMapper objectMapper;

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
     * @param exceptionHandler Coco Web 全局异常处理器
     * @param objectMapper JSON 序列化器
     */
    public CocoSignatureFilter(CocoSignatureProperties properties, CocoSignatureSecretResolver secretResolver,
            CocoSignatureVerifier signatureVerifier, CocoWebRequestContextResolver requestContextResolver,
            CocoWebRequestCanonicalizer requestCanonicalizer, CocoWebExceptionHandler exceptionHandler,
            ObjectMapper objectMapper) {
        this(properties, secretResolver, signatureVerifier, requestContextResolver, requestCanonicalizer,
                exceptionHandler, objectMapper, Clock.systemUTC());
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
     * @param exceptionHandler Coco Web 全局异常处理器
     * @param objectMapper JSON 序列化器
     * @param clock 时钟
     */
    public CocoSignatureFilter(CocoSignatureProperties properties, CocoSignatureSecretResolver secretResolver,
            CocoSignatureVerifier signatureVerifier, CocoWebRequestContextResolver requestContextResolver,
            CocoWebRequestCanonicalizer requestCanonicalizer, CocoWebExceptionHandler exceptionHandler,
            ObjectMapper objectMapper, Clock clock) {
        this.properties = properties == null ? new CocoSignatureProperties() : properties;
        this.secretResolver = Objects.requireNonNull(secretResolver, "secretResolver must not be null");
        this.signatureVerifier = Objects.requireNonNull(signatureVerifier, "signatureVerifier must not be null");
        this.requestContextResolver = Objects.requireNonNull(requestContextResolver,
                "requestContextResolver must not be null");
        this.requestCanonicalizer = Objects.requireNonNull(requestCanonicalizer,
                "requestCanonicalizer must not be null");
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler must not be null");
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
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
        boolean signatureExpected = hasSignatureHeader(request);
        if (!this.properties.isRequired() && !signatureExpected) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            verifyRequest(request, signatureExpected);
        }
        catch (CocoException ex) {
            writeErrorResponse(ex, request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean hasSignatureHeader(HttpServletRequest request) {
        return request.getHeader(this.properties.getSignatureHeaderName()) != null
                || request.getHeader(this.properties.getSignatureFallbackHeaderName()) != null;
    }

    private void verifyRequest(HttpServletRequest request, boolean signatureExpected) {
        String traceId = CocoTraceContext.currentTraceId().orElseGet(CocoTraceContext::getOrCreateTraceId);
        CocoWebRequestSnapshot snapshot = this.requestContextResolver.resolve(traceId, request);
        CocoWebRequestSecurityInput securityInput = snapshot.securityInput();
        CocoWebRequestCanonicalForm canonicalForm = this.requestCanonicalizer.canonicalize(securityInput);
        CocoSignatureRequest signatureRequest = resolveSignatureRequest(securityInput, canonicalForm);
        if (!signatureRequest.signed()) {
            if (this.properties.isRequired() || signatureExpected) {
                throw CocoBusinessExceptions.unauthorized("coco.web.signature.missing-signature");
            }
            return;
        }
        validateRequiredFields(signatureRequest);
        validateTimestamp(signatureRequest);
        CocoSignatureSecret secret = this.secretResolver.resolve(signatureRequest)
                .orElseThrow(() -> CocoBusinessExceptions.unauthorized("coco.web.signature.secret-not-found"));
        if (!this.signatureVerifier.verify(new CocoSignatureVerificationContext(signatureRequest, secret))) {
            throw CocoBusinessExceptions.unauthorized("coco.web.signature.invalid");
        }
    }

    private CocoSignatureRequest resolveSignatureRequest(CocoWebRequestSecurityInput input,
            CocoWebRequestCanonicalForm canonicalForm) {
        String signature = input.securityHeader(this.properties.getSignatureHeaderName())
                .or(() -> input.securityHeader(this.properties.getSignatureFallbackHeaderName()))
                .orElse(null);
        String algorithm = input.securityHeader(this.properties.getAlgorithmHeaderName())
                .orElse(this.properties.getDefaultAlgorithm());
        return new CocoSignatureRequest(
                input.securityHeader(this.properties.getAppIdHeaderName()).orElse(null),
                input.securityHeader(this.properties.getKeyIdHeaderName()).orElse(null),
                input.securityHeader(this.properties.getTimestampHeaderName()).orElse(null),
                input.securityHeader(this.properties.getNonceHeaderName()).orElse(null),
                algorithm,
                signature,
                canonicalForm.text(),
                canonicalForm.sha256());
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

    private void writeErrorResponse(CocoException exception, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            throw exception;
        }
        RequestAttributes previousAttributes = RequestContextHolder.getRequestAttributes();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        try {
            ResponseEntity<Object> entity = this.exceptionHandler.handleCocoException(exception,
                    new ServletWebRequest(request, response));
            response.setStatus(entity.getStatusCode().value());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            this.objectMapper.writeValue(response.getOutputStream(), entity.getBody());
        }
        finally {
            if (previousAttributes == null) {
                RequestContextHolder.resetRequestAttributes();
            }
            else {
                RequestContextHolder.setRequestAttributes(previousAttributes);
            }
        }
    }
}
