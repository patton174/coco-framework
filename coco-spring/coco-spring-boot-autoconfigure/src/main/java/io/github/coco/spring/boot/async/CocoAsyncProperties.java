package io.github.coco.spring.boot.async;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco 异步线程池配置属性。
 * <p>
 * 绑定 {@code coco.async} 命名空间，控制框架内置异步线程池的核心参数。
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
@ConfigurationProperties(prefix = "coco.async")
public class CocoAsyncProperties {

    private int corePoolSize = 8;

    private int maxPoolSize = 32;

    private int queueCapacity = 1000;

    private String threadNamePrefix = "coco-async-";

    /**
     * <p>
     * 返回核心线程数。
     * </p>
     * @return 核心线程数
     */
    public int getCorePoolSize() {
        return this.corePoolSize;
    }

    /**
     * <p>
     * 设置核心线程数。
     * </p>
     * @param corePoolSize 核心线程数
     */
    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    /**
     * <p>
     * 返回最大线程数。
     * </p>
     * @return 最大线程数
     */
    public int getMaxPoolSize() {
        return this.maxPoolSize;
    }

    /**
     * <p>
     * 设置最大线程数。
     * </p>
     * @param maxPoolSize 最大线程数
     */
    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    /**
     * <p>
     * 返回任务队列容量。
     * </p>
     * @return 任务队列容量
     */
    public int getQueueCapacity() {
        return this.queueCapacity;
    }

    /**
     * <p>
     * 设置任务队列容量。
     * </p>
     * @param queueCapacity 任务队列容量
     */
    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    /**
     * <p>
     * 返回线程名称前缀。
     * </p>
     * @return 线程名称前缀
     */
    public String getThreadNamePrefix() {
        return this.threadNamePrefix;
    }

    /**
     * <p>
     * 设置线程名称前缀。
     * </p>
     * @param threadNamePrefix 线程名称前缀
     */
    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }
}
