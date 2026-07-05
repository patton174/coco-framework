package io.github.coco.spring.boot.banner;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco 启动 banner 配置属性。
 * <p>
 * 绑定 {@code coco.banner} 命名空间，控制 Coco 启动信息是否打印以及展示的项目元信息。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-spring-boot-starter}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "coco.banner")
public class CocoBannerProperties {

    private static final String DEFAULT_TITLE = "Coco Spring";

    private static final String DEFAULT_AUTHOR = "patton174";

    private static final String DEFAULT_REPOSITORY = "https://github.com/patton174/coco-framework";

    private boolean enabled = true;

    private String title = DEFAULT_TITLE;

    private String author = DEFAULT_AUTHOR;

    private String repository = DEFAULT_REPOSITORY;

    private String version;

    /**
     * <p>
     * 返回是否启用 Coco 启动 banner。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用 Coco 启动 banner。
     * </p>
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回 banner 标题。
     * </p>
     * @return banner 标题
     */
    public String getTitle() {
        return this.title;
    }

    /**
     * <p>
     * 设置 banner 标题。
     * </p>
     * @param title banner 标题
     */
    public void setTitle(String title) {
        this.title = title == null || title.isBlank() ? DEFAULT_TITLE : title.trim();
    }

    /**
     * <p>
     * 返回作者 GitHub 用户名。
     * </p>
     * @return 作者 GitHub 用户名
     */
    public String getAuthor() {
        return this.author;
    }

    /**
     * <p>
     * 设置作者 GitHub 用户名。
     * </p>
     * @param author 作者 GitHub 用户名
     */
    public void setAuthor(String author) {
        this.author = author == null || author.isBlank() ? DEFAULT_AUTHOR : author.trim();
    }

    /**
     * <p>
     * 返回项目仓库地址。
     * </p>
     * @return 项目仓库地址
     */
    public String getRepository() {
        return this.repository;
    }

    /**
     * <p>
     * 设置项目仓库地址。
     * </p>
     * @param repository 项目仓库地址
     */
    public void setRepository(String repository) {
        this.repository = repository == null || repository.isBlank() ? DEFAULT_REPOSITORY : repository.trim();
    }

    /**
     * <p>
     * 返回显式配置的版本号。
     * </p>
     * @return 显式配置的版本号；未配置时为空
     */
    public String getVersion() {
        return this.version;
    }

    /**
     * <p>
     * 设置显式展示的版本号。
     * </p>
     * @param version 版本号
     */
    public void setVersion(String version) {
        this.version = version == null || version.isBlank() ? null : version.trim();
    }
}
