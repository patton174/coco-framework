package io.github.coco.feature.web.body;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Coco 请求体元数据。
 * <p>
 * 只保存请求体摘要、长度和阶段信息，不保存请求体原文，避免日志、审计和上下文快照无意扩散请求体内容。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @param transportSha256 传输态请求体 SHA-256 摘要
 * @param transportLength 传输态请求体长度
 * @param transportCached 传输态请求体是否已缓存
 * @param effectiveSha256 业务态请求体 SHA-256 摘要
 * @param effectiveLength 业务态请求体长度
 * @param effectiveCached 业务态请求体是否已缓存
 * @param stage 请求体阶段
 * @author patton174
 * @since 1.0.0
 */
public record CocoRequestBodyMetadata(String transportSha256, Long transportLength, boolean transportCached,
        String effectiveSha256, Long effectiveLength, boolean effectiveCached, CocoRequestBodyStage stage) {

    private static final CocoRequestBodyMetadata EMPTY = new CocoRequestBodyMetadata(null, null, false,
            null, null, false, CocoRequestBodyStage.NONE);

    /**
     * <p>
     * 创建请求体元数据，并归一化缓存状态。
     * </p>
     * @param transportSha256 传输态请求体 SHA-256 摘要
     * @param transportLength 传输态请求体长度
     * @param transportCached 传输态请求体是否已缓存
     * @param effectiveSha256 业务态请求体 SHA-256 摘要
     * @param effectiveLength 业务态请求体长度
     * @param effectiveCached 业务态请求体是否已缓存
     * @param stage 请求体阶段
     */
    public CocoRequestBodyMetadata {
        transportSha256 = normalizeOptional(transportSha256);
        effectiveSha256 = normalizeOptional(effectiveSha256);
        transportLength = normalizeLength(transportLength);
        effectiveLength = normalizeLength(effectiveLength);
        transportCached = transportCached && transportSha256 != null;
        effectiveCached = effectiveCached && effectiveSha256 != null;
        stage = stage == null ? resolveStage(transportSha256, transportCached, effectiveSha256, effectiveCached)
                : stage;
    }

    /**
     * <p>
     * 返回空请求体元数据。
     * </p>
     * @return 空请求体元数据
     */
    public static CocoRequestBodyMetadata empty() {
        return EMPTY;
    }

    /**
     * <p>
     * 从当前请求解析请求体元数据。
     * </p>
     * @param request 当前 HTTP 请求
     * @return 请求体元数据
     */
    public static CocoRequestBodyMetadata from(HttpServletRequest request) {
        CocoCachedRequestBody effectiveBody = CocoCachedBodyHttpServletRequest.effectiveBody(request)
                .orElse(CocoCachedRequestBody.empty());
        CocoCachedRequestBody transportBody = CocoCachedBodyHttpServletRequest.transportBody(request)
                .orElse(effectiveBody);
        return fromBodies(transportBody, effectiveBody);
    }

    /**
     * <p>
     * 从业务态请求体摘要创建请求体元数据。
     * </p>
     * @param sha256 业务态请求体 SHA-256 摘要
     * @param length 业务态请求体长度
     * @param cached 业务态请求体是否已缓存
     * @return 请求体元数据
     */
    public static CocoRequestBodyMetadata fromEffective(String sha256, Long length, boolean cached) {
        return new CocoRequestBodyMetadata(sha256, length, cached, sha256, length, cached, null);
    }

    private static CocoRequestBodyMetadata fromBodies(CocoCachedRequestBody transportBody,
            CocoCachedRequestBody effectiveBody) {
        CocoCachedRequestBody transport = transportBody == null ? CocoCachedRequestBody.empty() : transportBody;
        CocoCachedRequestBody effective = effectiveBody == null ? CocoCachedRequestBody.empty() : effectiveBody;
        return new CocoRequestBodyMetadata(transport.sha256(), transport.cached() ? transport.length() : null,
                transport.cached(), effective.sha256(), effective.cached() ? effective.length() : null,
                effective.cached(), null);
    }

    private static CocoRequestBodyStage resolveStage(String transportSha256, boolean transportCached,
            String effectiveSha256, boolean effectiveCached) {
        if (!effectiveCached) {
            return CocoRequestBodyStage.NONE;
        }
        if (!transportCached || Objects.equals(transportSha256, effectiveSha256)) {
            return CocoRequestBodyStage.TRANSPORT;
        }
        return CocoRequestBodyStage.DECRYPTED;
    }

    private static Long normalizeLength(Long length) {
        return length == null || length < 0 ? null : length;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
