package io.github.coco.feature.cors;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco CORS 功能配置属性。
 * <p>
 * 绑定 {@code coco.cors} 命名空间。功能默认关闭；启用时必须显式配置精确 Origin 或 Origin
 * Pattern，避免框架默认放开跨域访问。
 * </p>
 * <p>
 * 业务项目提供 {@code CorsConfigurationSource}、{@code CorsFilter} 或
 * {@code FilterRegistrationBean<CorsFilter>} 时，Coco CORS 会整体退避。原生 Spring MVC 的
 * {@code CorsRegistry}、{@code WebMvcConfigurer} 和 {@code @CrossOrigin} 策略不会触发退避；
 * 启用 Coco Servlet CORS 时不得在相同路径上混用这些 MVC 策略。
 * </p>
 *
 * @author patton174
 * @since 2.0.1
 */
@ConfigurationProperties(prefix = "coco.cors")
public class CocoCorsProperties {

    private boolean enabled;

    private List<String> allowedOrigins = new ArrayList<>();

    private List<String> allowedOriginPatterns = new ArrayList<>();

    private List<String> allowedMethods = new ArrayList<>(List.of("GET", "HEAD", "POST"));

    private List<String> allowedHeaders = new ArrayList<>();

    private List<String> exposedHeaders = new ArrayList<>();

    private boolean allowCredentials;

    private long maxAge = 1800;

    private List<String> pathPatterns = new ArrayList<>(List.of("/**"));

    /**
     * 返回是否启用 CORS 基础设施。
     *
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * 设置是否启用 CORS 基础设施。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回允许的精确 Origin 列表。
     *
     * @return 精确 Origin 列表
     */
    public List<String> getAllowedOrigins() {
        return new ArrayList<>(this.allowedOrigins);
    }

    /**
     * 设置允许的精确 Origin 列表。
     *
     * @param allowedOrigins 精确 Origin 列表
     */
    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = copyOf(allowedOrigins);
    }

    /**
     * 返回允许的 Origin Pattern 列表。
     *
     * @return Origin Pattern 列表
     */
    public List<String> getAllowedOriginPatterns() {
        return new ArrayList<>(this.allowedOriginPatterns);
    }

    /**
     * 设置允许的 Origin Pattern 列表。
     *
     * @param allowedOriginPatterns Origin Pattern 列表
     */
    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = copyOf(allowedOriginPatterns);
    }

    /**
     * 返回允许的 HTTP 方法列表。
     *
     * @return HTTP 方法列表
     */
    public List<String> getAllowedMethods() {
        return new ArrayList<>(this.allowedMethods);
    }

    /**
     * 设置允许的 HTTP 方法列表。
     *
     * @param allowedMethods HTTP 方法列表
     */
    public void setAllowedMethods(List<String> allowedMethods) {
        this.allowedMethods = copyOf(allowedMethods);
    }

    /**
     * 返回允许的请求头列表。
     *
     * @return 请求头列表
     */
    public List<String> getAllowedHeaders() {
        return new ArrayList<>(this.allowedHeaders);
    }

    /**
     * 设置允许的请求头列表。
     *
     * @param allowedHeaders 请求头列表
     */
    public void setAllowedHeaders(List<String> allowedHeaders) {
        this.allowedHeaders = copyOf(allowedHeaders);
    }

    /**
     * 返回允许暴露给浏览器的响应头列表。
     *
     * @return 响应头列表
     */
    public List<String> getExposedHeaders() {
        return new ArrayList<>(this.exposedHeaders);
    }

    /**
     * 设置允许暴露给浏览器的响应头列表。
     *
     * @param exposedHeaders 响应头列表
     */
    public void setExposedHeaders(List<String> exposedHeaders) {
        this.exposedHeaders = copyOf(exposedHeaders);
    }

    /**
     * 返回是否允许浏览器携带凭据。
     *
     * @return 允许时返回 {@code true}
     */
    public boolean isAllowCredentials() {
        return this.allowCredentials;
    }

    /**
     * 设置是否允许浏览器携带凭据。
     *
     * @param allowCredentials 是否允许携带凭据
     */
    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    /**
     * 返回浏览器缓存预检响应的秒数。
     *
     * @return 预检响应最大缓存秒数
     */
    public long getMaxAge() {
        return this.maxAge;
    }

    /**
     * 设置浏览器缓存预检响应的秒数。
     *
     * @param maxAge 预检响应最大缓存秒数
     */
    public void setMaxAge(long maxAge) {
        this.maxAge = maxAge;
    }

    /**
     * 返回应用 CORS 配置的请求路径 Pattern 列表。
     *
     * @return 请求路径 Pattern 列表
     */
    public List<String> getPathPatterns() {
        return new ArrayList<>(this.pathPatterns);
    }

    /**
     * 设置应用 CORS 配置的请求路径 Pattern 列表。
     *
     * @param pathPatterns 请求路径 Pattern 列表
     */
    public void setPathPatterns(List<String> pathPatterns) {
        this.pathPatterns = copyOf(pathPatterns);
    }

    private static List<String> copyOf(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
}
