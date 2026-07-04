package io.github.coco.common.i18n;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

import io.github.coco.common.exception.CocoException;
import org.springframework.context.MessageSource;

/**
 * 默认 Coco 消息服务。
 * <p>
 * 基于 Spring {@code MessageSource} 解析消息文本，并集中处理默认语言和缺省文本策略。
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
public final class DefaultCocoMessageService implements CocoMessageService {

    private final MessageSource messageSource;

    private final CocoLocaleResolver localeResolver;

    private final boolean useCodeAsDefaultMessage;

    public DefaultCocoMessageService(MessageSource messageSource, CocoLocaleResolver localeResolver,
            boolean useCodeAsDefaultMessage) {
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource must not be null");
        this.localeResolver = Objects.requireNonNull(localeResolver, "localeResolver must not be null");
        this.useCodeAsDefaultMessage = useCodeAsDefaultMessage;
    }

    @Override
    public String getMessage(String code, Object... args) {
        return getMessage(code, this.localeResolver.resolveLocale(), args);
    }

    @Override
    public String getMessage(String code, Locale locale, Object... args) {
        String checkedCode = requireCode(code);
        Locale checkedLocale = requireLocale(locale);
        Object[] checkedArgs = copyArgs(args);
        if (this.useCodeAsDefaultMessage) {
            return this.messageSource.getMessage(checkedCode, checkedArgs, checkedCode, checkedLocale);
        }
        return this.messageSource.getMessage(checkedCode, checkedArgs, checkedLocale);
    }

    @Override
    public String getMessageOrDefault(String code, String defaultMessage, Object... args) {
        return getMessageOrDefault(code, defaultMessage, this.localeResolver.resolveLocale(), args);
    }

    @Override
    public String getMessageOrDefault(String code, String defaultMessage, Locale locale, Object... args) {
        String checkedCode = requireCode(code);
        Locale checkedLocale = requireLocale(locale);
        String fallback = defaultMessage == null && this.useCodeAsDefaultMessage ? checkedCode : defaultMessage;
        return this.messageSource.getMessage(checkedCode, copyArgs(args), fallback, checkedLocale);
    }

    @Override
    public String resolve(CocoMessage message) {
        return resolve(message, this.localeResolver.resolveLocale());
    }

    @Override
    public String resolve(CocoMessage message, Locale locale) {
        CocoMessage checkedMessage = Objects.requireNonNull(message, "message must not be null");
        return getMessageOrDefault(checkedMessage.code(), checkedMessage.defaultMessage(), locale,
                checkedMessage.args());
    }

    @Override
    public String resolve(CocoException exception) {
        return resolve(exception, this.localeResolver.resolveLocale());
    }

    @Override
    public String resolve(CocoException exception, Locale locale) {
        CocoException checkedException = Objects.requireNonNull(exception, "exception must not be null");
        return getMessageOrDefault(checkedException.code(), checkedException.defaultMessage(), locale,
                checkedException.args());
    }

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("message code must not be blank");
        }
        return code;
    }

    private static Locale requireLocale(Locale locale) {
        return Objects.requireNonNull(locale, "locale must not be null");
    }

    private static Object[] copyArgs(Object[] args) {
        return args == null ? new Object[0] : Arrays.copyOf(args, args.length);
    }
}
