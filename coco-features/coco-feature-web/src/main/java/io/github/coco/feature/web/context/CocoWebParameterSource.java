package io.github.coco.feature.web.context;

/**
 * Coco Web 请求参数来源。
 * <p>
 * 用于在统一参数快照中区分合并参数、查询参数和请求体参数，避免上层安全能力直接关心 Servlet
 * 参数聚合规则。
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
public enum CocoWebParameterSource {

    /**
     * 无请求体参数。
     */
    NONE,

    /**
     * 合并后的请求参数视图。
     */
    MERGED,

    /**
     * 查询字符串参数视图。
     */
    QUERY,

    /**
     * 请求体参数视图。
     */
    PAYLOAD,

    /**
     * 表单请求体参数视图。
     */
    FORM,

    /**
     * JSON 请求体参数视图。
     */
    JSON;

    /**
     * <p>
     * 判断当前来源是否属于请求体参数来源。
     * </p>
     * @return 属于请求体参数来源时返回 {@code true}
     */
    public boolean payload() {
        return this == PAYLOAD || this == FORM || this == JSON;
    }
}
