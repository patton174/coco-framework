package io.github.coco.feature.codegen;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco 代码生成功能配置属性。
 * <p>
 * 绑定 {@code coco.codegen} 命名空间，控制代码生成基础设施和模板读取默认设置。
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
@ConfigurationProperties(prefix = "coco.codegen")
public class CocoCodegenProperties {

    private boolean enabled = true;

    @NestedConfigurationProperty
    private TemplateProperties templates = new TemplateProperties();

    /**
     * <p>
     * 返回是否启用代码生成基础设施。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用代码生成基础设施。
     * </p>
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回模板配置。
     * </p>
     * @return 模板配置
     */
    public TemplateProperties getTemplates() {
        return this.templates;
    }

    /**
     * <p>
     * 设置模板配置。
     * </p>
     * @param templates 模板配置
     */
    public void setTemplates(TemplateProperties templates) {
        this.templates = templates == null ? new TemplateProperties() : templates;
    }

    /**
     * Coco 代码生成模板配置。
     * <p>
     * 定义 FreeMarker 模板根位置和模板文件编码，业务项目可通过属性替换内置模板。
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
    public static class TemplateProperties {

        private String location = "classpath:/coco/codegen/templates";

        private String encoding = "UTF-8";

        /**
         * <p>
         * 返回模板位置。
         * </p>
         * @return 模板位置
         */
        public String getLocation() {
            return this.location;
        }

        /**
         * <p>
         * 设置模板位置。
         * </p>
         * @param location 模板位置
         */
        public void setLocation(String location) {
            this.location = location;
        }

        /**
         * <p>
         * 返回模板编码。
         * </p>
         * @return 模板编码
         */
        public String getEncoding() {
            return this.encoding;
        }

        /**
         * <p>
         * 设置模板编码。
         * </p>
         * @param encoding 模板编码
         */
        public void setEncoding(String encoding) {
            this.encoding = encoding;
        }
    }
}
