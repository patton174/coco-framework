package io.github.coco.api.feature;

import java.util.Set;

/**
 * Coco 功能注册配置。
 * <p>
 * 提供给 {@code CocoConfigurer} 使用，用于声明业务项目需要显式启用或禁用的框架能力。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-api-core}</li>
 * </ul>
 * @deprecated 该契约仅服务于已废弃的 {@code CocoConfigurer} Java 配置入口。业务项目应优先使用
 * {@code coco.features.*} 配置项或 {@link CocoFeatures} 注解。
 * @author patton174
 * @since 1.0.0
 */
@Deprecated(since = "1.0.0")
public interface CocoFeatureRegistry {

    /**
     * <p>
     * 声明需要显式启用的 Coco 功能。
     * </p>
     * @param features 需要启用的功能
     * @return 当前注册器实例，便于链式调用
     */
    CocoFeatureRegistry enable(CocoFeature... features);

    /**
     * <p>
     * 声明需要禁用的 Coco 功能。
     * </p>
     * @param features 需要禁用的功能
     * @return 当前注册器实例，便于链式调用
     */
    CocoFeatureRegistry disable(CocoFeature... features);

    /**
     * <p>
     * 判断指定功能是否被显式声明为启用。
     * </p>
     * @param feature 需要判断的功能
     * @return 已显式启用时返回 {@code true}
     */
    boolean isEnabled(CocoFeature feature);

    /**
     * <p>
     * 判断指定功能是否被显式声明为禁用。
     * </p>
     * @param feature 需要判断的功能
     * @return 已显式禁用时返回 {@code true}
     */
    boolean isDisabled(CocoFeature feature);

    /**
     * <p>
     * 返回当前注册器中显式启用的功能集合。
     * </p>
     * @return 显式启用的功能集合
     */
    Set<CocoFeature> enabledFeatures();

    /**
     * <p>
     * 返回当前注册器中显式禁用的功能集合。
     * </p>
     * @return 显式禁用的功能集合
     */
    Set<CocoFeature> disabledFeatures();
}
