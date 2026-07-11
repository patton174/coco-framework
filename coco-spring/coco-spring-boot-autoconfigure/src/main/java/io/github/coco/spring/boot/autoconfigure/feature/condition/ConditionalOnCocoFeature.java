package io.github.coco.spring.boot.autoconfigure.feature.condition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.github.coco.api.feature.CocoFeature;
import org.springframework.context.annotation.Conditional;

/**
 * Coco 功能条件注解。
 * <p>
 * 标注在自动配置类或 Bean 方法上，用于根据最终功能启用结果决定是否装配对应能力。
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
@Documented
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Conditional(OnCocoFeatureCondition.class)
public @interface ConditionalOnCocoFeature {

    /**
     * <p>
     * 指定当前自动配置或 Bean 方法依赖的 Coco 功能。
     * </p>
     * @return 需要处于启用状态的功能
     */
    CocoFeature value();
}
