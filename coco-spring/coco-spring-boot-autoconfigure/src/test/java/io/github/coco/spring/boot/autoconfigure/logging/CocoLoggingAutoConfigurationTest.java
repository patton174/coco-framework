package io.github.coco.spring.boot.autoconfigure.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.coco.logging.AsyncCocoLogSink;
import io.github.coco.logging.CocoAsyncLogDropListener;
import io.github.coco.logging.CocoLogHandle;
import io.github.coco.logging.CocoLogHandleRegistrar;
import io.github.coco.logging.CocoLogHandleRegistry;
import io.github.coco.logging.CocoLogHandles;
import io.github.coco.logging.CocoLogLevel;
import io.github.coco.logging.CocoLogSink;
import io.github.coco.logging.CocoLoggingProperties;
import io.github.coco.logging.Slf4jCocoAsyncLogDropListener;
import io.github.coco.logging.Slf4jCocoLogSink;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
 *   <li>模块：{@code coco-logging}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoLoggingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoLoggingAutoConfiguration.class));

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
     * 原有单参数工厂方法应继续可直接调用。
     * </p>
     */
    @Test
    void keepsSingleArgumentLogSinkFactoryMethod() {
        CocoLogSink sink = new CocoLoggingAutoConfiguration().cocoLogSink(new CocoLoggingProperties());

        assertThat(sink).isInstanceOfSatisfying(AsyncCocoLogSink.class, AsyncCocoLogSink::close);
    }

    /**
     * <p>
     * 未提供自定义监听器时，应注册默认 SLF4J 丢弃监听器。
     * </p>
     */
    @Test
    void providesDefaultAsyncLogDropListener() {
        this.contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(CocoAsyncLogDropListener.class);
            assertThat(context.getBean(CocoAsyncLogDropListener.class))
                    .isInstanceOf(Slf4jCocoAsyncLogDropListener.class);
        });
    }

    /**
     * <p>
     * 唯一自定义监听器应替换默认监听器并注入异步输出器。
     * </p>
     */
    @Test
    void replacesDefaultAsyncLogDropListenerWithUniqueCustomBean() {
        this.contextRunner
                .withUserConfiguration(CustomDropListenerConfiguration.class)
                .run(context -> {
                    CocoAsyncLogDropListener customListener = context.getBean("customDropListener",
                            CocoAsyncLogDropListener.class);

                    assertThat(context).hasNotFailed().hasSingleBean(CocoAsyncLogDropListener.class);
                    assertThat(context.getBean(CocoAsyncLogDropListener.class)).isSameAs(customListener);
                    assertThat(context.getBean(CocoLogSink.class))
                            .isInstanceOfSatisfying(AsyncCocoLogSink.class,
                                    sink -> assertThat(sink).extracting("dropListener").isSameAs(customListener));
                });
    }

    /**
     * <p>
     * 多个监听器存在时，应使用业务显式标记的主候选。
     * </p>
     */
    @Test
    void usesPrimaryDropListenerWhenMultipleBeansExist() {
        this.contextRunner
                .withUserConfiguration(PrimaryDropListenerConfiguration.class)
                .run(context -> {
                    CocoAsyncLogDropListener primaryListener = context.getBean("primaryDropListener",
                            CocoAsyncLogDropListener.class);

                    assertThat(context).hasNotFailed().hasSingleBean(CocoLogSink.class);
                    assertThat(context.getBean(CocoLogSink.class))
                            .isInstanceOfSatisfying(AsyncCocoLogSink.class,
                                    sink -> assertThat(sink).extracting("dropListener").isSameAs(primaryListener));
                });
    }

    /**
     * <p>
     * 多个无主监听器应导致单值注入失败，避免静默选择。
     * </p>
     */
    @Test
    void failsWhenMultipleDropListenersHaveNoPrimaryBean() {
        this.contextRunner
                .withUserConfiguration(MultipleDropListenersConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class);
                });
    }

    /**
     * <p>
     * 自定义日志输出器应继续优先于默认异步输出链路。
     * </p>
     */
    @Test
    void preservesCustomLogSinkPriority() {
        this.contextRunner
                .withUserConfiguration(CustomLogSinkConfiguration.class)
                .run(context -> {
                    CocoLogSink customSink = context.getBean("customLogSink", CocoLogSink.class);

                    assertThat(context).hasNotFailed().hasSingleBean(CocoLogSink.class);
                    assertThat(context.getBean(CocoLogSink.class)).isSameAs(customSink);
                    assertThat(customSink).isNotInstanceOf(AsyncCocoLogSink.class);
                });
    }

    /**
     * <p>
     * 自定义输出器接管时，不应解析未使用的多监听器候选。
     * </p>
     */
    @Test
    void ignoresAmbiguousDropListenersWhenCustomLogSinkTakesOver() {
        this.contextRunner
                .withUserConfiguration(MultipleDropListenersConfiguration.class, CustomLogSinkConfiguration.class)
                .run(context -> {
                    CocoLogSink customSink = context.getBean("customLogSink", CocoLogSink.class);

                    assertThat(context).hasNotFailed().hasSingleBean(CocoLogSink.class);
                    assertThat(context.getBean(CocoLogSink.class)).isSameAs(customSink);
                    assertThat(context).getBeans(CocoAsyncLogDropListener.class).hasSize(2);
                });
    }

    /**
     * <p>
     * 关闭异步日志时，应直接使用 SLF4J 输出器而不创建溢出链路。
     * </p>
     */
    @Test
    void usesSynchronousSlf4jSinkWhenAsyncLoggingIsDisabled() {
        this.contextRunner
                .withPropertyValues("coco.logging.async.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(CocoLogSink.class);
                    assertThat(context.getBean(CocoLogSink.class))
                            .isInstanceOf(Slf4jCocoLogSink.class)
                            .isNotInstanceOf(AsyncCocoLogSink.class);
                });
    }

    /**
     * <p>
     * 关闭异步日志时，不应解析未使用的多监听器候选。
     * </p>
     */
    @Test
    void ignoresAmbiguousDropListenersWhenAsyncLoggingIsDisabled() {
        this.contextRunner
                .withUserConfiguration(MultipleDropListenersConfiguration.class)
                .withPropertyValues("coco.logging.async.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(CocoLogSink.class);
                    assertThat(context.getBean(CocoLogSink.class)).isInstanceOf(Slf4jCocoLogSink.class);
                    assertThat(context).getBeans(CocoAsyncLogDropListener.class).hasSize(2);
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

    /**
     * <p>
     * 自定义异步日志丢弃监听器测试配置。
     * </p>
     */
    @Configuration(proxyBeanMethods = false)
    static class CustomDropListenerConfiguration {

        /**
         * <p>
         * 注册唯一自定义监听器。
         * </p>
         * @return 自定义异步日志丢弃监听器
         */
        @Bean
        CocoAsyncLogDropListener customDropListener() {
            return (level, handleName, totalDropped) -> {
            };
        }
    }

    /**
     * <p>
     * 多异步日志丢弃监听器测试配置。
     * </p>
     */
    @Configuration(proxyBeanMethods = false)
    static class MultipleDropListenersConfiguration {

        /**
         * <p>
         * 注册第一个监听器。
         * </p>
         * @return 第一个监听器
         */
        @Bean
        CocoAsyncLogDropListener firstDropListener() {
            return (level, handleName, totalDropped) -> {
            };
        }

        /**
         * <p>
         * 注册第二个监听器。
         * </p>
         * @return 第二个监听器
         */
        @Bean
        CocoAsyncLogDropListener secondDropListener() {
            return (level, handleName, totalDropped) -> {
            };
        }
    }

    /**
     * <p>
     * 带主候选的多异步日志丢弃监听器测试配置。
     * </p>
     */
    @Configuration(proxyBeanMethods = false)
    static class PrimaryDropListenerConfiguration {

        /**
         * <p>
         * 注册主监听器。
         * </p>
         * @return 主监听器
         */
        @Bean
        @Primary
        CocoAsyncLogDropListener primaryDropListener() {
            return (level, handleName, totalDropped) -> {
            };
        }

        /**
         * <p>
         * 注册次监听器。
         * </p>
         * @return 次监听器
         */
        @Bean
        CocoAsyncLogDropListener secondaryDropListener() {
            return (level, handleName, totalDropped) -> {
            };
        }
    }

    /**
     * <p>
     * 自定义日志输出器测试配置。
     * </p>
     */
    @Configuration(proxyBeanMethods = false)
    static class CustomLogSinkConfiguration {

        /**
         * <p>
         * 注册自定义日志输出器。
         * </p>
         * @return 自定义日志输出器
         */
        @Bean
        CocoLogSink customLogSink() {
            return record -> {
            };
        }
    }
}
