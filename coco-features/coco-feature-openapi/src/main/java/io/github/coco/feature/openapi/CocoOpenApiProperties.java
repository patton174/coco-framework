package io.github.coco.feature.openapi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco OpenAPI 功能配置属性。
 * <p>
 * 绑定 {@code coco.openapi} 命名空间，控制 OpenAPI 元数据基础设施和默认文档信息。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-openapi}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "coco.openapi")
public class CocoOpenApiProperties {

    private boolean enabled = true;

    @NestedConfigurationProperty
    private InfoProperties info = new InfoProperties();

    /**
     * <p>
     * 返回是否启用 OpenAPI 基础设施。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用 OpenAPI 基础设施。
     * </p>
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回 OpenAPI 文档信息配置。
     * </p>
     * @return OpenAPI 文档信息配置
     */
    public InfoProperties getInfo() {
        return this.info;
    }

    /**
     * <p>
     * 设置 OpenAPI 文档信息配置。
     * </p>
     * @param info OpenAPI 文档信息配置
     */
    public void setInfo(InfoProperties info) {
        this.info = info == null ? new InfoProperties() : info;
    }

    /**
     * Coco OpenAPI 文档信息配置。
     * <p>
     * 提供默认文档标题、版本和描述，后续 springdoc 或其他文档实现可直接读取该契约。
     * </p>
     * <p>
     * 项目信息：
     * </p>
     * <ul>
     *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
     *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
     *   <li>模块：{@code coco-feature-openapi}</li>
     * </ul>
     * @author patton174
     * @since 1.0.0
     */
    public static class InfoProperties {

        private String title = "Coco API";

        private String version = "1.0.0";

        private String description = "Coco Framework API";

        /**
         * <p>
         * 返回文档标题。
         * </p>
         * @return 文档标题
         */
        public String getTitle() {
            return this.title;
        }

        /**
         * <p>
         * 设置文档标题。
         * </p>
         * @param title 文档标题
         */
        public void setTitle(String title) {
            this.title = title;
        }

        /**
         * <p>
         * 返回文档版本。
         * </p>
         * @return 文档版本
         */
        public String getVersion() {
            return this.version;
        }

        /**
         * <p>
         * 设置文档版本。
         * </p>
         * @param version 文档版本
         */
        public void setVersion(String version) {
            this.version = version;
        }

        /**
         * <p>
         * 返回文档描述。
         * </p>
         * @return 文档描述
         */
        public String getDescription() {
            return this.description;
        }

        /**
         * <p>
         * 设置文档描述。
         * </p>
         * @param description 文档描述
         */
        public void setDescription(String description) {
            this.description = description;
        }
    }
}
