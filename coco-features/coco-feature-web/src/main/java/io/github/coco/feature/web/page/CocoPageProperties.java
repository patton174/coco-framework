package io.github.coco.feature.web.page;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco Web 分页拦截器配置属性。
 * <p>
 * 控制分页参数名称、默认值和上限，由 {@link CocoPageInterceptor} 在请求入口解析并写入
 * {@link io.github.coco.context.CocoPageContextHolder}。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.1.0
 */
@ConfigurationProperties(prefix = "coco.web.page")
public class CocoPageProperties {

    private boolean enabled = true;

    private String pageParameterName = "page";

    private String sizeParameterName = "size";

    private long defaultPage = 1;

    private long defaultSize = 20;

    private long maxSize = 100;

    private String sortParameterName = "sort";

    /**
     * <p>
     * 返回是否启用分页参数拦截。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用分页参数拦截。
     * </p>
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回页码请求参数名。
     * </p>
     * @return 页码参数名
     */
    public String getPageParameterName() {
        return this.pageParameterName;
    }

    /**
     * <p>
     * 设置页码请求参数名。
     * </p>
     * @param pageParameterName 页码参数名
     */
    public void setPageParameterName(String pageParameterName) {
        this.pageParameterName = pageParameterName;
    }

    /**
     * <p>
     * 返回每页大小请求参数名。
     * </p>
     * @return 每页大小参数名
     */
    public String getSizeParameterName() {
        return this.sizeParameterName;
    }

    /**
     * <p>
     * 设置每页大小请求参数名。
     * </p>
     * @param sizeParameterName 每页大小参数名
     */
    public void setSizeParameterName(String sizeParameterName) {
        this.sizeParameterName = sizeParameterName;
    }

    /**
     * <p>
     * 返回默认页码。
     * </p>
     * @return 默认页码
     */
    public long getDefaultPage() {
        return this.defaultPage;
    }

    /**
     * <p>
     * 设置默认页码。
     * </p>
     * @param defaultPage 默认页码
     */
    public void setDefaultPage(long defaultPage) {
        this.defaultPage = defaultPage;
    }

    /**
     * <p>
     * 返回默认每页大小。
     * </p>
     * @return 默认每页大小
     */
    public long getDefaultSize() {
        return this.defaultSize;
    }

    /**
     * <p>
     * 设置默认每页大小。
     * </p>
     * @param defaultSize 默认每页大小
     */
    public void setDefaultSize(long defaultSize) {
        this.defaultSize = defaultSize;
    }

    /**
     * <p>
     * 返回每页大小上限。
     * </p>
     * @return 每页大小上限
     */
    public long getMaxSize() {
        return this.maxSize;
    }

    /**
     * <p>
     * 设置每页大小上限。
     * </p>
     * @param maxSize 每页大小上限
     */
    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
    }

    /**
     * <p>
     * 返回排序请求参数名。
     * </p>
     * @return 排序参数名
     */
    public String getSortParameterName() {
        return this.sortParameterName;
    }

    /**
     * <p>
     * 设置排序请求参数名。
     * </p>
     * @param sortParameterName 排序参数名
     */
    public void setSortParameterName(String sortParameterName) {
        this.sortParameterName = sortParameterName;
    }
}
