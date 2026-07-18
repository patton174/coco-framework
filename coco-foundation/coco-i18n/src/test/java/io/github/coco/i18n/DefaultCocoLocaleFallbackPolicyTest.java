package io.github.coco.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import io.github.coco.i18n.internal.CocoLanguageTagNormalizer;
import io.github.coco.i18n.internal.DefaultCocoLocaleFallbackPolicy;
import org.junit.jupiter.api.Test;
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

    @Test
    void privateUseTagDoesNotMatchLocaleRoot() {
        CocoI18nProperties properties = propertiesWithSupportedLanguage("x-private");
        Locale privateUseLocale = Locale.forLanguageTag("X-PRIVATE");

        assertThat(this.policy.resolveLocale(privateUseLocale, properties)).isSameAs(privateUseLocale);
        assertThat(this.policy.resolveLocale(Locale.ROOT, properties)).isSameAs(Locale.JAPAN);
    }

    @Test
    void unicodeExtensionDoesNotMatchPlainLocale() {
        CocoI18nProperties properties = propertiesWithSupportedLanguage("en-u-ca-gregory");

        assertThat(this.policy.resolveLocale(Locale.US, properties)).isSameAs(Locale.JAPAN);
    }

    @Test
    void exactExtendedLocalePreservesTheRequestedObject() {
        Locale extendedLocale = Locale.forLanguageTag("en-u-ca-gregory");
        CocoI18nProperties properties = propertiesWithSupportedLanguage(extendedLocale.toLanguageTag());

        assertThat(this.policy.resolveLocale(extendedLocale, properties)).isSameAs(extendedLocale);
    }

    @Test
    void plainLanguageTagStillBroadlyMatchesRegionalLocaleWithoutMutatingConfiguration() {
        CocoI18nProperties properties = propertiesWithSupportedLanguage("en");
        List<String> configuredLanguages = properties.getSupportedLanguages();

        assertThat(this.policy.resolveLocale(Locale.US, properties)).isSameAs(Locale.US);
        assertThat(configuredLanguages).containsExactly("en");
        assertThat(properties.getSupportedLanguages()).containsExactly("en");
    }

    @Test
    void regionalTagRequiresAnExactPlainLocaleMatch() {
        CocoI18nProperties properties = propertiesWithSupportedLanguage("en-US");

        assertThat(this.policy.resolveLocale(Locale.US, properties)).isSameAs(Locale.US);
        assertThat(this.policy.resolveLocale(Locale.UK, properties)).isSameAs(Locale.JAPAN);
        assertThat(this.policy.resolveLocale(Locale.forLanguageTag("en-US-u-ca-gregory"), properties))
                .isSameAs(Locale.JAPAN);
    }

    @Test
    void deprecatedPreferredValueRegionsRemainDistinct() {
        CocoI18nProperties properties = propertiesWithSupportedLanguage("en-BU");
        Locale burma = Locale.forLanguageTag("en-BU");
        Locale myanmar = Locale.forLanguageTag("en-MM");

        assertThat(this.policy.resolveLocale(burma, properties)).isSameAs(burma);
        assertThat(this.policy.resolveLocale(myanmar, properties)).isSameAs(Locale.JAPAN);
        assertThat(CocoLanguageTagNormalizer.semanticKey(burma))
                .isNotEqualTo(CocoLanguageTagNormalizer.semanticKey(myanmar));
    }

    @Test
    void variantTagMatchesIgnoringCaseInBothDirections() {
        assertTagMatchPreservesRequestedLocale("en-US-posix", "en-US-POSIX");
        assertTagMatchPreservesRequestedLocale("en-US-POSIX", "en-US-posix");
    }

    @Test
    void multipleVariantsMatchIgnoringCase() {
        assertTagMatchPreservesRequestedLocale("sl-rozaj-biske", "SL-ROZAJ-BISKE");
    }

    @Test
    void variantAndUnicodeExtensionMatchIgnoringCase() {
        assertTagMatchPreservesRequestedLocale(
                "en-US-posix-u-ca-gregory", "EN-us-POSIX-u-CA-GREGORY");
    }

    @Test
    void differentVariantFallsBackToDefaultLocale() {
        CocoI18nProperties properties = propertiesWithSupportedLanguage("en-US-posix");

        assertThat(this.policy.resolveLocale(Locale.forLanguageTag("en-US-revised"), properties))
                .isSameAs(Locale.JAPAN);
    }

    @Test
    void transformedFieldsMatchRegardlessOfTkeyOrder() {
        assertTagMatchPreservesRequestedLocale(
                "en-t-es-419-h0-hybrid-m0-ungegn",
                "en-t-es-419-m0-ungegn-h0-hybrid");
    }

    @Test
    void transformedFieldValuesMustRemainEquivalent() {
        assertTagFallsBack(
                "en-t-es-419-h0-hybrid-m0-ungegn",
                "en-t-es-419-m0-prprname-h0-hybrid");
    }

    @Test
    void orderedSubtagSequencesAreNotConflated() {
        assertTagFallsBack("sl-rozaj-biske", "sl-biske-rozaj");
        assertTagFallsBack("en-x-foo-bar", "en-x-bar-foo");
        assertTagFallsBack("en-a-foo-bar", "en-a-bar-foo");
        assertTagFallsBack("en-t-m0-foo-bar", "en-t-m0-bar-foo");
    }

    @Test
    void combinedVariantUnicodeTransformedAndPrivateUseTagMatchesSemantically() {
        assertTagMatchPreservesRequestedLocale(
                "en-US-posix-u-nu-thai-ca-gregory-t-es-419-h0-hybrid-m0-ungegn-x-foo-bar",
                "EN-us-POSIX-t-es-419-m0-ungegn-h0-hybrid-u-ca-gregory-nu-thai-x-FOO-BAR");
    }

    @Test
    void transformedExtlangMatchesItsCanonicalLanguage() {
        assertTagMatchPreservesRequestedLocale(
                "en-t-cmn-hans-cn-h0-hybrid",
                "en-t-zh-cmn-hans-cn-h0-hybrid");
    }

    @Test
    void transformedLegacyLanguageMatchesItsCanonicalLanguage() {
        assertTagMatchPreservesRequestedLocale(
                "en-t-he-il-h0-hybrid",
                "en-t-iw-il-h0-hybrid");
    }

    @Test
    void supportedTagValidationAndSemanticKeysUseTheSameNormalization() {
        for (List<String> equivalentTags : List.of(
                List.of("en-t-cmn-hans-cn-h0-hybrid", "en-t-zh-cmn-hans-cn-h0-hybrid"),
                List.of("en-t-he-il-h0-hybrid", "en-t-iw-il-h0-hybrid"))) {
            assertThat(equivalentTags)
                    .allMatch(CocoLanguageTagNormalizer::isValidSupportedLanguageTag);
            assertThat(CocoLanguageTagNormalizer.semanticKey(Locale.forLanguageTag(equivalentTags.get(0))))
                    .isEqualTo(CocoLanguageTagNormalizer.semanticKey(
                            Locale.forLanguageTag(equivalentTags.get(1))));
        }
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

    private static CocoI18nProperties propertiesWithSupportedLanguage(String languageTag) {
        CocoI18nProperties properties = new CocoI18nProperties();
        properties.setDefaultLocale(Locale.JAPAN);
        properties.setSupportedLanguages(List.of(languageTag));
        return properties;
    }

    private void assertTagMatchPreservesRequestedLocale(String supportedTag, String requestedTag) {
        CocoI18nProperties properties = propertiesWithSupportedLanguage(supportedTag);
        Locale requestedLocale = Locale.forLanguageTag(requestedTag);

        assertThat(this.policy.resolveLocale(requestedLocale, properties)).isSameAs(requestedLocale);
    }

    private void assertTagFallsBack(String supportedTag, String requestedTag) {
        CocoI18nProperties properties = propertiesWithSupportedLanguage(supportedTag);

        assertThat(this.policy.resolveLocale(Locale.forLanguageTag(requestedTag), properties))
                .isSameAs(Locale.JAPAN);
    }
}
