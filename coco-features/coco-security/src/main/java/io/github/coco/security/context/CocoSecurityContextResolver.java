package io.github.coco.security.context;

import java.util.Optional;

/**
 * Coco 安全上下文解析器。
 * <p>
 * Web、网关、任务调度或测试环境可以替换该接口，把各自入口中的身份信息转换为 Coco 安全上下文。
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
public interface CocoSecurityContextResolver {

    /**
     * <p>
     * 解析当前调用上下文中的安全上下文。
     * </p>
     * @return 安全上下文；无法解析时为空
     */
    Optional<CocoSecurityContext> resolve();
}
