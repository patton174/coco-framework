package io.github.coco.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;

import io.github.coco.i18n.CocoMessage;
import io.github.coco.i18n.CocoMessageCode;
import io.github.coco.i18n.CocoMessageService;
import io.github.coco.i18n.internal.DefaultCocoLocaleResolver;
import io.github.coco.i18n.internal.DefaultCocoMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * 默认 Coco 消息服务测试。
 * <p>
 * 验证消息服务可以按默认语言和显式语言解析资源包，并处理缺省消息。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-i18n}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class DefaultCocoMessageServiceTest {

    private final CocoI18nProperties properties = new CocoI18nProperties();

    private final CocoMessageService messageService = new DefaultCocoMessageService(
            messageSource(), new DefaultCocoLocaleResolver(this.properties), true);

    @Test
    void resolvesMessageWithDefaultLocale() {
        String message = this.messageService.getMessage("sample.hello", "Coco");

        assertEquals("你好，Coco", message);
    }

    @Test
    void resolvesMessageWithExplicitLocale() {
        String message = this.messageService.getMessage("sample.hello", Locale.US, "Coco");

        assertEquals("Hello, Coco", message);
    }

    @Test
    void resolvesMessageCodeWithDefaultLocale() {
        String message = this.messageService.getMessage(SampleMessageCode.HELLO, "Coco");

        assertEquals("你好，Coco", message);
    }

    @Test
    void resolvesMessageCodeWithExplicitLocale() {
        String message = this.messageService.getMessage(SampleMessageCode.HELLO, Locale.US, "Coco");

        assertEquals("Hello, Coco", message);
    }

    @Test
    void resolvesMessageWithSpringLocaleContext() {
        LocaleContextHolder.setLocale(Locale.US);
        try {
            String message = this.messageService.getMessage("sample.hello", "Coco");

            assertEquals("Hello, Coco", message);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    void resolvesMessageCodeDefaultWhenResourceIsMissing() {
        String message = this.messageService.getMessage(SampleMessageCode.MISSING, "Coco");

        assertEquals("默认：Coco", message);
    }

    @Test
    void returnsCodeWhenMessageIsMissingAndCodeFallbackIsEnabled() {
        String message = this.messageService.getMessage("sample.missing");

        assertEquals("sample.missing", message);
    }

    @Test
    void resolvesCocoMessageDescriptor() {
        CocoMessage message = new CocoMessage("sample.hello", "默认：{0}", "Coco");

        assertEquals("你好，Coco", this.messageService.resolve(message));
    }

    @Test
    void resolvesFallbackMessageDescriptor() {
        CocoMessage message = new CocoMessage("sample.missing", "默认错误：{0}", "Coco");

        assertEquals("默认错误：Coco", this.messageService.resolve(message));
    }

    @Test
    void rejectsBlankCode() {
        assertThrows(IllegalArgumentException.class, () -> this.messageService.getMessage(" "));
    }

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }

    private enum SampleMessageCode implements CocoMessageCode {

        HELLO("sample.hello", "默认：{0}"),

        MISSING("sample.code-missing", "默认：{0}");

        private final String code;

        private final String defaultMessage;

        SampleMessageCode(String code, String defaultMessage) {
            this.code = code;
            this.defaultMessage = defaultMessage;
        }

        @Override
        public String code() {
            return this.code;
        }

        @Override
        public String defaultMessage() {
            return this.defaultMessage;
        }
    }
}
