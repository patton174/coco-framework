package io.github.coco.feature.runtime.condition;

import java.util.Map;

import io.github.coco.api.feature.CocoFeature;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Coco 功能条件判断器。
 * <p>
 * 根据 {@link ConditionalOnCocoFeature} 声明的功能标识判断当前自动配置或 Bean 是否应该生效。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-runtime}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
final class OnCocoFeatureCondition extends SpringBootCondition {

    /**
     * <p>
     * 根据 {@link ConditionalOnCocoFeature} 声明判断当前自动配置或 Bean 是否匹配。
     * </p>
     * @param context Spring 条件上下文
     * @param metadata 被判断目标的注解元数据
     * @return Spring Boot 条件匹配结果
     */
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(
                ConditionalOnCocoFeature.class.getName());
        CocoFeature feature = (CocoFeature) attributes.get("value");
        boolean enabled = new CocoRuntimeFeatureResolver()
                .resolve(context.getEnvironment(), context.getClassLoader())
                .isEnabled(feature);
        ConditionMessage.Builder message = ConditionMessage.forCondition(ConditionalOnCocoFeature.class, feature.id());
        return enabled
                ? ConditionOutcome.match(message.because("feature is enabled"))
                : ConditionOutcome.noMatch(message.because("feature is disabled"));
    }
}
