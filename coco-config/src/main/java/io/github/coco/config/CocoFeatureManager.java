package io.github.coco.config;

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
 *   <li>模块：{@code coco-config}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public interface CocoFeatureManager {

    boolean isEnabled(CocoFeature feature);

    Set<CocoFeature> enabledFeatures();

    Set<CocoFeature> excludedFeatures();
}
