package io.github.coco.api.feature;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Coco 功能配置注解。
 * <p>
 * 业务方可以标注在 Spring 配置类上，用代码方式声明需要显式启用或禁用的框架能力。
 * </p>
 * <p>
 * 当同一个配置源同时声明启用和禁用同一功能时，禁用声明优先生效。
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
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CocoFeatures {

    /**
     * <p>
     * 显式启用的 Coco 功能。
     * </p>
     * @return 需要启用的功能列表
     */
    CocoFeature[] enabled() default {};

    /**
     * <p>
     * 显式禁用的 Coco 功能。
     * </p>
     * <p>
     * 当同一配置源同时声明启用和禁用同一功能时，禁用声明优先生效。
     * </p>
     * @return 需要禁用的功能列表
     */
    CocoFeature[] disabled() default {};
}
