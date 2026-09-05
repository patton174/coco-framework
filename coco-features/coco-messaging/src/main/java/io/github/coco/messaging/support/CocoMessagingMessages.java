package io.github.coco.messaging.support;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.springframework.context.i18n.LocaleContextHolder;

/**
 * 消息模块内部国际化消息解析器。
 */
public final class CocoMessagingMessages {

    private static final String BUNDLE_NAME = "coco-messaging-messages";

    private CocoMessagingMessages() {
    }

    /**
     * 解析模块消息。
     * @param code 消息编码
     * @param arguments 消息参数
     * @return 已格式化消息
     */
    public static String message(String code, Object... arguments) {
        try {
            Locale locale = LocaleContextHolder.getLocale();
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale == null ? Locale.getDefault() : locale);
            return MessageFormat.format(bundle.getString(code), arguments);
        }
        catch (MissingResourceException exception) {
            return code;
        }
    }
}
