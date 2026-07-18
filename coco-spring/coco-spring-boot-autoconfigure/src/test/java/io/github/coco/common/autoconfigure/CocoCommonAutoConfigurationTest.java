package io.github.coco.common.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import io.github.coco.CocoCommonProperties;
import io.github.coco.i18n.CocoLocaleFallbackPolicy;
import io.github.coco.i18n.CocoLocaleResolver;
import io.github.coco.i18n.CocoMessage;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.i18n.CocoMessageCode;
import io.github.coco.i18n.CocoMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.support.StaticMessageSource;

/**
 * Coco 通用基础设施自动装配测试。
 * <p>
 * 验证 starter 引入后可自动创建国际化消息服务，并且不覆盖业务应用自己的消息源。
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
class CocoCommonAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoCommonAutoConfiguration.class));

    @Test
    void keepsPublishedOneArgumentLocaleResolverFactoryBinaryLinkable() throws Throwable {
        MethodHandle factory = MethodHandles.publicLookup().findVirtual(
                CocoCommonAutoConfiguration.class,
                "cocoLocaleResolver",
                MethodType.methodType(CocoLocaleResolver.class, CocoCommonProperties.class));

        CocoLocaleResolver resolver = (CocoLocaleResolver) factory.invokeExact(
                new CocoCommonAutoConfiguration(), new CocoCommonProperties());

        assertEquals(Locale.SIMPLIFIED_CHINESE, resolver.resolveLocale());
    }

    @Test
    void beanFactoryPathUsesCustomLocaleFallbackPolicyWithoutReplacingIt() {
        CocoLocaleFallbackPolicy customPolicy = (locale, properties) -> Locale.CANADA_FRENCH;

        this.contextRunner
                .withBean(CocoLocaleFallbackPolicy.class, () -> customPolicy)
                .run(context -> {
                    assertSame(customPolicy, context.getBean(CocoLocaleFallbackPolicy.class));
                    assertEquals(Locale.CANADA_FRENCH,
                            context.getBean(CocoLocaleResolver.class).resolveLocale());
                });
    }

    @Test
    void createsCocoMessageService() {
        this.contextRunner.run(context -> assertTrue(context.containsBean("cocoMessageService")));
    }

    @Test
    void resolvesFrameworkMessageWithDefaultLocale() {
        this.contextRunner.run(context -> {
            CocoMessageService messageService = context.getBean(CocoMessageService.class);

            assertEquals("未知错误", messageService.getMessage("coco.error.unknown"));
        });
    }

    @Test
    void resolvesFrameworkErrorCodeWithDefaultLocale() {
        this.contextRunner.run(context -> {
            CocoMessageService messageService = context.getBean(CocoMessageService.class);

            assertEquals("参数不合法：name", messageService.getMessage(TestMessageCode.INVALID_ARGUMENT, "name"));
        });
    }

    @Test
    void resolvesNotFoundMessageDescriptorWithDefaultLocale() {
        this.contextRunner.run(context -> {
            CocoMessageService messageService = context.getBean(CocoMessageService.class);

            assertEquals("资源不存在：user",
                    messageService.resolve(new CocoMessage("coco.error.not-found", "coco.error.not-found", "user")));
        });
    }

    @Test
    void resolvesInternalGuardMessageDescriptorWithDefaultLocale() {
        this.contextRunner.run(context -> {
            CocoMessageService messageService = context.getBean(CocoMessageService.class);

            assertEquals("异常编码不能为空",
                    messageService.resolve(new CocoMessage("coco.error.missing-error-code",
                            "coco.error.missing-error-code")));
        });
    }

    @Test
    void appliesDefaultLocaleFromApplicationProperties() {
        this.contextRunner
                .withPropertyValues("coco.common.i18n.default-locale=en-US")
                .run(context -> {
                    CocoMessageService messageService = context.getBean(CocoMessageService.class);

                    assertEquals("Unknown error", messageService.getMessage("coco.error.unknown"));
                });
    }

    @Test
    void keepsApplicationMessageSourceSeparateFromCocoMessageSource() {
        this.contextRunner
                .withUserConfiguration(UserMessageSourceConfiguration.class)
                .run(context -> {
                    MessageSource applicationMessageSource = context.getBean("messageSource", MessageSource.class);
                    MessageSource cocoMessageSource = context.getBean("cocoMessageSource", MessageSource.class);

                    assertNotSame(applicationMessageSource, cocoMessageSource);
                    assertEquals("application", applicationMessageSource.getMessage("app.only", null, null));
                });
    }

    @Test
    void resolvesRegisteredModuleMessageBundle() {
        this.contextRunner
                .withUserConfiguration(ModuleMessageBundleConfiguration.class)
                .run(context -> {
                    CocoMessageService messageService = context.getBean(CocoMessageService.class);

                    assertEquals("模块消息", messageService.getMessage("module.hello"));
                });
    }

    @Test
    void createsStableMessageSourceSnapshotsWhileTheLiveBasenameListChangesConcurrently()
            throws InterruptedException {
        List<String> configuredBasenames = IntStream.range(0, 128)
                .mapToObj(index -> "application-messages-" + index)
                .toList();
        CocoCommonProperties properties = new CocoCommonProperties();
        properties.getI18n().setBasename(configuredBasenames);
        List<String> liveBasenames = properties.getI18n().getBasename();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        ObjectProvider<CocoMessageBundleRegistrar> registrars =
                beanFactory.getBeanProvider(CocoMessageBundleRegistrar.class);
        CocoCommonAutoConfiguration configuration = new CocoCommonAutoConfiguration();
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread writer = new Thread(() -> {
            try {
                await(start, failure);
                for (int index = 0; index < 20_000 && failure.get() == null; index++) {
                    liveBasenames.add("changing-messages");
                    liveBasenames.remove("changing-messages");
                }
            }
            catch (Throwable ex) {
                failure.compareAndSet(null, ex);
            }
        }, "coco-i18n-basename-writer");
        Thread reader = new Thread(() -> {
            await(start, failure);
            try {
                for (int index = 0; index < 2_000 && failure.get() == null; index++) {
                    MessageSource source = configuration.cocoMessageSource(properties, registrars);
                    List<String> snapshot = new ArrayList<>(
                            ((ResourceBundleMessageSource) source).getBasenameSet());
                    if (snapshot.isEmpty() || !"coco-messages".equals(snapshot.get(snapshot.size() - 1))) {
                        throw new AssertionError("Framework bundle was not last in the snapshot: " + snapshot);
                    }
                    snapshot.remove("coco-messages");
                    snapshot.remove("changing-messages");
                    if (!configuredBasenames.equals(snapshot)) {
                        throw new AssertionError("Observed partial or reordered basename snapshot: " + snapshot);
                    }
                }
            }
            catch (Throwable ex) {
                failure.compareAndSet(null, ex);
            }
        }, "coco-i18n-basename-reader");

        writer.start();
        reader.start();
        start.countDown();
        writer.join();
        reader.join();

        assertTrue(failure.get() == null,
                () -> "Concurrent basename snapshot failed: " + failure.get());
    }

    @Configuration(proxyBeanMethods = false)
    static class UserMessageSourceConfiguration {

        @Bean
        MessageSource messageSource() {
            StaticMessageSource messageSource = new StaticMessageSource();
            messageSource.addMessage("app.only", java.util.Locale.getDefault(), "application");
            return messageSource;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ModuleMessageBundleConfiguration {

        @Bean
        CocoMessageBundleRegistrar moduleMessageBundleRegistrar() {
            return registry -> registry.add("module-messages");
        }
    }

    private static void await(CountDownLatch start, AtomicReference<Throwable> failure) {
        try {
            start.await();
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            failure.compareAndSet(null, ex);
        }
    }

    private enum TestMessageCode implements CocoMessageCode {

        INVALID_ARGUMENT("coco.error.invalid-argument");

        private final String code;

        TestMessageCode(String code) {
            this.code = code;
        }

        @Override
        public String code() {
            return this.code;
        }
    }
}
