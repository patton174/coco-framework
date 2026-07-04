package io.github.coco.common.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;

import io.github.coco.common.exception.CocoException;
import org.junit.jupiter.api.Test;
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
 *   <li>模块：{@code coco-common-i18n}</li>
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
    void resolvesCocoExceptionDescriptor() {
        CocoException exception = new CocoException("sample.missing", "默认错误：{0}", "Coco");

        assertEquals("默认错误：Coco", this.messageService.resolve(exception));
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
}
