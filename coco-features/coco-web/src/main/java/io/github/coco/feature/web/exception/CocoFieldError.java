package io.github.coco.feature.web.exception;

/**
 * Coco Web 字段级校验错误。
 * <p>
 * 表示单个请求参数的校验失败信息，用于在统一响应中返回结构化的字段错误详情。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @param field 字段名称
 * @param message 校验错误消息
 * @author patton174
 * @since 1.1.0
 */
public record CocoFieldError(String field, String message) {
}
