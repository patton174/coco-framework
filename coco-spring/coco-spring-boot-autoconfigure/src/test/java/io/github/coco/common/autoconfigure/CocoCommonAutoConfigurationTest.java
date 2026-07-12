package io.github.coco.common.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.coco.i18n.CocoMessage;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.i18n.CocoMessageCode;
import io.github.coco.i18n.CocoMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
