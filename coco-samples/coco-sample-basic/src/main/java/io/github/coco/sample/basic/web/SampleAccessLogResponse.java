package io.github.coco.sample.basic.web;

import io.github.coco.common.accesslog.CocoAccessLog;

/**
 * Coco 示例接口访问日志响应。
 * <p>
 * 将 Coco 访问日志事件转换为适合 JSON 输出的响应模型。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-sample-basic}</li>
 * </ul>
 * @param traceId TraceId
 * @param method HTTP 方法
 * @param path 请求路径
 * @param status 响应状态码
 * @param durationMillis 请求耗时，单位毫秒
 * @param success 是否成功
 * @param exceptionType 异常类型
 * @author patton174
 * @since 1.0.0
 */
public record SampleAccessLogResponse(String traceId, String method, String path, int status,
        long durationMillis, boolean success, String exceptionType) {

    /**
     * <p>
     * 从 Coco 接口访问日志创建示例响应。
     * </p>
     * @param accessLog Coco 接口访问日志
     * @return 示例接口访问日志响应
     */
    public static SampleAccessLogResponse from(CocoAccessLog accessLog) {
        return new SampleAccessLogResponse(
                accessLog.traceId(),
                accessLog.method().orElse(null),
                accessLog.path().orElse(null),
                accessLog.status(),
                accessLog.durationMillis(),
                accessLog.success(),
                accessLog.exceptionType().orElse(null));
    }
}
