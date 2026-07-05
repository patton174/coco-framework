package io.github.coco.sample.basic.web;

/**
 * Coco 示例请求上下文响应。
 * <p>
 * 展示当前请求中 Coco 已绑定的 TraceId、HTTP 方法和请求路径。
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
 * @author patton174
 * @since 1.0.0
 */
public record SampleRequestContextResponse(String traceId, String method, String path) {
}
