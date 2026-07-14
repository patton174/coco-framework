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
 *   <li>模块：{@code coco-api}</li>
 * </ul>
 * @deprecated 功能选择建议优先使用 {@code coco.features.*} 配置项或将
 * {@link io.github.coco.api.feature.CocoFeatures} 标注在 SpringApplication 主源类上。该 Bean 回调发生在功能条件
 * 已经判断之后，仅保留源代码和二进制兼容；当回调试图改变启动早期计划时，应用会显式启动失败。
 * @author patton174
 * @since 1.0.0
 */
@Deprecated(since = "1.0.0")
public interface CocoConfigurer {

    /**
     * <p>
     * 配置 Coco 标准功能的启用或禁用声明。
     * </p>
     * <p>
     * 业务项目可以通过该方法保留旧版功能开关声明。只有声明与启动早期计划一致时才能继续启动；需要改变计划时，
     * 请迁移到 {@code coco.features.*} 或主源类上的 {@code @CocoFeatures}。
     * </p>
     * @param features 功能注册器
     */
    default void configureFeatures(CocoFeatureRegistry features) {
    }
}
