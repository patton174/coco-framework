package io.github.coco.feature.web.headers;

import io.github.coco.context.CocoStrings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

/**
 * Coco 安全响应头配置属性。
 * <p>
 * 控制框架统一写入的安全响应头取值。其中 {@code X-Content-Type-Options}、{@code X-Frame-Options}、
 * {@code Referrer-Policy} 提供安全的默认值；{@code Content-Security-Policy}、{@code Permissions-Policy}、
 * {@code Strict-Transport-Security} 默认不写入，需由应用显式开启。
 * </p>
 * <p>
 * 任一响应头取值为 {@code null} 或空白时，该响应头不会写入响应。
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
@ConfigurationProperties(prefix = "coco.web.security-headers")
public class CocoSecurityHeadersProperties {

    private static final String DEFAULT_CONTENT_TYPE_OPTIONS = "nosniff";

    private static final String DEFAULT_FRAME_OPTIONS = "DENY";

    private static final String DEFAULT_REFERRER_POLICY = "strict-origin-when-cross-origin";

    private boolean enabled = true;

    private int order = Ordered.HIGHEST_PRECEDENCE;

    private String contentTypeOptions = DEFAULT_CONTENT_TYPE_OPTIONS;

    private String frameOptions = DEFAULT_FRAME_OPTIONS;

    private String referrerPolicy = DEFAULT_REFERRER_POLICY;

    private String contentSecurityPolicy;

    private String permissionsPolicy;

    private String strictTransportSecurity;

    /**
     * <p>
     * 返回是否启用安全响应头过滤器。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用安全响应头过滤器。
     * </p>
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回安全响应头过滤器的执行顺序。
     * </p>
     * @return 过滤器执行顺序
     */
    public int getOrder() {
        return this.order;
    }

    /**
     * <p>
     * 设置安全响应头过滤器的执行顺序。
     * </p>
     * @param order 过滤器执行顺序
     */
    public void setOrder(int order) {
        this.order = order;
    }

    /**
     * <p>
     * 返回 {@code X-Content-Type-Options} 响应头取值。
     * </p>
     * @return 响应头取值；不写入该响应头时返回 {@code null}
     */
    public String getContentTypeOptions() {
        return this.contentTypeOptions;
    }

    /**
     * <p>
     * 设置 {@code X-Content-Type-Options} 响应头取值。
     * </p>
     * <p>
     * 取值为 {@code null} 或空白时回退为默认值 {@code nosniff}。
     * </p>
     * @param contentTypeOptions 响应头取值
     */
    public void setContentTypeOptions(String contentTypeOptions) {
        String normalized = CocoStrings.blankToNull(contentTypeOptions);
        this.contentTypeOptions = normalized == null ? DEFAULT_CONTENT_TYPE_OPTIONS : normalized;
    }

    /**
     * <p>
     * 返回 {@code X-Frame-Options} 响应头取值。
     * </p>
     * @return 响应头取值；不写入该响应头时返回 {@code null}
     */
    public String getFrameOptions() {
        return this.frameOptions;
    }

    /**
     * <p>
     * 设置 {@code X-Frame-Options} 响应头取值。
     * </p>
     * <p>
     * 取值为 {@code null} 或空白时回退为默认值 {@code DENY}。
     * </p>
     * @param frameOptions 响应头取值
     */
    public void setFrameOptions(String frameOptions) {
        String normalized = CocoStrings.blankToNull(frameOptions);
        this.frameOptions = normalized == null ? DEFAULT_FRAME_OPTIONS : normalized;
    }

    /**
     * <p>
     * 返回 {@code Referrer-Policy} 响应头取值。
     * </p>
     * @return 响应头取值；不写入该响应头时返回 {@code null}
     */
    public String getReferrerPolicy() {
        return this.referrerPolicy;
    }

    /**
     * <p>
     * 设置 {@code Referrer-Policy} 响应头取值。
     * </p>
     * <p>
     * 取值为 {@code null} 或空白时回退为默认值 {@code strict-origin-when-cross-origin}。
     * </p>
     * @param referrerPolicy 响应头取值
     */
    public void setReferrerPolicy(String referrerPolicy) {
        String normalized = CocoStrings.blankToNull(referrerPolicy);
        this.referrerPolicy = normalized == null ? DEFAULT_REFERRER_POLICY : normalized;
    }

    /**
     * <p>
     * 返回 {@code Content-Security-Policy} 响应头取值。
     * </p>
     * <p>
     * 该响应头默认不写入，必须由应用显式开启：错误的 CSP 会直接破坏应用的正常运行，且不存在普适的安全默认值，
     * 因此策略内容只能由应用自行编写。
     * </p>
     * @return 响应头取值；不写入该响应头时返回 {@code null}
     */
    public String getContentSecurityPolicy() {
        return this.contentSecurityPolicy;
    }

    /**
     * <p>
     * 设置 {@code Content-Security-Policy} 响应头取值。
     * </p>
     * <p>
     * 取值为 {@code null} 或空白时保持为 {@code null}，即不写入该响应头。
     * </p>
     * @param contentSecurityPolicy 响应头取值
     */
    public void setContentSecurityPolicy(String contentSecurityPolicy) {
        this.contentSecurityPolicy = CocoStrings.blankToNull(contentSecurityPolicy);
    }

    /**
     * <p>
     * 返回 {@code Permissions-Policy} 响应头取值。
     * </p>
     * <p>
     * 该响应头默认不写入，必须由应用显式开启：需要放开或收紧的浏览器特性列表与具体应用强相关，框架无法预设。
     * </p>
     * @return 响应头取值；不写入该响应头时返回 {@code null}
     */
    public String getPermissionsPolicy() {
        return this.permissionsPolicy;
    }

    /**
     * <p>
     * 设置 {@code Permissions-Policy} 响应头取值。
     * </p>
     * <p>
     * 取值为 {@code null} 或空白时保持为 {@code null}，即不写入该响应头。
     * </p>
     * @param permissionsPolicy 响应头取值
     */
    public void setPermissionsPolicy(String permissionsPolicy) {
        this.permissionsPolicy = CocoStrings.blankToNull(permissionsPolicy);
    }

    /**
     * <p>
     * 返回 {@code Strict-Transport-Security} 响应头取值。
     * </p>
     * <p>
     * 该响应头默认不写入，必须由应用显式开启：错误配置的 {@code max-age} 会让域名在该时长内无法通过普通 HTTP 访问，
     * 且该响应头在明文 HTTP 上没有意义。
     * </p>
     * @return 响应头取值；不写入该响应头时返回 {@code null}
     */
    public String getStrictTransportSecurity() {
        return this.strictTransportSecurity;
    }

    /**
     * <p>
     * 设置 {@code Strict-Transport-Security} 响应头取值。
     * </p>
     * <p>
     * 取值为 {@code null} 或空白时保持为 {@code null}，即不写入该响应头。
     * </p>
     * @param strictTransportSecurity 响应头取值
     */
    public void setStrictTransportSecurity(String strictTransportSecurity) {
        this.strictTransportSecurity = CocoStrings.blankToNull(strictTransportSecurity);
    }
}
