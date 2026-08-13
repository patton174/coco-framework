package io.github.coco.context.spring;

import java.util.List;

import io.github.coco.context.CocoContextSnapshotContributor;
import io.github.coco.context.CocoContextSnapshotFactory;
import io.github.coco.context.CocoRequestContextHolder;
import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.logging.context.CocoMdcContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;

/** Coco Spring 异步上下文传播自动配置。 */
@AutoConfiguration
@ConditionalOnClass(TaskDecorator.class)
@ConditionalOnProperty(prefix = "coco.context.propagation", name = "enabled", havingValue = "true", matchIfMissing = true)
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
    CocoContextSnapshotFactory cocoContextSnapshotFactory(List<CocoContextSnapshotContributor> contributors) {
        return new CocoContextSnapshotFactory(contributors);
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(CocoContextSnapshotFactory.class)
    static class DefaultTaskDecoratorConfiguration {
        @Bean
        @ConditionalOnMissingBean(TaskDecorator.class)
        CocoContextTaskDecorator cocoContextTaskDecorator(CocoContextSnapshotFactory snapshotFactory) {
            return new CocoContextTaskDecorator(snapshotFactory);
        }
    }
    private static CocoContextSnapshotContributor contributor(String id, int order,
            java.util.function.Supplier<io.github.coco.context.CocoContextSnapshot> capture) {
        return new CocoContextSnapshotContributor() {
            public String id() { return id; }
            public int order() { return order; }
            public io.github.coco.context.CocoContextSnapshot capture() { return capture.get(); }
        };
    }
}
