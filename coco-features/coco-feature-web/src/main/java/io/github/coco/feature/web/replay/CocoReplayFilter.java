package io.github.coco.feature.web.replay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.github.coco.common.context.CocoRequestContextAttributes;
import io.github.coco.common.context.CocoRequestContextHolder;
import io.github.coco.common.exception.CocoBusinessExceptions;
import io.github.coco.common.exception.CocoException;
import io.github.coco.common.trace.CocoTraceContext;
import io.github.coco.feature.web.context.CocoWebRequestContextPhase;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.feature.web.context.CocoWebRequestSnapshotAttributes;
import io.github.coco.feature.web.context.DefaultCocoWebRequestMatcher;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadata;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadataResolver;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Coco Web 防重放过滤器�? * <p>
 * 在业务处理前基于请求时间戳和随机串占用防重放键，阻止签名或加密等受保护请求在有效窗口内重复提交�? * </p>
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
public final class CocoReplayFilter extends OncePerRequestFilter {

    private final CocoReplayProperties properties;

    private final CocoReplayStore replayStore;

    private final CocoReplayKeyResolver replayKeyResolver;

    private final CocoWebRequestContextResolver requestContextResolver;

    private final CocoWebRequestMatcher requestMatcher;

    private final CocoWebRequestSecurityMetadataResolver securityMetadataResolver;

    private final CocoFilterExceptionResponseWriter exceptionResponseWriter;

    private final Clock clock;

    /**
     * <p>
     * 创建 Coco Web 防重放过滤器�?     * </p>
     * @param properties 防重放配置属�?     * @param replayStore 防重放存�?     * @param replayKeyResolver 防重放键解析�?     * @param requestContextResolver Web 请求上下文解析器
     * @param securityMetadataResolver Web 请求安全元数据解析器
     * @param exceptionResponseWriter 过滤器异常响应写出器
     */
    public CocoReplayFilter(CocoReplayProperties properties, CocoReplayStore replayStore,
            CocoReplayKeyResolver replayKeyResolver, CocoWebRequestContextResolver requestContextResolver,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver,
            CocoFilterExceptionResponseWriter exceptionResponseWriter) {
        this(properties, replayStore, replayKeyResolver, requestContextResolver, securityMetadataResolver,
                exceptionResponseWriter, Clock.systemUTC());
    }

    /**
     * <p>
     * 创建 Coco Web 防重放过滤器�?     * </p>
     * @param properties 防重放配置属�?     * @param replayStore 防重放存�?     * @param replayKeyResolver 防重放键解析�?     * @param requestContextResolver Web 请求上下文解析器
     * @param securityMetadataResolver Web 请求安全元数据解析器
     * @param exceptionResponseWriter 过滤器异常响应写出器
     * @param clock 时钟
     */
    public CocoReplayFilter(CocoReplayProperties properties, CocoReplayStore replayStore,
            CocoReplayKeyResolver replayKeyResolver, CocoWebRequestContextResolver requestContextResolver,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver,
            CocoFilterExceptionResponseWriter exceptionResponseWriter, Clock clock) {
        this(properties, replayStore, replayKeyResolver, requestContextResolver, securityMetadataResolver,
                exceptionResponseWriter, null, clock);
    }

    /**
     * <p>
     * 创建 Coco Web 防重放过滤器�?     * </p>
     * @param properties 防重放配置属�?     * @param replayStore 防重放存�?     * @param replayKeyResolver 防重放键解析�?     * @param requestContextResolver Web 请求上下文解析器
     * @param securityMetadataResolver Web 请求安全元数据解析器
     * @param exceptionResponseWriter 过滤器异常响应写出器
     * @param requestMatcher Web 请求匹配�?     * @param clock 时钟
     */
    public CocoReplayFilter(CocoReplayProperties properties, CocoReplayStore replayStore,
            CocoReplayKeyResolver replayKeyResolver, CocoWebRequestContextResolver requestContextResolver,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver,
            CocoFilterExceptionResponseWriter exceptionResponseWriter, CocoWebRequestMatcher requestMatcher,
            Clock clock) {
        this.properties = properties == null ? new CocoReplayProperties() : properties;
        this.replayStore = Objects.requireNonNull(replayStore, "replayStore must not be null");
        this.replayKeyResolver = Objects.requireNonNull(replayKeyResolver, "replayKeyResolver must not be null");
        this.requestContextResolver = Objects.requireNonNull(requestContextResolver,
                "requestContextResolver must not be null");
        this.requestMatcher = requestMatcher == null ? new DefaultCocoWebRequestMatcher() : requestMatcher;
        this.securityMetadataResolver = Objects.requireNonNull(securityMetadataResolver,
                "securityMetadataResolver must not be null");
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
        boolean replayRequired = replayRequired(request);
        try {
            verifyReplay(request, replayRequired);
        }
        catch (CocoException ex) {
            this.exceptionResponseWriter.write(ex, request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean matchesIgnoredRequest(HttpServletRequest request) {
        return this.requestMatcher.matches(request, this.properties.getMatcher().getIgnored());
    }

    private boolean replayRequired(HttpServletRequest request) {
        return this.properties.isRequired()
                || this.requestMatcher.matches(request, this.properties.getMatcher().getRequired());
    }

    private void verifyReplay(HttpServletRequest request, boolean replayRequired) {
        String traceId = CocoTraceContext.currentTraceId().orElseGet(CocoTraceContext::getOrCreateTraceId);
        CocoWebRequestSnapshot snapshot = this.requestContextResolver.resolve(traceId, request);
        CocoWebRequestSecurityMetadata metadata = this.securityMetadataResolver.resolve(snapshot.securityInput());
        if (!shouldProtect(metadata, replayRequired)) {
            return;
        }
        CocoReplayKey replayKey = this.replayKeyResolver.resolve(snapshot, metadata);
        validateRequiredFields(replayKey);
        Instant requestTime = parseTimestamp(replayKey.timestamp());
        Instant now = this.clock.instant();
        Duration ttl = Duration.ofSeconds(this.properties.getTtlSeconds());
        Duration maxClockSkew = Duration.ofSeconds(this.properties.getMaxClockSkewSeconds());
        if (requestTime.isAfter(now.plus(maxClockSkew))) {
            throw CocoBusinessExceptions.unauthorized("coco.web.replay.invalid-timestamp");
        }
        if (requestTime.isBefore(now.minus(maxClockSkew))) {
            throw CocoBusinessExceptions.unauthorized("coco.web.replay.expired");
        }
        Instant expiresAt = now.plus(ttl);
        if (!this.replayStore.reserve(replayKey, expiresAt)) {
            throw CocoBusinessExceptions.unauthorized("coco.web.replay.detected");
        }
        publishVerifiedSnapshot(request, snapshot, metadata, replayKey, expiresAt);
    }

    /**
     * <p>
     * 判断当前请求是否需要执行防重放校验�?     * </p>
     * <p>
     * 当请求已经携带防重放协议字段时，即使未显式配置强制防重放，也应进入校验，避免业务误以为协议字段已经生效�?     * </p>
     * @param metadata 请求安全元数�?     * @param replayRequired 当前请求是否强制防重�?     * @return 需要执行防重放校验时返�?{@code true}
     */
    private boolean shouldProtect(CocoWebRequestSecurityMetadata metadata, boolean replayRequired) {
        return replayRequired
                || metadata.replayProtected()
                || (this.properties.isProtectSignedRequests() && metadata.signed())
                || (this.properties.isProtectEncryptedRequests() && metadata.encrypted());
    }

    private static void validateRequiredFields(CocoReplayKey replayKey) {
        if (replayKey.appId() == null) {
            throw CocoBusinessExceptions.unauthorized("coco.web.replay.missing-app-id");
        }
        if (replayKey.timestamp() == null) {
            throw CocoBusinessExceptions.unauthorized("coco.web.replay.missing-timestamp");
        }
        if (replayKey.nonce() == null) {
            throw CocoBusinessExceptions.unauthorized("coco.web.replay.missing-nonce");
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
                throw CocoBusinessExceptions.unauthorized("coco.web.replay.invalid-timestamp");
            }
        }
    }

    private void publishVerifiedSnapshot(HttpServletRequest request, CocoWebRequestSnapshot snapshot,
            CocoWebRequestSecurityMetadata metadata, CocoReplayKey replayKey, Instant expiresAt) {
        Map<String, String> evidence = new LinkedHashMap<>();
        putEvidence(evidence, CocoRequestContextAttributes.REPLAY_METADATA_SOURCE,
                this.properties.getMetadataSource().name());
        putEvidence(evidence, CocoRequestContextAttributes.REPLAY_RESERVED, Boolean.TRUE.toString());
        putEvidence(evidence, CocoRequestContextAttributes.REPLAY_EXPIRES_AT,
                expiresAt == null ? null : expiresAt.toString());
        putEvidence(evidence, CocoRequestContextAttributes.REPLAY_WINDOW_SECONDS,
                Long.toString(this.properties.getTtlSeconds()));
        putEvidence(evidence, CocoRequestContextAttributes.REPLAY_KEY_SHA256,
                replayKey == null ? null : sha256(replayKey.value()));
        CocoWebRequestSnapshot verifiedSnapshot = snapshot.withSecurityMetadata(metadata)
                .withContextAttributes(evidence)
                .withContextPhase(CocoWebRequestContextPhase.REPLAY_VERIFIED);
        CocoWebRequestSnapshotAttributes.set(request, verifiedSnapshot);
        CocoRequestContextHolder.set(verifiedSnapshot.toRequestContext());
    }

    private static void putEvidence(Map<String, String> attributes, String name, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(name, value);
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }
}
