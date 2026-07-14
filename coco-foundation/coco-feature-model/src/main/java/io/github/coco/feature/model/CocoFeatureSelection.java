package io.github.coco.feature.model;

import java.util.EnumSet;
import java.util.Set;

import io.github.coco.api.feature.CocoFeature;

/**
 * Coco 功能选择声明。
 * <p>
 * 描述某个配置源显式启用和禁用的功能集合，不直接执行依赖传播。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-model}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public record CocoFeatureSelection(Set<CocoFeature> enabled, Set<CocoFeature> disabled) {

    /**
     * <p>
     * 创建功能选择声明，并将输入集合复制为不可变集合。
     * </p>
     * @param enabled 显式启用的功能集合
     * @param disabled 显式禁用的功能集合
     */
    public CocoFeatureSelection {
        enabled = copy(enabled);
        disabled = copy(disabled);
    }

    /**
     * <p>
     * 返回显式启用的功能集合副本。
     * </p>
     * @return 显式启用的功能集合
     */
    public Set<CocoFeature> enabled() {
        return Set.copyOf(this.enabled);
    }

    /**
     * <p>
     * 返回显式禁用的功能集合副本。
     * </p>
     * @return 显式禁用的功能集合
     */
    public Set<CocoFeature> disabled() {
        return Set.copyOf(this.disabled);
    }

    /**
     * <p>
     * 创建不包含任何显式启用或禁用声明的选择。
     * </p>
     * @return 空功能选择
     */
    public static CocoFeatureSelection empty() {
        return new CocoFeatureSelection(Set.of(), Set.of());
    }

    /**
     * <p>
     * 从显式启用和禁用集合创建功能选择声明。
     * </p>
     * <p>
     * 配置文件、注解扫描、Maven 参数等输入源应先转换为该核心模型，再交给功能解析器处理。
     * </p>
     * @param enabled 显式启用的功能集合
     * @param disabled 显式禁用的功能集合
     * @return 功能选择
     */
    public static CocoFeatureSelection of(Set<CocoFeature> enabled, Set<CocoFeature> disabled) {
        return new CocoFeatureSelection(enabled, disabled);
    }

    /**
     * <p>
     * 从注解数组创建功能选择声明。
     * </p>
     * @param enabled 显式启用的功能数组
     * @param disabled 显式禁用的功能数组
     * @return 功能选择
     */
    public static CocoFeatureSelection of(CocoFeature[] enabled, CocoFeature[] disabled) {
        return of(copy(enabled), copy(disabled));
    }

    /**
     * <p>
     * 创建只包含启用声明的功能选择。
     * </p>
     * @param enabled 显式启用的功能集合
     * @return 功能选择
     */
    public static CocoFeatureSelection ofEnabled(Set<CocoFeature> enabled) {
        return new CocoFeatureSelection(enabled, Set.of());
    }

    /**
     * <p>
     * 创建只包含禁用声明的功能选择。
     * </p>
     * @param disabled 显式禁用的功能集合
     * @return 功能选择
     */
    public static CocoFeatureSelection ofDisabled(Set<CocoFeature> disabled) {
        return new CocoFeatureSelection(Set.of(), disabled);
    }

    /**
     * <p>
     * 合并两个功能选择声明。
     * </p>
     * <p>
     * 禁用是安全性收缩操作：无论声明来源和合并顺序如何，同一功能同时出现启用与禁用时始终以禁用为准。
     * 因此 profile、外部配置或命令行中的显式禁用不会被代码或注解的启用声明重新打开。
     * </p>
     * @param first 第一个选择
     * @param second 第二个选择
     * @return 合并后的功能选择
     */
    public static CocoFeatureSelection merge(CocoFeatureSelection first,
            CocoFeatureSelection second) {
        EnumSet<CocoFeature> mergedEnabled = EnumSet.noneOf(CocoFeature.class);
        EnumSet<CocoFeature> mergedDisabled = EnumSet.noneOf(CocoFeature.class);
        addAll(mergedEnabled, first == null ? null : first.enabled());
        addAll(mergedEnabled, second == null ? null : second.enabled());
        addAll(mergedDisabled, first == null ? null : first.disabled());
        addAll(mergedDisabled, second == null ? null : second.disabled());
        mergedEnabled.removeAll(mergedDisabled);
        return new CocoFeatureSelection(mergedEnabled, mergedDisabled);
    }

    /**
     * <p>
     * 将当前选择与另一选择合并，冲突时禁用优先。
     * </p>
     * @param other 另一选择
     * @return 合并后的功能选择
     */
    public CocoFeatureSelection merge(CocoFeatureSelection other) {
        return merge(this, other);
    }

    private static void addAll(EnumSet<CocoFeature> target, Set<CocoFeature> source) {
        if (source != null) {
            target.addAll(source);
        }
    }

    private static Set<CocoFeature> copy(Set<CocoFeature> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(EnumSet.copyOf(source));
    }

    private static Set<CocoFeature> copy(CocoFeature[] source) {
        if (source == null || source.length == 0) {
            return Set.of();
        }
        EnumSet<CocoFeature> copied = EnumSet.noneOf(CocoFeature.class);
        for (CocoFeature feature : source) {
            if (feature != null) {
                copied.add(feature);
            }
        }
        return copied.isEmpty() ? Set.of() : Set.copyOf(copied);
    }
}
