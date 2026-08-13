package io.github.coco.feature.scheduler;

import java.time.Clock;
import java.util.Collection;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Coco 进程内任务调度自动配置。
 * <p>
 * 模块只声明名为 {@value #SCHEDULER_BEAN_NAME} 的专用调度器，不影响业务应用的全局 Spring 调度器。
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(CocoSchedulerProperties.class)
@ConditionalOnProperty(prefix = "coco.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CocoSchedulerAutoConfiguration {
    /** Coco 专用调度器 Bean 名称。 */
    public static final String SCHEDULER_BEAN_NAME = "cocoTaskScheduler";
    @Bean(name = SCHEDULER_BEAN_NAME, destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = SCHEDULER_BEAN_NAME)
    public ThreadPoolTaskScheduler cocoTaskScheduler(CocoSchedulerProperties properties) {
        CocoSchedulerConfigurationValidator.validate(properties);
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.getPoolSize()); scheduler.setThreadNamePrefix("coco-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true); scheduler.setAwaitTerminationSeconds((int) Math.min(Integer.MAX_VALUE, properties.getShutdownAwait().toSeconds()));
        return scheduler;
    }
    @Bean
    @ConditionalOnMissingBean
    public Clock cocoSchedulerClock() { return Clock.systemUTC(); }
    @Bean
    @ConditionalOnMissingBean(name = "cocoTaskExecutionLoggingListener")
    public CocoTaskExecutionListener cocoTaskExecutionLoggingListener() { return new Slf4jCocoTaskExecutionListener(); }
    @Bean
    @ConditionalOnMissingBean
    public CocoTaskSchedulerLifecycle cocoTaskSchedulerLifecycle(@Qualifier(SCHEDULER_BEAN_NAME) TaskScheduler scheduler,
            CocoSchedulerProperties properties, Collection<CocoScheduledTask> tasks,
            Collection<CocoTaskExecutionListener> listeners, Clock cocoSchedulerClock) {
        return new CocoTaskSchedulerLifecycle(scheduler, properties, tasks, listeners, cocoSchedulerClock);
    }
}
