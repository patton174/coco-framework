package io.github.coco.feature.web.trace;

/**
 * Coco Web Trace 配置属性。
 * <p>
 * 配置请求 Trace 过滤器是否启用，以及读取和回写 TraceId 的 HTTP 头名称。
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
 * @since 1.0.0
 */
public class CocoTraceProperties {

    /**
     * 默认 TraceId HTTP 头名称。
     */
    public static final String DEFAULT_HEADER_NAME = "X-Trace-Id";

    /**
     * 默认 MDC TraceId 键名。
     */
    public static final String DEFAULT_MDC_KEY = "traceId";

    /**
     * 默认 TraceId Cookie 名称。
     */
    public static final String DEFAULT_COOKIE_NAME = "COCO_TRACE_ID";

    /**
     * 默认 TraceId Cookie Path。
     */
    public static final String DEFAULT_COOKIE_PATH = "/";

    /**
     * 默认 TraceId Cookie SameSite 策略。
     */
    public static final String DEFAULT_COOKIE_SAME_SITE = "Lax";

    private boolean enabled = true;

    private String headerName = DEFAULT_HEADER_NAME;

    private String mdcKey = DEFAULT_MDC_KEY;

    private boolean responseHeaderEnabled = true;

    private boolean responseCookieEnabled;

    private String cookieName = DEFAULT_COOKIE_NAME;

    private String cookiePath = DEFAULT_COOKIE_PATH;

    private int cookieMaxAge = -1;

    private boolean cookieHttpOnly;

    private boolean cookieSecure;

    private String cookieSameSite = DEFAULT_COOKIE_SAME_SITE;

    /**
     * <p>
     * 返回 Trace 过滤器是否启用。
     * </p>
     * @return Trace 过滤器是否启用
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置 Trace 过滤器是否启用。
     * </p>
     * @param enabled Trace 过滤器是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回读取和回写 TraceId 的 HTTP 头名称。
     * </p>
     * @return TraceId HTTP 头名称
     */
    public String getHeaderName() {
        return this.headerName;
    }

    /**
     * <p>
     * 设置读取和回写 TraceId 的 HTTP 头名称。
     * </p>
     * @param headerName TraceId HTTP 头名称
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName == null || headerName.isBlank()
                ? DEFAULT_HEADER_NAME
                : headerName.trim();
    }

    /**
     * <p>
     * 返回写入日志 MDC 的 TraceId 键名。
     * </p>
     * @return MDC TraceId 键名
     */
    public String getMdcKey() {
        return this.mdcKey;
    }

    /**
     * <p>
     * 设置写入日志 MDC 的 TraceId 键名。
     * </p>
     * @param mdcKey MDC TraceId 键名
     */
    public void setMdcKey(String mdcKey) {
        this.mdcKey = mdcKey == null || mdcKey.isBlank()
                ? DEFAULT_MDC_KEY
                : mdcKey.trim();
    }

    /**
     * <p>
     * 返回是否将 TraceId 写入响应头。
     * </p>
     * @return 写入响应头时返回 {@code true}
     */
    public boolean isResponseHeaderEnabled() {
        return this.responseHeaderEnabled;
    }

    /**
     * <p>
     * 设置是否将 TraceId 写入响应头。
     * </p>
     * @param responseHeaderEnabled 是否写入响应头
     */
    public void setResponseHeaderEnabled(boolean responseHeaderEnabled) {
        this.responseHeaderEnabled = responseHeaderEnabled;
    }

    /**
     * <p>
     * 返回是否将 TraceId 写入 Cookie。
     * </p>
     * @return 写入 Cookie 时返回 {@code true}
     */
    public boolean isResponseCookieEnabled() {
        return this.responseCookieEnabled;
    }

    /**
     * <p>
     * 设置是否将 TraceId 写入 Cookie。
     * </p>
     * @param responseCookieEnabled 是否写入 Cookie
     */
    public void setResponseCookieEnabled(boolean responseCookieEnabled) {
        this.responseCookieEnabled = responseCookieEnabled;
    }

    /**
     * <p>
     * 返回 TraceId Cookie 名称。
     * </p>
     * @return TraceId Cookie 名称
     */
    public String getCookieName() {
        return this.cookieName;
    }

    /**
     * <p>
     * 设置 TraceId Cookie 名称。
     * </p>
     * @param cookieName TraceId Cookie 名称
     */
    public void setCookieName(String cookieName) {
        this.cookieName = cookieName == null || cookieName.isBlank()
                ? DEFAULT_COOKIE_NAME
                : cookieName.trim();
    }

    /**
     * <p>
     * 返回 TraceId Cookie Path。
     * </p>
     * @return TraceId Cookie Path
     */
    public String getCookiePath() {
        return this.cookiePath;
    }

    /**
     * <p>
     * 设置 TraceId Cookie Path。
     * </p>
     * @param cookiePath TraceId Cookie Path
     */
    public void setCookiePath(String cookiePath) {
        this.cookiePath = cookiePath == null || cookiePath.isBlank()
                ? DEFAULT_COOKIE_PATH
                : cookiePath.trim();
    }

    /**
     * <p>
     * 返回 TraceId Cookie Max-Age，负数表示会话级 Cookie。
     * </p>
     * @return TraceId Cookie Max-Age
     */
    public int getCookieMaxAge() {
        return this.cookieMaxAge;
    }

    /**
     * <p>
     * 设置 TraceId Cookie Max-Age。
     * </p>
     * @param cookieMaxAge TraceId Cookie Max-Age
     */
    public void setCookieMaxAge(int cookieMaxAge) {
        this.cookieMaxAge = cookieMaxAge;
    }

    /**
     * <p>
     * 返回 TraceId Cookie 是否 HttpOnly。
     * </p>
     * @return HttpOnly 时返回 {@code true}
     */
    public boolean isCookieHttpOnly() {
        return this.cookieHttpOnly;
    }

    /**
     * <p>
     * 设置 TraceId Cookie 是否 HttpOnly。
     * </p>
     * @param cookieHttpOnly 是否 HttpOnly
     */
    public void setCookieHttpOnly(boolean cookieHttpOnly) {
        this.cookieHttpOnly = cookieHttpOnly;
    }

    /**
     * <p>
     * 返回 TraceId Cookie 是否 Secure。
     * </p>
     * @return Secure 时返回 {@code true}
     */
    public boolean isCookieSecure() {
        return this.cookieSecure;
    }

    /**
     * <p>
     * 设置 TraceId Cookie 是否 Secure。
     * </p>
     * @param cookieSecure 是否 Secure
     */
    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    /**
     * <p>
     * 返回 TraceId Cookie SameSite 策略。
     * </p>
     * @return TraceId Cookie SameSite 策略
     */
    public String getCookieSameSite() {
        return this.cookieSameSite;
    }

    /**
     * <p>
     * 设置 TraceId Cookie SameSite 策略。
     * </p>
     * @param cookieSameSite TraceId Cookie SameSite 策略
     */
    public void setCookieSameSite(String cookieSameSite) {
        this.cookieSameSite = cookieSameSite == null || cookieSameSite.isBlank()
                ? null
                : cookieSameSite.trim();
    }
}
