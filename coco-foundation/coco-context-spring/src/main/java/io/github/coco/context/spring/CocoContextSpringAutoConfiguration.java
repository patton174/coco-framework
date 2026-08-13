package io.github.coco.context.spring;

import io.github.coco.context.CocoContextSnapshot;
import io.github.coco.context.CocoContextSnapshotContributor;
import io.github.coco.context.CocoContextSnapshotFactory;
import io.github.coco.context.CocoRequestContextHolder;
import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.logging.context.CocoMdcContext;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;

/**
 * Coco Spring 异步上下文传播自动配置.
 *
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(TaskDecorator.class)
@ConditionalOnProperty(prefix = "coco.context.propagation", name = "enabled",
    havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CocoContextPropagationProperties.class)
public class CocoContextSpringAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "cocoTraceContextSnapshotContributor")
  CocoContextSnapshotContributor cocoTraceContextSnapshotContributor() {
    return contributor("trace", 0, CocoTraceContext::capture);
  }

  @Bean
  @ConditionalOnMissingBean(name = "cocoRequestContextSnapshotContributor")
  CocoContextSnapshotContributor cocoRequestContextSnapshotContributor() {
    return contributor("request", 10, CocoRequestContextHolder::capture);
  }

  @Bean
  @ConditionalOnClass(CocoMdcContext.class)
  @ConditionalOnMissingBean(name = "cocoMdcContextSnapshotContributor")
  CocoContextSnapshotContributor cocoMdcContextSnapshotContributor() {
    return contributor("mdc", 20, CocoMdcContext::capture);
  }

  @Bean
  @ConditionalOnMissingBean(CocoContextSnapshotFactory.class)
  CocoContextSnapshotFactory cocoContextSnapshotFactory(
      List<CocoContextSnapshotContributor> contributors) {
    return new CocoContextSnapshotFactory(contributors);
  }

  @Bean(name = "cocoContextTaskDecorator")
  @ConditionalOnMissingBean(value = CocoContextTaskDecorator.class,
      name = "cocoContextTaskDecorator")
  CocoContextTaskDecorator cocoContextTaskDecorator(
      CocoContextSnapshotFactory snapshotFactory) {
    return new CocoContextTaskDecorator(snapshotFactory);
  }

  private static CocoContextSnapshotContributor contributor(String id, int order,
      Supplier<CocoContextSnapshot> capture) {
    return new CocoContextSnapshotContributor() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public int order() {
        return order;
      }

      @Override
      public CocoContextSnapshot capture() {
        return capture.get();
      }
    };
  }
}
