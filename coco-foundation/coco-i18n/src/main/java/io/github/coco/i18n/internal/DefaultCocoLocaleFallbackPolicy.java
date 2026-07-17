package io.github.coco.i18n.internal;

import java.util.Locale;
import java.util.Objects;

import io.github.coco.i18n.CocoI18nProperties;
import io.github.coco.i18n.CocoLocaleFallbackPolicy;

/**
 * 默认 Coco 请求语言回退策略。
 * <p>
 * 保留受支持语言的完整 BCP 47 locale，以便 {@code ResourceBundle} 继续优先匹配
 * {@code zh_TW}、{@code en_US} 等精确资源；不受支持的语言回退到配置的默认语言。
 * </p>
 *
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-i18n}</li>
 * </ul>
 * 空 {@code supportedLanguages} 不过滤请求语言；非空列表是显式允许列表。
 *
 * @author patton174
 *
 * @since 1.0.0
 */
public final class DefaultCocoLocaleFallbackPolicy implements CocoLocaleFallbackPolicy {

    /**
     * {@inheritDoc}
     */
    @Override
    public Locale resolveLocale(Locale locale, CocoI18nProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        if (locale == null) {
            return properties.getDefaultLocale();
        }
        if (properties.getSupportedLanguages().isEmpty()) {
            return locale;
        }
        boolean supported = properties.getSupportedLanguages().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .anyMatch(language -> matchesSupportedLocale(language, locale));
        return supported ? locale : properties.getDefaultLocale();
    }

    private static boolean matchesSupportedLocale(String supportedLanguage, Locale requestedLocale) {
        if (supportedLanguage.isBlank()) {
            return false;
        }
        Locale supportedLocale = Locale.forLanguageTag(supportedLanguage.replace('_', '-'));
        if (!supportedLocale.getLanguage().equalsIgnoreCase(requestedLocale.getLanguage())) {
            return false;
        }
        if (supportedLocale.getCountry().isEmpty() && supportedLocale.getScript().isEmpty()
                && supportedLocale.getVariant().isEmpty()) {
            return true;
        }
        return supportedLocale.equals(requestedLocale);
    }
}
