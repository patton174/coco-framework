package io.github.coco.feature.tenant.context;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Coco 租户上下文执行器适配器。
 * <p>
 * 在每次提交任务时捕获当前线程的租户上下文，在任务执行期间安装该上下文，并在任务结束后恢复
 * worker 线程原有的租户上下文。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-tenant}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoTenantContextExecutor implements Executor {

    private final Executor delegate;

    /**
     * <p>
     * 创建租户上下文执行器适配器。
     * </p>
     * @param delegate 目标执行器
     */
    public CocoTenantContextExecutor(Executor delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Runnable command) {
        Runnable checkedCommand = Objects.requireNonNull(command, "command must not be null");
        this.delegate.execute(CocoTenantContextHolder.capture().wrap(checkedCommand));
    }
}
