package io.github.coco.config;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.coco.api.CocoConfigurer;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.runtime.condition.CocoRuntimeFeatureResolver;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.feature.model.CocoFeaturePlan;
import io.github.coco.feature.model.CocoFeatureSelection;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Coco 配置自动装配。
 * <p>
 * 负责绑定 {@code coco} 配置，并合并业务侧提供的 {@code CocoConfigurer} Bean，生成运行期功能管理器。
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
@AutoConfiguration
@EnableConfigurationProperties(CocoProperties.class)
@SuppressWarnings("deprecation")
public class CocoConfigAutoConfiguration {

    private static final Log LOGGER = LogFactory.getLog(CocoConfigAutoConfiguration.class);

    /**
     * <p>
     * 创建 Coco 功能启用计划。
     * </p>
     * <p>
     * 返回启动早期已经供条件判断使用的单一计划，并校验 Bean 阶段才发现的 {@link CocoConfigurer} 和
     * {@code @CocoFeatures} 声明不会改变该计划。
     * </p>
     * @param properties Coco 配置属性
     * @param configurers 业务方提供的 Coco 配置器
     * @param beanFactory Spring Bean 工厂，用于查找注解声明
     * @param environment Spring 运行环境
     * @return 最终功能启用计划
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoFeaturePlan cocoFeaturePlan(CocoProperties properties, ObjectProvider<CocoConfigurer> configurers,
            ConfigurableListableBeanFactory beanFactory, Environment environment) {
        CocoRuntimeFeatureResolver resolver = new CocoRuntimeFeatureResolver();
        CocoFeatureSelection codeSelection = CocoFeatureSelectionCollector.collect(beanFactory, configurers);
        CocoFeaturePlan plan = resolver.resolveWithCodeSelection(
                environment, beanFactory.getBeanClassLoader(), codeSelection);
        logFeaturePlan("startup-plan", plan, properties.getFeatures().toSelection(), codeSelection);
        return plan;
    }

    /**
     * <p>
     * 基于最终功能启用计划创建运行期功能管理器。
     * </p>
     * @param featurePlan 最终功能启用计划
     * @return 功能管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoFeatureManager cocoFeatureManager(CocoFeaturePlan featurePlan) {
        return new DefaultCocoFeatureManager(featurePlan);
    }

    /**
     * <p>
     * 拒绝 Bean 阶段提供的自定义计划覆盖启动早期计划。
     * </p>
     * <p>
     * 条件判断无法读取普通 Bean，因此自定义计划即使值相同也不能替代启动早期持有的计划对象。
     * </p>
     * @param featurePlan Spring 容器最终暴露的功能计划 Bean
     * @param featureManager Spring 容器最终暴露的功能管理器
     * @param environment Spring 运行环境
     * @param beanFactory Spring Bean 工厂
     * @return 单例初始化校验器
     */
    @Bean
    public SmartInitializingSingleton cocoFeaturePlanConsistencyValidator(CocoFeaturePlan featurePlan,
            CocoFeatureManager featureManager, Environment environment,
            ConfigurableListableBeanFactory beanFactory) {
        CocoFeaturePlan startupPlan = new CocoRuntimeFeatureResolver()
                .resolve(environment, beanFactory.getBeanClassLoader());
        if (featurePlan != startupPlan) {
            throw new IllegalStateException("A custom CocoFeaturePlan bean cannot replace the startup feature plan "
                    + "after conditions were evaluated. Express feature selection through coco.features.* or "
                    + "@CocoFeatures on a SpringApplication primary source.");
        }
        if (!startupPlan.enabledFeatures().equals(featureManager.enabledFeatures())
                || !startupPlan.disabledFeatures().equals(featureManager.disabledFeatures())) {
            throw inconsistentFeatureManager();
        }
        for (CocoFeature feature : CocoFeature.values()) {
            if (featureManager.isEnabled(feature) != startupPlan.isEnabled(feature)) {
                throw inconsistentFeatureManager();
            }
        }
        return () -> {
        };
    }

    private IllegalStateException inconsistentFeatureManager() {
        return new IllegalStateException("A custom CocoFeatureManager bean disagrees with the startup "
                + "feature plan after conditions were evaluated.");
    }

    /**
     * <p>
     * 注册配置模块内置的国际化消息资源。
     * </p>
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoConfigMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoConfigMessageBundleRegistrar() {
        return registry -> registry.add("coco-config-messages");
    }

    private static void logFeaturePlan(String source, CocoFeaturePlan plan, CocoFeatureSelection propertySelection,
            CocoFeatureSelection codeSelection) {
        if (!LOGGER.isInfoEnabled()) {
            return;
        }
        LOGGER.info("Coco features resolved from " + source
                + ": enabled=" + featureIds(plan.enabledFeatures())
                + ", disabled=" + featureIds(plan.disabledFeatures())
                + ", disabledByDependency=" + featureIds(disabledByDependencyFeatures(plan,
                        propertySelection.merge(codeSelection)))
                + ", propertySelection=" + describeSelection(propertySelection)
                + ", codeSelection=" + describeSelection(codeSelection) + ".");
    }

    private static String describeSelection(CocoFeatureSelection selection) {
        CocoFeatureSelection target = selection == null ? CocoFeatureSelection.empty() : selection;
        return "{enabled=" + featureIds(target.enabled()) + ", disabled=" + featureIds(target.disabled()) + "}";
    }

    private static Set<CocoFeature> disabledByDependencyFeatures(CocoFeaturePlan plan,
            CocoFeatureSelection selection) {
        EnumSet<CocoFeature> disabledByDependency = EnumSet.noneOf(CocoFeature.class);
        for (io.github.coco.feature.model.CocoFeatureDefinition definition : plan.definitions()) {
            if (plan.disabledFeatures().contains(definition.feature())
                    && !selection.disabled().contains(definition.feature())
                    && !plan.enabledFeatures().containsAll(definition.dependencies())) {
                disabledByDependency.add(definition.feature());
            }
        }
        return disabledByDependency.isEmpty() ? Set.of() : Set.copyOf(disabledByDependency);
    }

    private static String featureIds(Set<CocoFeature> features) {
        if (features == null || features.isEmpty()) {
            return "[]";
        }
        return features.stream()
                .map(CocoFeature::id)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
