package io.github.coco.feature.security.web;

import java.util.Optional;

import io.github.coco.feature.security.context.CocoSecurityContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Coco Web 安全上下文解析器。
 * <p>
 * 业务项目可以替换该 SPI，把网关、Spring Security、JWT、Session 或自定义认证结果转换为 Coco 安全上下文。
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
@FunctionalInterface
public interface CocoWebSecurityContextResolver {

    /**
     * <p>
     * 从当前 Servlet 请求解析安全上下文。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 安全上下文；无法解析时为空
     */
    Optional<CocoSecurityContext> resolve(HttpServletRequest request);
}
