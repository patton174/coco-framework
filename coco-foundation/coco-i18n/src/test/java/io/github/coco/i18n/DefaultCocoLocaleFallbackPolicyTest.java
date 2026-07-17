package io.github.coco.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import io.github.coco.i18n.internal.DefaultCocoLocaleFallbackPolicy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultCocoLocaleFallbackPolicyTest {

    private final DefaultCocoLocaleFallbackPolicy policy = new DefaultCocoLocaleFallbackPolicy();

    @ParameterizedTest
    @MethodSource("requestLocales")
    void leavesEveryRequestLocaleUntouchedWhenFilteringIsDisabled(Locale locale) {
        assertThat(this.policy.resolveLocale(locale, new CocoI18nProperties())).isSameAs(locale);
    }

    @ParameterizedTest
    @MethodSource("requestLocales")
    void onlyUsesTheDefaultForNullWhenFilteringIsDisabled(Locale locale) {
        CocoI18nProperties properties = new CocoI18nProperties();
        properties.setDefaultLocale(Locale.JAPAN);

        assertThat(this.policy.resolveLocale(locale, properties)).isSameAs(locale);
        assertThat(this.policy.resolveLocale(null, properties)).isSameAs(Locale.JAPAN);
    }

    @ParameterizedTest
    @MethodSource("explicitAllowlistLocales")
    void explicitAllowlistPreservesMatchingObjectAndFallsBackForOtherLocales(Locale locale) {
        CocoI18nProperties properties = new CocoI18nProperties();
        properties.setDefaultLocale(Locale.JAPAN);
        properties.setSupportedLanguages(List.of(locale.toLanguageTag()));

        assertThat(this.policy.resolveLocale(locale, properties)).isSameAs(locale);
        Locale rejectedLocale = "zz-ZZ".equals(locale.toLanguageTag())
                ? Locale.forLanguageTag("fr-FR")
                : Locale.forLanguageTag("zz-ZZ");
        assertThat(this.policy.resolveLocale(rejectedLocale, properties))
                .isSameAs(Locale.JAPAN);
    }

    private static Stream<Locale> requestLocales() {
        return Stream.of(
                Locale.ROOT,
                Locale.forLanguageTag("zh-TW"),
                Locale.forLanguageTag("zh-Hant-TW"),
                Locale.forLanguageTag("zh-HK"),
                Locale.forLanguageTag("zh-CN"),
                Locale.forLanguageTag("zh-Hans"),
                Locale.forLanguageTag("zh"),
                Locale.forLanguageTag("en-US"),
                Locale.forLanguageTag("ja-JP"),
                Locale.forLanguageTag("fr-FR"),
                Locale.forLanguageTag("zz-ZZ"));
    }

    private static Stream<Locale> explicitAllowlistLocales() {
        return requestLocales().filter(locale -> !Locale.ROOT.equals(locale));
    }
}
