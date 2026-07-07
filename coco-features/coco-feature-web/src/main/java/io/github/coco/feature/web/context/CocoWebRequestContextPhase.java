package io.github.coco.feature.web.context;

/**
 * <p>
 * Coco Web 请求上下文阶段。
 * </p>
 * <p>
 * 用于表达当前请求已经经过的框架安全处理阶段，便于业务、日志、审计和后续安全能力读取一致的生命周期状态。
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
public enum CocoWebRequestContextPhase {

    /**
     * 请求已完成传输态上下文采集。
     */
    TRANSPORT_CAPTURED("transport-captured"),

    /**
     * 请求已通过签名校验。
     */
    SIGNATURE_VERIFIED("signature-verified"),

    /**
     * 请求已完成解密并切换为业务可见明文。
     */
    DECRYPTED("decrypted"),

    /**
     * 请求已通过防重放校验。
     */
    REPLAY_VERIFIED("replay-verified");

    private final String id;

    CocoWebRequestContextPhase(String id) {
        this.id = id;
    }

    /**
     * <p>
     * 返回稳定阶段标识。
     * </p>
     * @return 阶段标识
     */
    public String id() {
        return this.id;
    }

    /**
     * <p>
     * 返回当前阶段与目标阶段中更靠后的阶段。
     * </p>
     * @param candidate 目标阶段
     * @return 合并后的阶段
     */
    public CocoWebRequestContextPhase merge(CocoWebRequestContextPhase candidate) {
        if (candidate == null || candidate.ordinal() < this.ordinal()) {
            return this;
        }
        return candidate;
    }
}
