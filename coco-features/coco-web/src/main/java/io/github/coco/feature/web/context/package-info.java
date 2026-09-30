/**
 * Coco Web 请求上下文。
 * <p>
 * 定义 Servlet 请求解析、上下文快照、安全输入、参数清洗、签名规范化和浏览器指纹信号能力，为日志、审计、鉴权、租户和数据权限提供统一请求入口信息。
 * 该包是请求上下文基础设施的聚合包，后续能力可以围绕这里的 SPI 继续拆分专用子包。
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
package io.github.coco.feature.web.context;
