package io.github.coco.feature.web;

import io.github.coco.feature.web.accesslog.CocoAccessLogCaptureProperties;
import io.github.coco.feature.web.context.CocoWebContextProperties;
import io.github.coco.feature.web.response.CocoResponseProperties;
import io.github.coco.feature.web.response.CocoResponseWrapProperties;
import io.github.coco.feature.web.trace.CocoTraceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco Web 功能配置属性。
 * <p>
 * 绑定 {@code coco.web} 命名空间，集中维护 Web 功能模块自己的配置项。
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
@ConfigurationProperties(prefix = "coco.web")
public class CocoWebProperties {

    @NestedConfigurationProperty
    private CocoTraceProperties trace = new CocoTraceProperties();

    @NestedConfigurationProperty
    private CocoResponseProperties response = new CocoResponseProperties();

    @NestedConfigurationProperty
    private CocoResponseWrapProperties responseWrap = new CocoResponseWrapProperties();

    @NestedConfigurationProperty
    private CocoAccessLogCaptureProperties accessLog = new CocoAccessLogCaptureProperties();

    @NestedConfigurationProperty
    private CocoWebContextProperties context = new CocoWebContextProperties();

    /**
     * <p>
     * 返回 Trace 配置属性。
     * </p>
     * @return Trace 配置属性
     */
    public CocoTraceProperties getTrace() {
        return this.trace;
    }

    /**
     * <p>
     * 设置 Trace 配置属性。
     * </p>
     * @param trace Trace 配置属性
     */
    public void setTrace(CocoTraceProperties trace) {
        this.trace = trace == null ? new CocoTraceProperties() : trace;
    }

    /**
     * <p>
     * 返回统一响应配置属性。
     * </p>
     * @return 统一响应配置属性
     */
    public CocoResponseProperties getResponse() {
        return this.response;
    }

    /**
     * <p>
     * 设置统一响应配置属性。
     * </p>
     * @param response 统一响应配置属性
     */
    public void setResponse(CocoResponseProperties response) {
        this.response = response == null ? new CocoResponseProperties() : response;
    }

    /**
     * <p>
     * 返回正常响应包装配置属性。
     * </p>
     * @return 正常响应包装配置属性
     */
    public CocoResponseWrapProperties getResponseWrap() {
        return this.responseWrap;
    }

    /**
     * <p>
     * 设置正常响应包装配置属性。
     * </p>
     * @param responseWrap 正常响应包装配置属性
     */
    public void setResponseWrap(CocoResponseWrapProperties responseWrap) {
        this.responseWrap = responseWrap == null ? new CocoResponseWrapProperties() : responseWrap;
    }

    /**
     * <p>
     * 返回接口访问日志打印配置属性。
     * </p>
     * @return 接口访问日志打印配置属性
     */
    public CocoAccessLogCaptureProperties getAccessLog() {
        return this.accessLog;
    }

    /**
     * <p>
     * 设置接口访问日志打印配置属性。
     * </p>
     * @param accessLog 接口访问日志打印配置属性
     */
    public void setAccessLog(CocoAccessLogCaptureProperties accessLog) {
        this.accessLog = accessLog == null ? new CocoAccessLogCaptureProperties() : accessLog;
    }

    /**
     * <p>
     * 返回 Web 请求上下文配置属性。
     * </p>
     * @return Web 请求上下文配置属性
     */
    public CocoWebContextProperties getContext() {
        return this.context;
    }

    /**
     * <p>
     * 设置 Web 请求上下文配置属性。
     * </p>
     * @param context Web 请求上下文配置属性
     */
    public void setContext(CocoWebContextProperties context) {
        this.context = context == null ? new CocoWebContextProperties() : context;
    }
}
