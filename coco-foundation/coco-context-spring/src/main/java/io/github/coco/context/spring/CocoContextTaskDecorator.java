package io.github.coco.context.spring;

import java.util.Objects;

import io.github.coco.context.CocoContextSnapshotFactory;
import org.springframework.core.task.TaskDecorator;

/** Spring 异步任务的 Coco 上下文装饰器。 */
public final class CocoContextTaskDecorator implements TaskDecorator {

    private final CocoContextSnapshotFactory snapshotFactory;

    public CocoContextTaskDecorator(CocoContextSnapshotFactory snapshotFactory) {
        this.snapshotFactory = Objects.requireNonNull(snapshotFactory, "snapshotFactory must not be null");
    }

    @Override
    public Runnable decorate(Runnable runnable) {
        return this.snapshotFactory.capture().wrap(runnable);
    }
}
