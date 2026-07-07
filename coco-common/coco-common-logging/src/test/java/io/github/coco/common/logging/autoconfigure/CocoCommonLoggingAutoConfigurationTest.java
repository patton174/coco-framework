package io.github.coco.common.logging.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.coco.common.logging.core.CocoLogHandle;
import io.github.coco.common.logging.core.CocoLogHandleRegistrar;
import io.github.coco.common.logging.core.CocoLogHandleRegistry;
import io.github.coco.common.logging.core.CocoLogHandles;
import io.github.coco.common.logging.core.CocoLogLevel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>
 * Coco 通用日志自动配置测试。
 * </p>
 * <p>
 * 验证访问日志句柄会正确吸收配置文件中的 logger 名称，同时不会在未显式配置时覆盖业务侧通过注册器提供的自定义句柄。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-logging}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoCommonLoggingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoCommonLoggingAutoConfiguration.class));

    /**
     * <p>
     * 显式配置访问日志 logger 名称时，应覆盖默认句柄的 logger 名称。
     * </p>
     */
    @Test
    void appliesConfiguredAccessLoggerNameToDefaultHandle() {
        this.contextRunner
                .withPropertyValues("coco.logging.access-log.logger-name=io.github.coco.access.biz")
                .run(context -> {
                    CocoLogHandle handle = context.getBean(CocoLogHandleRegistry.class)
                            .find(CocoLogHandles.ACCESS)
                            .orElseThrow();

                    assertEquals("io.github.coco.access.biz", handle.loggerName());
                    assertEquals(CocoLogLevel.INFO, handle.defaultLevel());
                });
    }

    /**
     * <p>
     * 未显式配置访问日志 logger 名称时，应保留业务侧注册器提供的自定义句柄。
     * </p>
     */
    @Test
    void preservesCustomAccessHandleWhenLoggerNameIsNotConfigured() {
        this.contextRunner
                .withUserConfiguration(CustomAccessHandleConfiguration.class)
                .run(context -> {
                    CocoLogHandle handle = context.getBean(CocoLogHandleRegistry.class)
                            .find(CocoLogHandles.ACCESS)
                            .orElseThrow();

                    assertEquals("io.github.coco.access.custom", handle.loggerName());
                    assertEquals(CocoLogLevel.WARN, handle.defaultLevel());
                });
    }

    /**
     * <p>
     * 显式配置访问日志 logger 名称时，应覆盖注册器提供的 logger 名称，同时保留未显式配置的原有默认级别。
     * </p>
     */
    @Test
    void mergesConfiguredAccessLoggerNameWithCustomHandleLevel() {
        this.contextRunner
                .withUserConfiguration(CustomAccessHandleConfiguration.class)
                .withPropertyValues("coco.logging.access-log.logger-name=io.github.coco.access.configured")
                .run(context -> {
                    CocoLogHandle handle = context.getBean(CocoLogHandleRegistry.class)
                            .find(CocoLogHandles.ACCESS)
                            .orElseThrow();

                    assertEquals("io.github.coco.access.configured", handle.loggerName());
                    assertEquals(CocoLogLevel.WARN, handle.defaultLevel());
                });
    }

    /**
     * <p>
     * 显式配置访问日志级别时，应覆盖注册器提供的默认级别。
     * </p>
     */
    @Test
    void appliesConfiguredAccessLogLevelWhenExplicitlySet() {
        this.contextRunner
                .withUserConfiguration(CustomAccessHandleConfiguration.class)
                .withPropertyValues("coco.logging.access-log.level=debug")
                .run(context -> {
                    CocoLogHandle handle = context.getBean(CocoLogHandleRegistry.class)
                            .find(CocoLogHandles.ACCESS)
                            .orElseThrow();

                    assertEquals("io.github.coco.access.custom", handle.loggerName());
                    assertEquals(CocoLogLevel.DEBUG, handle.defaultLevel());
                });
    }

    /**
     * <p>
     * 自定义访问日志句柄测试配置。
     * </p>
     */
    @Configuration(proxyBeanMethods = false)
    static class CustomAccessHandleConfiguration {

        /**
         * <p>
         * 注册业务侧自定义访问日志句柄。
         * </p>
         * @return 自定义日志句柄注册器
         */
        @Bean
        CocoLogHandleRegistrar customAccessHandleRegistrar() {
            return registry -> registry.register(CocoLogHandle.of(CocoLogHandles.ACCESS,
                    "io.github.coco.access.custom", CocoLogLevel.WARN));
        }
    }
}
