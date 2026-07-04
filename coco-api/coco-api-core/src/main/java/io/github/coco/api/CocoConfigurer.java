package io.github.coco.api;

import io.github.coco.api.feature.CocoFeatureRegistry;

/**
 * Coco 配置入口。
 * <p>
 * 业务项目通过实现该接口，以接近 Spring {@code WebMvcConfigurer} 的方式配置 Coco。
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
public interface CocoConfigurer {

    /**
     * <p>
     * 配置 Coco 标准功能的启用或禁用声明。
     * </p>
     * <p>
     * 业务项目可以通过该方法在 Java 配置类中集中声明功能开关，框架会在启动阶段与配置文件和注解声明合并。
     * </p>
     * @param features 功能注册器
     */
    default void configureFeatures(CocoFeatureRegistry features) {
    }
}
