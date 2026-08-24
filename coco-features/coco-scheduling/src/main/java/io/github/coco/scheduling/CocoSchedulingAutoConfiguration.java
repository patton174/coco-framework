package io.github.coco.scheduling;

import java.util.Collection;

import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.i18n.CocoMessageService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Coco 调度模块自动配置。
 *
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "coco.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CocoSchedulingProperties.class)
public class CocoSchedulingAutoConfiguration {

    /**
     * 注册调度模块国际化消息资源。
     *
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoSchedulingMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoSchedulingMessageBundleRegistrar() {
        return registry -> registry.add("coco-scheduling-messages");
    }

    @Bean
    @ConditionalOnMissingBean
    CocoSchedulingMessageResolver cocoSchedulingMessageResolver(ObjectProvider<CocoMessageService> messages) {
        return new CocoSchedulingMessageResolver(messages.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    CocoTaskDefinitionValidator cocoTaskDefinitionValidator(CocoSchedulingMessageResolver messages) {
        return new CocoTaskDefinitionValidator(messages);
    }

    /**
     * 创建默认 Spring 任务调度器；业务方提供同类型 Bean 时优先使用业务实现。
     *
     * @param properties 调度配置
     * @param validator 配置校验器
     * @return 默认任务调度器
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(TaskScheduler.class)
    public ThreadPoolTaskScheduler cocoTaskSchedulerExecutor(CocoSchedulingProperties properties,
            CocoTaskDefinitionValidator validator) {
        validator.validateProperties(properties);
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.getPoolSize());
        scheduler.setThreadNamePrefix(properties.getThreadNamePrefix());
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(!properties.getShutdown().isInterrupt());
        scheduler.setAwaitTerminationMillis(properties.getShutdown().getAwaitTermination().toMillis());
        return scheduler;
    }

    /**
     * 创建默认进程内互斥 guard；业务方可替换为分布式锁适配实现。
     *
     * @return 执行互斥 guard
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "coco.scheduling", name = "guard-type", havingValue = "in-memory", matchIfMissing = true)
    public CocoTaskExecutionGuard cocoTaskExecutionGuard() {
        return new InMemoryCocoTaskExecutionGuard();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.github.coco.feature.lock.CocoLockManager")
    @ConditionalOnProperty(prefix = "coco.scheduling", name = "guard-type", havingValue = "coco-lock")
    static class CocoLockTaskExecutionGuardConfiguration {

        /**
         * 创建跨实例 CocoLock 任务 guard。
         *
         * @param manager CocoLock 管理器
         * @param properties 调度配置
         * @return 跨实例任务执行 guard
         */
        @Bean
        @ConditionalOnMissingBean(CocoTaskExecutionGuard.class)
        CocoTaskExecutionGuard cocoLockTaskExecutionGuard(io.github.coco.feature.lock.CocoLockManager manager,
                CocoSchedulingProperties properties) {
            return new CocoLockTaskExecutionGuard(manager, properties.getGuard());
        }
    }

    /**
     * 创建 Coco 调度和动态注册 API；业务方可整体替换该 Bean。
     *
     * @param taskScheduler Spring 任务调度器
     * @param guard 执行互斥 guard
     * @param observers 任务事件观察器
     * @param validator 任务定义校验器
     * @param properties 调度配置
     * @return Coco 任务调度器
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(CocoTaskScheduler.class)
    public CocoTaskScheduler cocoTaskScheduler(TaskScheduler taskScheduler, CocoTaskExecutionGuard guard,
            Collection<CocoTaskExecutionObserver> observers, CocoTaskDefinitionValidator validator,
            CocoSchedulingProperties properties) {
        return new DefaultCocoTaskScheduler(taskScheduler, guard, observers, validator, properties.getShutdown());
    }

    /**
     * 创建注解任务注册器。
     *
     * @param beanFactory BeanFactory
     * @param scheduler Coco 任务调度器
     * @param validator 任务定义校验器
     * @return 注解任务注册器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoScheduledTaskRegistrar cocoScheduledTaskRegistrar(ConfigurableListableBeanFactory beanFactory,
            CocoTaskScheduler scheduler, CocoTaskDefinitionValidator validator) {
        return new CocoScheduledTaskRegistrar(beanFactory, scheduler, validator);
    }
}
