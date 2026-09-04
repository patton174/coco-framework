package io.github.coco.spring.boot.async;

import io.github.coco.context.CocoContextSnapshot;
import io.github.coco.context.CocoPageContextHolder;
import io.github.coco.context.CocoRequestContextHolder;
import io.github.coco.context.trace.CocoTraceContext;
import org.springframework.core.task.TaskDecorator;

/**
 * Coco 上下文传播任务装饰器。
 * <p>
 * 在提交异步任务时自动捕获当前线程的请求上下文和 Trace 上下文，并在工作线程执行任务前恢复，
 * 任务结束后还原工作线程原有上下文，确保跨线程的上下文透传。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-spring-boot-autoconfigure}</li>
 * </ul>
 * @author patton174
 * @since 1.1.0
 */
public class CocoContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        CocoContextSnapshot snapshot = CocoContextSnapshot.compose(
                CocoRequestContextHolder.capture(),
                CocoTraceContext.capture(),
                CocoPageContextHolder.capture());
        return snapshot.wrap(runnable);
    }
}
