/**
 * Coco 审计核心契约。
 * <p>
 * 定义审计事件模型、审计事件发布器、审计记录器 SPI 和记录失败处理策略。该包不绑定具体存储实现，
 * 业务系统可按需接入数据库、消息队列或外部审计平台。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-audit}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
package io.github.coco.audit.core;
