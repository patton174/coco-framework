package io.github.coco.feature.runtime.condition;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.model.CocoFeatureManifest;
import io.github.coco.feature.model.CocoFeatureManifestLoader;
import io.github.coco.feature.model.CocoFeaturePlan;
import io.github.coco.feature.model.CocoFeatureSelection;
import io.github.coco.feature.model.StandardCocoFeatures;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * Coco 运行期功能解析器。
 * <p>
 * 在 Spring 配置类条件判断前计算并缓存单一功能计划。构建清单限定产物中可用的能力，profile、外部配置、
 * 命令行和启动早期代码选择可以在该边界内继续缩小最终运行计划。
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
public final class CocoRuntimeFeatureResolver {

    private static final String STATE_PROPERTY_SOURCE_NAME = "cocoFeaturePlanState";

    private static final String STATE_PROPERTY_NAME = "coco.internal.feature-plan-state";

    /**
     * <p>
     * 在启动早期初始化当前 Spring 环境唯一的功能计划。
     * </p>
     * @param environment Spring 可配置环境
     * @param classLoader 用于读取构建清单的类加载器
     * @param earlyCodeSelection 可在配置类解析前读取的代码选择
     * @return 当前启动使用的单一功能计划
     */
    public CocoFeaturePlan initialize(ConfigurableEnvironment environment, ClassLoader classLoader,
            CocoFeatureSelection earlyCodeSelection) {
        if (environment == null) {
            return createState(null, classLoader, earlyCodeSelection).plan();
        }
        synchronized (environment) {
            RuntimeFeatureState existing = state(environment);
            CocoFeatureSelection requestedCodeSelection = selectionOrEmpty(earlyCodeSelection);
            if (existing != null) {
                if (!existing.earlyCodeSelection().equals(requestedCodeSelection)) {
                    throw new IllegalStateException("Coco feature plan was already initialized with a different "
                            + "startup code selection.");
                }
                return existing.plan();
            }
            RuntimeFeatureState resolved = createState(environment, classLoader, requestedCodeSelection);
            environment.getPropertySources().addFirst(new MapPropertySource(
                    STATE_PROPERTY_SOURCE_NAME, Map.of(STATE_PROPERTY_NAME, resolved)));
            return resolved.plan();
        }
    }

    /**
     * <p>
     * 返回当前应用运行期唯一的最终功能计划。
     * </p>
     * <p>
     * 标准 Spring Boot 启动会由环境后处理器预先初始化计划；测试或手工创建的上下文没有执行该扩展点时，
     * 首次调用会按环境配置惰性初始化同一份计划。
     * </p>
     * @param environment Spring 环境
     * @param classLoader 用于读取构建期清单的类加载器
     * @return 最终功能启用计划
     */
    public CocoFeaturePlan resolve(Environment environment, ClassLoader classLoader) {
        RuntimeFeatureState existing = state(environment);
        if (existing != null) {
            return existing.plan();
        }
        if (environment instanceof ConfigurableEnvironment configurableEnvironment) {
            return initialize(configurableEnvironment, classLoader, CocoFeatureSelection.empty());
        }
        return createState(environment, classLoader, CocoFeatureSelection.empty()).plan();
    }

    /**
     * <p>
     * 解析功能计划，并兼容没有执行 Spring Boot 环境后处理器的手工测试上下文。
     * </p>
     * <p>
     * 若计划尚未初始化，Bean 阶段收集到的旧版代码选择可参与首次解析；若条件判断或标准 Spring Boot
     * 启动已经初始化计划，则该选择只能与现有计划一致。
     * </p>
     * @param environment Spring 环境
     * @param classLoader 用于读取构建清单的类加载器
     * @param codeSelection Bean 阶段收集到的代码选择
     * @return 当前上下文使用的功能计划
     */
    public CocoFeaturePlan resolveWithCodeSelection(Environment environment, ClassLoader classLoader,
            CocoFeatureSelection codeSelection) {
        RuntimeFeatureState existing = state(environment);
        if (existing == null) {
            if (environment instanceof ConfigurableEnvironment configurableEnvironment) {
                return initialize(configurableEnvironment, classLoader, selectionOrEmpty(codeSelection));
            }
            return createState(environment, classLoader, selectionOrEmpty(codeSelection)).plan();
        }
        validateLateCodeSelection(environment, classLoader, codeSelection);
        return existing.plan();
    }

    /**
     * <p>
     * 校验 Bean 创建阶段才发现的代码选择不会改变启动早期计划。
     * </p>
     * @param environment Spring 环境
     * @param classLoader 用于读取构建清单的类加载器
     * @param lateCodeSelection Bean 阶段收集到的 {@code CocoConfigurer/@CocoFeatures} 选择
     * @throws IllegalStateException 晚期选择会改变已用于条件判断的功能计划时抛出
     */
    public void validateLateCodeSelection(Environment environment, ClassLoader classLoader,
            CocoFeatureSelection lateCodeSelection) {
        RuntimeFeatureState current = state(environment);
        if (current == null) {
            resolve(environment, classLoader);
            current = state(environment);
        }
        if (current == null) {
            return;
        }
        CocoFeatureSelection combinedCodeSelection = current.earlyCodeSelection()
                .merge(selectionOrEmpty(lateCodeSelection));
        CocoFeatureSelection combinedRuntimeSelection = current.environmentSelection()
                .merge(combinedCodeSelection);
        CocoFeaturePlan candidate = current.manifest() == null
                ? StandardCocoFeatures.resolve(combinedRuntimeSelection)
                : StandardCocoFeatures.resolveRuntimePlan(current.manifest(), combinedRuntimeSelection);
        if (!candidate.equals(current.plan())) {
            throw new IllegalStateException("CocoConfigurer or @CocoFeatures changed the feature plan after startup "
                    + "conditions were evaluated. Move the selection to coco.features.* or place @CocoFeatures "
                    + "on a SpringApplication primary source so it can participate in the startup feature plan.");
        }
    }

    private RuntimeFeatureState createState(Environment environment, ClassLoader classLoader,
            CocoFeatureSelection earlyCodeSelection) {
        CocoFeatureSelection environmentSelection = resolveFromEnvironment(environment);
        CocoFeatureSelection runtimeSelection = environmentSelection.merge(selectionOrEmpty(earlyCodeSelection));
        CocoFeatureManifest manifest = CocoFeatureManifestLoader.load(classLoader).orElse(null);
        CocoFeaturePlan plan = manifest == null
                ? StandardCocoFeatures.resolve(runtimeSelection)
                : StandardCocoFeatures.resolveRuntimePlan(manifest, runtimeSelection);
        return new RuntimeFeatureState(manifest, environmentSelection,
                selectionOrEmpty(earlyCodeSelection), plan);
    }

    private CocoFeatureSelection resolveFromEnvironment(Environment environment) {
        if (environment == null) {
            return CocoFeatureSelection.empty();
        }
        return CocoFeatureSelection.of(
                bind(environment, "coco.features.enabled"),
                new LinkedHashSet<>(bind(environment, "coco.features.disabled")));
    }

    private Set<CocoFeature> bind(Environment environment, String propertyName) {
        return Binder.get(environment)
                .bind(propertyName, Bindable.setOf(CocoFeature.class))
                .orElse(Set.of());
    }

    private RuntimeFeatureState state(Environment environment) {
        if (!(environment instanceof ConfigurableEnvironment configurableEnvironment)) {
            return null;
        }
        PropertySource<?> propertySource = configurableEnvironment.getPropertySources()
                .get(STATE_PROPERTY_SOURCE_NAME);
        if (propertySource == null) {
            return null;
        }
        Object value = propertySource.getProperty(STATE_PROPERTY_NAME);
        return value instanceof RuntimeFeatureState runtimeFeatureState ? runtimeFeatureState : null;
    }

    private CocoFeatureSelection selectionOrEmpty(CocoFeatureSelection selection) {
        return selection == null ? CocoFeatureSelection.empty() : selection;
    }

    private record RuntimeFeatureState(
            CocoFeatureManifest manifest,
            CocoFeatureSelection environmentSelection,
            CocoFeatureSelection earlyCodeSelection,
            CocoFeaturePlan plan) {
    }
}
