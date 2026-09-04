package io.github.coco.spring.boot.async;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Coco 异步线程池自动配置。
 * <p>
 * 自动创建带有上下文传播能力的 {@link ThreadPoolTaskExecutor}，确保异步任务可以访问提交线程的请求上下文和 Trace 上下文。
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
@AutoConfiguration
@ConditionalOnClass(ThreadPoolTaskExecutor.class)
@ConditionalOnProperty(prefix = "coco.async", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CocoAsyncProperties.class)
public class CocoAsyncAutoConfiguration {

    /**
     * <p>
     * 创建带有上下文传播装饰器的异步任务执行器。
     * </p>
     * @param properties 异步线程池配置属性
     * @return 异步任务执行器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoTaskExecutor")
    public ThreadPoolTaskExecutor cocoTaskExecutor(CocoAsyncProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());
        executor.setTaskDecorator(new CocoContextTaskDecorator());
        executor.initialize();
        return executor;
    }
}
