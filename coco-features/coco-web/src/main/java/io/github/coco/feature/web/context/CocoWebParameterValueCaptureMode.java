package io.github.coco.feature.web.context;

/**
 * Coco Web 请求参数值采集模式。
 * <p>
 * 该模式只影响普通请求上下文和访问日志使用的清洗视图，不影响签名、加密和防重放使用的原始安全输入视图。
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
public enum CocoWebParameterValueCaptureMode {

    /**
     * <p>
     * 仅保留参数名，所有参数值均替换为占位掩码。
     * </p>
     */
    METADATA_ONLY,

    /**
     * <p>
     * 仅采集显式允许名单中的参数值，其他参数值替换为占位掩码。
     * </p>
     */
    ALLOW_LIST,

    /**
     * <p>
     * 采集所有未命中敏感参数名单的参数值，用于显式恢复旧版行为。
     * </p>
     */
    ALL
}
