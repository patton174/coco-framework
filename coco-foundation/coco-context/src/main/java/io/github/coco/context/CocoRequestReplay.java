package io.github.coco.context;

import java.util.Map;
import java.util.Optional;

/**
 * 请求防重放视图。
 * <p>
 * 提供请求防重放标识、时间戳、随机串、窗口等信息的结构化访问，
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
public final class CocoRequestReplay {

    private final Map<String, String> attributes;

    CocoRequestReplay(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    /**
     * <p>
     * 返回请求是否带有防重放材料。
     * </p>
     * @return 带有防重放材料时返回 {@code true}
     */
    public boolean replayProtected() {
        return CocoRequestContextAttributeParser.booleanAttribute(this.attributes,
                CocoRequestContextAttributes.REQUEST_REPLAY_PROTECTED);
    }

    /**
     * <p>
     * 返回重放应用标识。
     * </p>
     * @return 重放应用标识；未设置时为空
     */
    public Optional<String> appId() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.REPLAY_APP_ID);
    }

    /**
     * <p>
     * 返回重放密钥标识。
     * </p>
     * @return 重放密钥标识；未设置时为空
     */
    public Optional<String> keyId() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.REPLAY_KEY_ID);
    }

    /**
     * <p>
     * 返回重放时间戳。
     * </p>
     * @return 重放时间戳；未设置时为空
     */
    public Optional<String> timestamp() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.REPLAY_TIMESTAMP);
    }

    /**
     * <p>
     * 返回重放随机串。
     * </p>
     * @return 重放随机串；未设置时为空
     */
    public Optional<String> nonce() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.REPLAY_NONCE);
    }

    /**
     * <p>
     * 返回请求防重放元数据来源。
     * </p>
     * @return 请求防重放元数据来源；未设置时为空
     */
    public Optional<String> metadataSource() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.REPLAY_METADATA_SOURCE);
    }

    /**
     * <p>
     * 返回请求防重放键是否已预占。
     * </p>
     * @return 防重放键已预占时返回 {@code true}
     */
    public boolean reserved() {
        return CocoRequestContextAttributeParser.booleanAttribute(this.attributes,
                CocoRequestContextAttributes.REPLAY_RESERVED);
    }

    /**
     * <p>
     * 返回请求防重放键过期时间。
     * </p>
     * @return 请求防重放键过期时间；未设置时为空
     */
    public Optional<String> expiresAt() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.REPLAY_EXPIRES_AT);
    }

    /**
     * <p>
     * 返回请求防重放窗口秒数。
     * </p>
     * @return 请求防重放窗口秒数；未设置时为空
     */
    public Optional<Long> windowSeconds() {
        return CocoRequestContextAttributeParser.longAttribute(this.attributes,
                CocoRequestContextAttributes.REPLAY_WINDOW_SECONDS);
    }

    /**
     * <p>
     * 返回请求防重放键 SHA-256 摘要。
     * </p>
     * @return 请求防重放键 SHA-256 摘要；未设置时为空
     */
    public Optional<String> keySha256() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.REPLAY_KEY_SHA256);
    }
}
