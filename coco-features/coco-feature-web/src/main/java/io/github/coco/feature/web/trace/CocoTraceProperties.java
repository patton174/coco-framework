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

    private boolean enabled = true;

    private String headerName = DEFAULT_HEADER_NAME;

    private String mdcKey = DEFAULT_MDC_KEY;

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
}
