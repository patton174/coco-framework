package io.github.coco.spring.boot.autoconfigure.feature;

import java.util.Set;

import io.github.coco.api.feature.CocoFeature;

/**
 * Coco 功能管理器。
 * <p>
 * 提供运行时查询框架标准功能启用状态的统一入口。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-spring-boot-autoconfigure}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public interface CocoFeatureManager {

    /**
     * <p>
     * 判断指定 Coco 功能是否在当前应用中启用。
     * </p>
     * @param feature 需要判断的功能
     * @return 功能启用时返回 {@code true}
     */
    boolean isEnabled(CocoFeature feature);

    /**
     * <p>
     * 返回当前应用最终启用的 Coco 功能集合。
     * </p>
     * @return 启用功能集合
     */
    Set<CocoFeature> enabledFeatures();

    /**
     * <p>
     * 返回当前应用最终禁用或因依赖缺失而未启用的 Coco 功能集合。
     * </p>
     * @return 禁用功能集合
     */
    Set<CocoFeature> disabledFeatures();
}
