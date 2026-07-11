package io.github.coco.web.request.metadata;

/**
 * Coco Web 安全元数据来源�? * <p>
 * 定义 Sign、AES 和防重放协议材料从请求头、请求参数或二者组合中解析的策略�? * </p>
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
public enum CocoWebSecurityMetadataSource {

    /**
     * <p>
     * 仅从请求头解析安全元数据�?     * </p>
     */
    HEADER,

    /**
     * <p>
     * 仅从请求参数解析安全元数据�?     * </p>
     */
    PARAMETER,

    /**
     * <p>
     * 优先从请求头解析，缺失时退回请求参数�?     * </p>
     */
    HEADER_THEN_PARAMETER,

    /**
     * <p>
     * 优先从请求参数解析，缺失时退回请求头�?     * </p>
     */
    PARAMETER_THEN_HEADER;

    /**
     * <p>
     * 返回当前策略是否允许读取请求头�?     * </p>
     * @return 允许读取请求头时返回 {@code true}
     */
    public boolean supportsHeader() {
        return this == HEADER || this == HEADER_THEN_PARAMETER || this == PARAMETER_THEN_HEADER;
    }

    /**
     * <p>
     * 返回当前策略是否允许读取请求参数�?     * </p>
     * @return 允许读取请求参数时返�?{@code true}
     */
    public boolean supportsParameter() {
        return this == PARAMETER || this == HEADER_THEN_PARAMETER || this == PARAMETER_THEN_HEADER;
    }

    /**
     * <p>
     * 返回当前策略是否优先读取请求参数�?     * </p>
     * @return 优先读取请求参数时返�?{@code true}
     */
    public boolean parameterFirst() {
        return this == PARAMETER || this == PARAMETER_THEN_HEADER;
    }
}
