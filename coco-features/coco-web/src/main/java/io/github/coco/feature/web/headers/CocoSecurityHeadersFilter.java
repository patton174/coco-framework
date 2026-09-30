package io.github.coco.feature.web.headers;

import java.io.IOException;
import java.util.Objects;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Coco 安全响应头过滤器。
 * <p>
 * 在过滤器链最前端写入配置的安全响应头，使后续过滤器、业务代码以及下游产生的错误响应都携带这些响应头。
 * </p>
 * <p>
 * {@code Strict-Transport-Security} 仅在 {@link HttpServletRequest#isSecure()} 为 {@code true} 时写入。
 * 需要注意：在 TLS 由前置代理终止的部署中，只有应用设置了 {@code server.forward-headers-strategy=framework}
 * （或 {@code native}）时 {@code isSecure()} 才会反映客户端原始协议；否则该响应头会被静默跳过。
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
public class CocoSecurityHeadersFilter extends OncePerRequestFilter {

    private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

    private static final String X_FRAME_OPTIONS = "X-Frame-Options";

    private static final String REFERRER_POLICY = "Referrer-Policy";

    private static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";

    private static final String PERMISSIONS_POLICY = "Permissions-Policy";

    private static final String STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";

    private final CocoSecurityHeadersProperties properties;

    /**
     * <p>
     * 创建 Coco 安全响应头过滤器。
     * </p>
     * @param properties 安全响应头配置属性
     */
    public CocoSecurityHeadersFilter(CocoSecurityHeadersProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        // 响应头必须在进入过滤器链之前写入：一是下游产生的错误响应（签名 401、限流 429、未处理异常 500）同样需要携带，
        // 响应一旦提交后再写入就已经太晚；二是后续过滤器和业务代码仍可通过 setHeader 覆盖框架的默认取值。
        writeSecurityHeaders(request, response);
        filterChain.doFilter(request, response);
    }

    /**
     * <p>
     * 将配置的安全响应头写入响应。
     * </p>
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     */
    private void writeSecurityHeaders(HttpServletRequest request, HttpServletResponse response) {
        setHeaderIfPresent(response, X_CONTENT_TYPE_OPTIONS, this.properties.getContentTypeOptions());
        setHeaderIfPresent(response, X_FRAME_OPTIONS, this.properties.getFrameOptions());
        setHeaderIfPresent(response, REFERRER_POLICY, this.properties.getReferrerPolicy());
        setHeaderIfPresent(response, CONTENT_SECURITY_POLICY, this.properties.getContentSecurityPolicy());
        setHeaderIfPresent(response, PERMISSIONS_POLICY, this.properties.getPermissionsPolicy());
        // HSTS 在明文 HTTP 上会被浏览器忽略，写入只会掩盖部署配置问题，因此仅在安全连接上写入。
        if (request.isSecure()) {
            setHeaderIfPresent(response, STRICT_TRANSPORT_SECURITY, this.properties.getStrictTransportSecurity());
        }
    }

    /**
     * <p>
     * 当取值非空时写入指定响应头。
     * </p>
     * @param response 当前 HTTP 响应
     * @param name 响应头名称
     * @param value 响应头取值；为 {@code null} 时不写入
     */
    private static void setHeaderIfPresent(HttpServletResponse response, String name, String value) {
        if (value != null) {
            response.setHeader(name, value);
        }
    }
}
