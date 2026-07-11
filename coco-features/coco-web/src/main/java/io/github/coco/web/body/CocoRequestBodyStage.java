package io.github.coco.web.body;

/**
 * Coco 请求体阶段。
 * <p>
 * 标识当前请求体摘要对应的语义阶段，供签名、加密、审计和日志能力区分传输态请求体与业务实际可见请求体。
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
public enum CocoRequestBodyStage {

    /**
     * 请求体未缓存或不存在。
     */
    NONE("none"),

    /**
     * 当前请求体仍为传输态请求体。
     */
    TRANSPORT("transport"),

    /**
     * 当前请求体已经被框架转换为业务态明文请求体。
     */
    DECRYPTED("decrypted");

    private final String id;

    CocoRequestBodyStage(String id) {
        this.id = id;
    }

    /**
     * <p>
     * 返回稳定的阶段标识。
     * </p>
     * @return 阶段标识
     */
    public String id() {
        return this.id;
    }
}
