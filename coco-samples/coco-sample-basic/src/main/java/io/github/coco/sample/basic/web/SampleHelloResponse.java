package io.github.coco.sample.basic.web;

/**
 * Coco 示例问候响应。
 * <p>
 * 普通业务响应模型，不依赖 Coco Web 统一响应结构。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-sample-basic}</li>
 * </ul>
 * @param name 示例名称
 * @param message 示例消息
 * @author patton174
 * @since 1.0.0
 */
public record SampleHelloResponse(String name, String message) {
}
