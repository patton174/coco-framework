/**
 * Coco Web 安全上下文桥接。
 * <p>
 * 提供 Servlet 请求到 {@code CocoSecurityContextHolder} 的生命周期适配，不绑定具体认证协议、用户模型或权限存储。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-security}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
package io.github.coco.security.web;
