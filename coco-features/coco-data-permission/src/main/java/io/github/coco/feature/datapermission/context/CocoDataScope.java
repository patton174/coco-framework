package io.github.coco.feature.datapermission.context;

/**
 * Coco 数据权限范围。
 * <p>
 * 表达数据访问边界，不绑定 SQL 方言或具体组织模型。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public enum CocoDataScope {

    /**
     * 允许访问全部数据。
     */
    ALL,

    /**
     * 仅允许访问本人数据。
     */
    SELF,

    /**
     * 允许访问自定义范围数据。
     */
    CUSTOM,

    /**
     * 拒绝访问数据。
     */
    DENY
}
