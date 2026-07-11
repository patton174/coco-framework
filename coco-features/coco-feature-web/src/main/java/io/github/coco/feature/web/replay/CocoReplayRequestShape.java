package io.github.coco.feature.web.replay;

import java.time.Instant;

import io.github.coco.exception.CocoBusinessExceptions;
import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadata;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Coco Web 防重放请求形态校验工具。
 * <p>
 * 仅负责判定请求是否需要防重放保护，以及校验防重放键的必需字段和时间戳格式。
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
final class CocoReplayRequestShape {

    private CocoReplayRequestShape() {
    }

    static boolean replayRequired(CocoReplayProperties properties, CocoWebRequestMatcher requestMatcher,
            HttpServletRequest request) {
        return properties.isRequired()
                || requestMatcher.matches(request, properties.getMatcher().getRequired());
    }

    /**
     * <p>
     * 判断当前请求是否需要执行防重放校验。
     * </p>
     * <p>
     * 当请求已经携带防重放协议字段时，即使未显式配置强制防重放，也应进入校验，避免业务误以为协议字段已经生效。
     * </p>
     * @param properties 防重放配置属性
     * @param metadata 请求安全元数据
     * @param replayRequired 当前请求是否强制防重放
     * @return 需要执行防重放校验时返回 {@code true}
     */
    static boolean shouldProtect(CocoReplayProperties properties, CocoWebRequestSecurityMetadata metadata,
            boolean replayRequired) {
        return replayRequired
                || metadata.replayProtected()
                || (properties.isProtectSignedRequests() && metadata.signed())
                || (properties.isProtectEncryptedRequests() && metadata.encrypted());
    }

    static void validateRequiredFields(CocoReplayKey replayKey) {
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

    static Instant parseTimestamp(String timestamp) {
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
}
