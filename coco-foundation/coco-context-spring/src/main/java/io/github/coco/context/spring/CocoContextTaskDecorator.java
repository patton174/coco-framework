package io.github.coco.context.spring;

import io.github.coco.context.CocoContextSnapshotFactory;
import java.util.Objects;
import org.springframework.core.task.TaskDecorator;

/**
 * Spring 异步任务的 Coco 上下文装饰器.
 *
 * @author patton174
 * @since 1.0.0
 */
public final class CocoContextTaskDecorator implements TaskDecorator {

  private final CocoContextSnapshotFactory snapshotFactory;

  /**
   * 创建 Coco 上下文任务装饰器.
   *
   * @param snapshotFactory 上下文快照工厂
   */
  public CocoContextTaskDecorator(CocoContextSnapshotFactory snapshotFactory) {
    this.snapshotFactory = Objects.requireNonNull(snapshotFactory,
        "snapshotFactory must not be null");
  }

  @Override
  public Runnable decorate(Runnable runnable) {
    return this.snapshotFactory.capture().wrap(runnable);
  }
}
