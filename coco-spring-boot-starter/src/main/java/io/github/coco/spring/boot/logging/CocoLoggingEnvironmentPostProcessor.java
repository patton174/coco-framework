package io.github.coco.spring.boot.logging;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Coco 日志环境后处理器。
 * <p>
 * 在 Spring Boot 初始化日志系统前注入 Coco 默认日志样式，并降低 Spring 与内嵌容器启动日志噪音。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-spring-boot-starter}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoLoggingEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "cocoLoggingDefaults";

    /**
     * {@inheritDoc}
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty("coco.logging.enabled", Boolean.class, true)) {
            return;
        }
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("logging.pattern.console", environment.getProperty("coco.logging.console-pattern",
                CocoLoggingProperties.DEFAULT_CONSOLE_PATTERN));
        defaults.put("logging.level.io.github.coco", "INFO");
        defaults.put("logging.level.io.github.coco.access", "INFO");
        defaults.put("logging.level.io.github.coco.lifecycle", "INFO");
        if (environment.getProperty("coco.logging.quiet-spring", Boolean.class, true)) {
            defaults.put("spring.main.log-startup-info", "false");
            defaults.put("logging.level.org.springframework", "WARN");
            defaults.put("logging.level.org.springframework.boot.web.embedded.tomcat", "WARN");
            defaults.put("logging.level.org.springframework.web.servlet.DispatcherServlet", "WARN");
            defaults.put("logging.level.org.apache.catalina", "WARN");
            defaults.put("logging.level.org.apache.tomcat", "WARN");
        }
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
