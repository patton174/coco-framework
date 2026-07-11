package io.github.coco.web.context.payload;

/**
 * <p>
 * Coco Web 请求体参数解析状态。
 * </p>
 * <p>
 * 用于区分请求体参数为什么可用或不可用，避免上层只能根据空参数集合猜测是没有请求体、未缓存还是被加密传输态阻断。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public enum CocoWebPayloadParseStatus {

    /**
     * 请求体参数解析能力已禁用。
     */
    DISABLED("disabled"),

    /**
     * 当前请求没有请求体。
     */
    NO_BODY("no-body"),

    /**
     * 请求体存在，但框架尚未缓存。
     */
    NOT_CACHED("not-cached"),

    /**
     * 请求体内容类型不在可解析范围内。
     */
    UNSUPPORTED_CONTENT_TYPE("unsupported-content-type"),

    /**
     * 当前仍处于加密传输态，暂不解析请求体参数。
     */
    ENCRYPTED_TRANSPORT("encrypted-transport"),

    /**
     * 请求体格式非法，当前无法完成参数解析。
     */
    MALFORMED_PAYLOAD("malformed-payload"),

    /**
     * 请求体参数在解析过程中达到数量上限，结果为部分参数快照。
     */
    PARAMETER_LIMIT_REACHED("parameter-limit-reached"),

    /**
     * JSON 请求体在解析过程中达到最大递归深度，结果为部分参数快照。
     */
    JSON_DEPTH_LIMIT_REACHED("json-depth-limit-reached"),

    /**
     * 请求体参数已经解析完成。
     */
    PARSED("parsed");

    private final String id;

    CocoWebPayloadParseStatus(String id) {
        this.id = id;
    }

    /**
     * <p>
     * 返回稳定状态标识。
     * </p>
     * @return 状态标识
     */
    public String id() {
        return this.id;
    }

    /**
     * <p>
     * 判断当前状态是否代表已有可消费的请求体参数视图。
     * </p>
     * @return 已有可消费参数视图时返回 {@code true}
     */
    public boolean available() {
        return this == PARSED || this == PARAMETER_LIMIT_REACHED || this == JSON_DEPTH_LIMIT_REACHED;
    }

    /**
     * <p>
     * 判断当前状态是否代表解析结果被限制条件截断。
     * </p>
     * @return 当前结果被截断时返回 {@code true}
     */
    public boolean truncated() {
        return this == PARAMETER_LIMIT_REACHED || this == JSON_DEPTH_LIMIT_REACHED;
    }
}
