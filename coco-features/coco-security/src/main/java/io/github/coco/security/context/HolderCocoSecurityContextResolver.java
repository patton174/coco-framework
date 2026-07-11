package io.github.coco.security.context;

import java.util.Optional;

/**
 * 基于 {@link CocoSecurityContextHolder} 的安全上下文解析器。
 * <p>
 * 作为安全模块默认实现，优先服务框架内部 ThreadLocal 上下文传播，业务系统可以注册自定义解析器替换它。
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
public final class HolderCocoSecurityContextResolver implements CocoSecurityContextResolver {

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<CocoSecurityContext> resolve() {
        return CocoSecurityContextHolder.current();
    }
}
