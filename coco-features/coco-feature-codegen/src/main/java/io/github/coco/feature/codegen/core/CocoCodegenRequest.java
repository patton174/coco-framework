package io.github.coco.feature.codegen.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Coco 代码生成请求。
 * <p>
 * 表示一次代码生成调用的模板组、目标包名和扩展上下文。该对象不绑定数据库表或具体模板引擎。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-codegen}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoCodegenRequest {

    private final String templateGroup;

    private final String targetPackage;

    private final Map<String, Object> attributes;

    private CocoCodegenRequest(Builder builder) {
        this.templateGroup = requireText(builder.templateGroup, "templateGroup");
        this.targetPackage = normalize(builder.targetPackage);
        this.attributes = normalizeAttributes(builder.attributes);
    }

    /**
     * <p>
     * 创建代码生成请求构建器。
     * </p>
     * @param templateGroup 模板组
     * @return 代码生成请求构建器
     */
    public static Builder builder(String templateGroup) {
        return new Builder(templateGroup);
    }

    /**
     * <p>
     * 返回模板组。
     * </p>
     * @return 模板组
     */
    public String templateGroup() {
        return this.templateGroup;
    }

    /**
     * <p>
     * 返回目标包名。
     * </p>
     * @return 目标包名；不存在时为空
     */
    public String targetPackage() {
        return this.targetPackage;
    }

    /**
     * <p>
     * 返回扩展上下文。
     * </p>
     * @return 扩展上下文
     */
    public Map<String, Object> attributes() {
        return this.attributes;
    }

    private static String requireText(String value, String name) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, Object> normalizeAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        attributes.forEach((name, value) -> {
            String normalizedName = normalize(name);
            if (normalizedName != null && value != null) {
                normalized.put(normalizedName, value);
            }
        });
        return Collections.unmodifiableMap(normalized);
    }

    /**
     * Coco 代码生成请求构建器。
     * <p>
     * 以链式方式设置代码生成请求。
     * </p>
     * <p>
     * 项目信息：
     * </p>
     * <ul>
     *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
     *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
     *   <li>模块：{@code coco-feature-codegen}</li>
     * </ul>
     * @author patton174
     * @since 1.0.0
     */
    public static final class Builder {

        private final String templateGroup;

        private String targetPackage;

        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private Builder(String templateGroup) {
            this.templateGroup = templateGroup;
        }

        /**
         * <p>
         * 设置目标包名。
         * </p>
         * @param targetPackage 目标包名
         * @return 当前构建器
         */
        public Builder targetPackage(String targetPackage) {
            this.targetPackage = targetPackage;
            return this;
        }

        /**
         * <p>
         * 添加扩展上下文属性。
         * </p>
         * @param name 属性名称
         * @param value 属性值
         * @return 当前构建器
         */
        public Builder attribute(String name, Object value) {
            String normalizedName = normalize(name);
            if (normalizedName != null && value != null) {
                this.attributes.put(normalizedName, value);
            }
            return this;
        }

        /**
         * <p>
         * 构建代码生成请求。
         * </p>
         * @return 代码生成请求
         */
        public CocoCodegenRequest build() {
            return new CocoCodegenRequest(this);
        }
    }
}
