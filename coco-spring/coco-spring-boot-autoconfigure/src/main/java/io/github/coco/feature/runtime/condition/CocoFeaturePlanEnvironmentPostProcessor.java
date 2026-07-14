package io.github.coco.feature.runtime.condition;

import java.util.EnumSet;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.api.feature.CocoFeatures;
import io.github.coco.feature.model.CocoFeatureSelection;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Coco 功能计划环境后处理器。
 * <p>
 * 在 Spring Boot 完成 Config Data 加载后收集标注在 {@link SpringApplication} 主源类上的
 * {@link CocoFeatures} 声明，并注册上下文初始化器。初始化器会在所有环境后处理器完成后、配置类条件判断前，
 * 合并最终运行期有效配置和构建清单，冻结唯一功能计划。
 * </p>
 * @author patton174
 * @since 2.0.0
 */
public final class CocoFeaturePlanEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * {@inheritDoc}
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        CocoFeatureSelection codeSelection = collectPrimarySourceSelection(application);
        ApplicationContextInitializer<ConfigurableApplicationContext> initializer = context ->
                new CocoRuntimeFeatureResolver().initialize(
                        context.getEnvironment(), context.getClassLoader(), codeSelection);
        application.addInitializers(initializer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    private CocoFeatureSelection collectPrimarySourceSelection(SpringApplication application) {
        EnumSet<CocoFeature> enabled = EnumSet.noneOf(CocoFeature.class);
        EnumSet<CocoFeature> disabled = EnumSet.noneOf(CocoFeature.class);
        for (Object source : application.getAllSources()) {
            if (!(source instanceof Class<?> sourceClass)) {
                continue;
            }
            CocoFeatures features = sourceClass.getAnnotation(CocoFeatures.class);
            if (features != null) {
                addAll(enabled, features.enabled());
                addAll(disabled, features.disabled());
            }
        }
        return CocoFeatureSelection.of(enabled, disabled);
    }

    private void addAll(EnumSet<CocoFeature> target, CocoFeature[] features) {
        if (features == null) {
            return;
        }
        for (CocoFeature feature : features) {
            if (feature != null) {
                target.add(feature);
            }
        }
    }
}
