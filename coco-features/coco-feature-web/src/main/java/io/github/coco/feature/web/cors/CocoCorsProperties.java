package io.github.coco.feature.web.cors;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco CORS 跨域配置属性。
 * <p>
 * 控制全局 CORS 过滤器的允许来源、方法、请求头、响应头、凭证和预检缓存时间。
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
@ConfigurationProperties(prefix = "coco.web.cors")
public class CocoCorsProperties {

    private boolean enabled = false;

    private List<String> allowedOrigins = List.of("*");

    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");

    private List<String> allowedHeaders = List.of("*");

    private List<String> exposedHeaders = List.of();

    private boolean allowCredentials = false;

    private long maxAge = 1800;

    /**
     * <p>
     * 返回是否启用 CORS 跨域过滤器。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用 CORS 跨域过滤器。
     * </p>
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回允许的跨域来源列表。
     * </p>
     * @return 允许的来源列表
     */
    public List<String> getAllowedOrigins() {
        return this.allowedOrigins;
    }

    /**
     * <p>
     * 设置允许的跨域来源列表。
     * </p>
     * @param allowedOrigins 允许的来源列表
     */
    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * <p>
     * 返回允许的 HTTP 方法列表。
     * </p>
     * @return 允许的方法列表
     */
    public List<String> getAllowedMethods() {
        return this.allowedMethods;
    }

    /**
     * <p>
     * 设置允许的 HTTP 方法列表。
     * </p>
     * @param allowedMethods 允许的方法列表
     */
    public void setAllowedMethods(List<String> allowedMethods) {
        this.allowedMethods = allowedMethods;
    }

    /**
     * <p>
     * 返回允许的请求头列表。
     * </p>
     * @return 允许的请求头列表
     */
    public List<String> getAllowedHeaders() {
        return this.allowedHeaders;
    }

    /**
     * <p>
     * 设置允许的请求头列表。
     * </p>
     * @param allowedHeaders 允许的请求头列表
     */
    public void setAllowedHeaders(List<String> allowedHeaders) {
        this.allowedHeaders = allowedHeaders;
    }

    /**
     * <p>
     * 返回需要暴露给客户端的响应头列表。
     * </p>
     * @return 暴露的响应头列表
     */
    public List<String> getExposedHeaders() {
        return this.exposedHeaders;
    }

    /**
     * <p>
     * 设置需要暴露给客户端的响应头列表。
     * </p>
     * @param exposedHeaders 暴露的响应头列表
     */
    public void setExposedHeaders(List<String> exposedHeaders) {
        this.exposedHeaders = exposedHeaders;
    }

    /**
     * <p>
     * 返回是否允许发送凭证（Cookie 等）。
     * </p>
     * @return 允许凭证时返回 {@code true}
     */
    public boolean isAllowCredentials() {
        return this.allowCredentials;
    }

    /**
     * <p>
     * 设置是否允许发送凭证（Cookie 等）。
     * </p>
     * @param allowCredentials 是否允许凭证
     */
    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    /**
     * <p>
     * 返回预检请求的缓存时间，单位秒。
     * </p>
     * @return 预检缓存秒数
     */
    public long getMaxAge() {
        return this.maxAge;
    }

    /**
     * <p>
     * 设置预检请求的缓存时间，单位秒。
     * </p>
     * @param maxAge 预检缓存秒数
     */
    public void setMaxAge(long maxAge) {
        this.maxAge = maxAge;
    }
}
