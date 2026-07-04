package io.github.coco.api.feature;

import java.util.Set;

/**
 * Coco 功能注册配置。
 * <p>
 * 提供给 {@code CocoConfigurer} 使用，用于声明业务项目需要排除的框架能力。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-api-core}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public interface CocoFeatureRegistry {

    /**
     * <p>
     * 声明需要显式启用的 Coco 功能。
     * </p>
     * @param features 需要启用的功能
     * @return 当前注册器实例，便于链式调用
     */
    CocoFeatureRegistry include(CocoFeature... features);

    /**
     * <p>
     * 声明需要排除或禁用的 Coco 功能。
     * </p>
     * @param features 需要禁用的功能
     * @return 当前注册器实例，便于链式调用
     */
    CocoFeatureRegistry exclude(CocoFeature... features);

    /**
     * <p>
     * 声明需要显式启用的 Coco 功能。
     * </p>
     * <p>
     * 该方法是 {@link #include(CocoFeature...)} 的业务语义别名。
     * </p>
     * @param features 需要启用的功能
     * @return 当前注册器实例，便于链式调用
     */
    default CocoFeatureRegistry enable(CocoFeature... features) {
        return include(features);
    }

    /**
     * <p>
     * 声明需要禁用的 Coco 功能。
     * </p>
     * <p>
     * 该方法是 {@link #exclude(CocoFeature...)} 的业务语义别名。
     * </p>
     * @param features 需要禁用的功能
     * @return 当前注册器实例，便于链式调用
     */
    default CocoFeatureRegistry disable(CocoFeature... features) {
        return exclude(features);
    }

    /**
     * <p>
     * 判断指定功能是否被显式声明为启用。
     * </p>
     * @param feature 需要判断的功能
     * @return 已显式启用时返回 {@code true}
     */
    boolean isIncluded(CocoFeature feature);

    /**
     * <p>
     * 判断指定功能是否被显式声明为禁用。
     * </p>
     * @param feature 需要判断的功能
     * @return 已显式禁用时返回 {@code true}
     */
    boolean isExcluded(CocoFeature feature);

    /**
     * <p>
     * 返回当前注册器中显式启用的功能集合。
     * </p>
     * @return 显式启用的功能集合
     */
    Set<CocoFeature> includedFeatures();

    /**
     * <p>
     * 返回当前注册器中显式禁用的功能集合。
     * </p>
     * @return 显式禁用的功能集合
     */
    Set<CocoFeature> excludedFeatures();
}
