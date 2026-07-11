package io.github.coco.spring.boot.banner;

import java.time.Duration;

import io.github.coco.common.logging.lifecycle.CocoLifecycleLogger;
import io.github.coco.spring.boot.logging.CocoNodeLogRendererBootstrap;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Coco Spring 应用运行监听器。
 * <p>
 * 在 Spring Boot 打印 banner 前安装 Coco Spring banner，并根据 Coco 日志默认策略关闭 Spring 原始启动摘要日志。
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
 * @since 1.0.0
 */
public final class CocoSpringApplicationRunListener implements SpringApplicationRunListener {

    private final SpringApplication application;

    /**
     * <p>
     * 创建 Coco Spring 应用运行监听器。
     * </p>
     * @param application Spring 应用
     * @param args 启动参数
     */
    public CocoSpringApplicationRunListener(SpringApplication application, String[] args) {
        this.application = application;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void environmentPrepared(ConfigurableBootstrapContext bootstrapContext,
            ConfigurableEnvironment environment) {
        if (environment.getProperty("coco.logging.enabled", Boolean.class, true)) {
            CocoNodeLogRendererBootstrap.install(environment);
        }
        if (environment.getProperty("coco.banner.enabled", Boolean.class, true)
                && !environment.containsProperty("spring.banner.location")) {
            this.application.setBanner(new CocoSpringBanner());
        }
        else if (!environment.getProperty("coco.banner.enabled", Boolean.class, true)) {
            this.application.setBannerMode(Banner.Mode.OFF);
        }
        if (!environment.getProperty("spring.main.log-startup-info", Boolean.class, true)) {
            this.application.setLogStartupInfo(false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void starting(ConfigurableBootstrapContext bootstrapContext) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void contextPrepared(ConfigurableApplicationContext context) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void contextLoaded(ConfigurableApplicationContext context) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void started(ConfigurableApplicationContext context, Duration timeTaken) {
        if (context.getEnvironment().getProperty("coco.logging.enabled", Boolean.class, true)) {
            lifecycleLogger(context).started(context, timeTaken);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void ready(ConfigurableApplicationContext context, Duration timeTaken) {
        if (context.getEnvironment().getProperty("coco.logging.enabled", Boolean.class, true)) {
            lifecycleLogger(context).ready(context, timeTaken);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void failed(ConfigurableApplicationContext context, Throwable exception) {
        if (context == null || context.getEnvironment().getProperty("coco.logging.enabled", Boolean.class, true)) {
            lifecycleLogger(context).failed(context, exception);
        }
    }

    private static CocoLifecycleLogger lifecycleLogger(ConfigurableApplicationContext context) {
        if (context != null) {
            try {
                return context.getBean(CocoLifecycleLogger.class);
            }
            catch (RuntimeException ex) {
                // 启动早期失败时可能尚未完成日志 Bean 装配，回退到默认生命周期记录器。
            }
        }
        return new CocoLifecycleLogger();
    }
}
