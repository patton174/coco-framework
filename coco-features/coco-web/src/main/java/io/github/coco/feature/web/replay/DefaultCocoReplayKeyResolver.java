package io.github.coco.feature.web.replay;

import java.util.Objects;

import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadata;

/**
 * 默认 Coco Web 防重放键解析器�? * <p>
 * 使用防重放协议头中的应用标识、密钥标识、时间戳和随机串组成基础键，并可按配置加入请求方法和路径�? * </p>
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
public final class DefaultCocoReplayKeyResolver implements CocoReplayKeyResolver {

    private final CocoReplayProperties properties;

    /**
     * <p>
     * 创建默认防重放键解析器�?     * </p>
     * @param properties 防重放配置属�?     */
    public DefaultCocoReplayKeyResolver(CocoReplayProperties properties) {
        this.properties = properties == null ? new CocoReplayProperties() : properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoReplayKey resolve(CocoWebRequestSnapshot snapshot, CocoWebRequestSecurityMetadata metadata) {
        CocoWebRequestSnapshot checkedSnapshot = Objects.requireNonNull(snapshot, "snapshot must not be null");
        CocoWebRequestSecurityMetadata checkedMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
        return new CocoReplayKey(
                firstNonBlank(checkedMetadata.replayAppId(), checkedMetadata.primaryAppId().orElse(null)),
                firstNonBlank(checkedMetadata.replayKeyId(), checkedMetadata.primaryKeyId().orElse(null)),
                firstNonBlank(checkedMetadata.replayTimestamp(), checkedMetadata.signatureTimestamp()),
                firstNonBlank(checkedMetadata.replayNonce(), checkedMetadata.signatureNonce()),
                this.properties.isIncludeMethod() ? checkedSnapshot.method() : null,
                this.properties.isIncludePath() ? checkedSnapshot.path() : null);
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback == null || fallback.isBlank() ? null : fallback;
    }
}
