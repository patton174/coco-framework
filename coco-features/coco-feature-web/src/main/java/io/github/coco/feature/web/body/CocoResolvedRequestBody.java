package io.github.coco.feature.web.body;

/**
 * Coco 已解析请求体。
 * <p>
 * 同时保存传输态请求体、业务态请求体和请求体元数据，作为 Sign、AES、防重放、日志和审计能力共享的请求体解析结果。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @param transportBody 传输态请求体
 * @param effectiveBody 业务态请求体
 * @param metadata 请求体元数据
 * @param contentType 请求内容类型
 * @param characterEncoding 请求字符编码
 * @author patton174
 * @since 1.0.0
 */
public record CocoResolvedRequestBody(CocoCachedRequestBody transportBody, CocoCachedRequestBody effectiveBody,
        CocoRequestBodyMetadata metadata, String contentType, String characterEncoding) {

    /**
     * <p>
     * 创建已解析请求体，并归一化空对象和空白字段。
     * </p>
     * @param transportBody 传输态请求体
     * @param effectiveBody 业务态请求体
     * @param metadata 请求体元数据
     * @param contentType 请求内容类型
     * @param characterEncoding 请求字符编码
     */
    public CocoResolvedRequestBody {
        effectiveBody = effectiveBody == null ? CocoCachedRequestBody.empty() : effectiveBody;
        transportBody = transportBody == null ? effectiveBody : transportBody;
        metadata = metadata == null ? CocoRequestBodyMetadata.from(transportBody, effectiveBody) : metadata;
        contentType = normalizeOptional(contentType);
        characterEncoding = normalizeOptional(characterEncoding);
    }

    /**
     * <p>
     * 返回是否存在已缓存的业务态请求体。
     * </p>
     * @return 业务态请求体已缓存时返回 {@code true}
     */
    public boolean effectiveCached() {
        return this.effectiveBody.cached();
    }

    /**
     * <p>
     * 返回是否存在已缓存的传输态请求体。
     * </p>
     * @return 传输态请求体已缓存时返回 {@code true}
     */
    public boolean transportCached() {
        return this.transportBody.cached();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
