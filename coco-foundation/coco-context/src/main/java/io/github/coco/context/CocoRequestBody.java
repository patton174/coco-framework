package io.github.coco.context;

import java.util.Map;
import java.util.Optional;

/**
 * 请求体视图。
 * <p>
 * 提供请求体摘要、长度和阶段等信息的结构化访问，
 * 属性来源于 {@link CocoRequestContext} 的内部属性快照。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-context}</li>
 * </ul>
 * @author patton174
 * @since 1.1.0
 */
public final class CocoRequestBody {

    private final Map<String, String> attributes;

    CocoRequestBody(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    /**
     * <p>
     * 返回请求体 SHA-256 摘要。
     * </p>
     * @return 请求体 SHA-256 摘要；未设置时为空
     */
    public Optional<String> sha256() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.REQUEST_BODY_SHA256);
    }

    /**
     * <p>
     * 返回传输态请求体 SHA-256 摘要。
     * </p>
     * @return 传输态请求体 SHA-256 摘要；未设置时为空
     */
    public Optional<String> transportSha256() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.REQUEST_BODY_TRANSPORT_SHA256);
    }

    /**
     * <p>
     * 返回业务态请求体 SHA-256 摘要。
     * </p>
     * @return 业务态请求体 SHA-256 摘要；未设置时为空
     */
    public Optional<String> effectiveSha256() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.REQUEST_BODY_EFFECTIVE_SHA256)
                .or(this::sha256);
    }

    /**
     * <p>
     * 返回传输态请求体长度。
     * </p>
     * @return 传输态请求体长度；未设置时为空
     */
    public Optional<Long> transportLength() {
        return CocoRequestContextAttributeParser.longAttribute(this.attributes,
                CocoRequestContextAttributes.REQUEST_BODY_TRANSPORT_LENGTH);
    }

    /**
     * <p>
     * 返回业务态请求体长度。
     * </p>
     * @return 业务态请求体长度；未设置时为空
     */
    public Optional<Long> effectiveLength() {
        return CocoRequestContextAttributeParser.longAttribute(this.attributes,
                CocoRequestContextAttributes.REQUEST_BODY_EFFECTIVE_LENGTH);
    }

    /**
     * <p>
     * 返回请求体阶段。
     * </p>
     * @return 请求体阶段；未设置时为空
     */
    public Optional<String> stage() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.REQUEST_BODY_STAGE);
    }
}
