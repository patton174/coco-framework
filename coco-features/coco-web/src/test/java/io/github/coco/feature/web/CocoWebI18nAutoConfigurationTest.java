package io.github.coco.feature.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Locale;

import io.github.coco.CocoCommonProperties;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.i18n.CocoLocaleFallbackPolicy;
import io.github.coco.i18n.CocoLocaleResolver;
import io.github.coco.feature.web.i18n.CocoWebLocaleResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class CocoWebI18nAutoConfigurationTest {

    private final WebApplicationContextRunner isolatedContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoWebI18nAutoConfiguration.class));

    private final WebApplicationContextRunner commonWebContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoWebAutoConfiguration.class));

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void keepsPublishedOneArgumentFactoryMethodBinaryLinkable() throws Throwable {
        MethodHandle factory = MethodHandles.publicLookup().findVirtual(
                CocoWebI18nAutoConfiguration.class,
                "cocoWebLocaleResolver",
                MethodType.methodType(CocoLocaleResolver.class, CocoCommonProperties.class));

        CocoLocaleResolver resolver = (CocoLocaleResolver) factory.invokeExact(
                new CocoWebI18nAutoConfiguration(), new CocoCommonProperties());

        assertThat(resolver).isInstanceOf(CocoWebLocaleResolver.class);
    }

    @Test
    void startsWhenWebI18nIsImportedWithoutCommonAutoConfiguration() {
        this.isolatedContextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(CocoLocaleResolver.class);
            assertThat(context).doesNotHaveBean(CocoCommonProperties.class);
            assertThat(context).doesNotHaveBean(CocoLocaleFallbackPolicy.class);
            assertThat(context.getBean(CocoLocaleResolver.class).resolveLocale())
                    .isEqualTo(Locale.SIMPLIFIED_CHINESE);
        });
    }

    @Test
    void preservesRequestLocalesInIsolatedWebImportWhenFilteringIsDisabled() {
        this.isolatedContextRunner.run(context -> {
            CocoLocaleResolver resolver = context.getBean(CocoLocaleResolver.class);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
            request.addHeader("Accept-Language", "zh-CN");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            assertThat(resolver.resolveLocale()).isEqualTo(Locale.SIMPLIFIED_CHINESE);

            request.removeHeader("Accept-Language");
            request.addHeader("Accept-Language", "zh");
            assertThat(resolver.resolveLocale()).isEqualTo(Locale.CHINESE);

            request.removeHeader("Accept-Language");
            request.addHeader("Accept-Language", "zh-TW");
            assertThat(resolver.resolveLocale()).isEqualTo(Locale.TAIWAN);

            request.removeHeader("Accept-Language");
            request.addHeader("Accept-Language", "fr-CA");
            assertThat(resolver.resolveLocale()).isEqualTo(Locale.CANADA_FRENCH);
        });
    }

    @Test
    void preservesAllRequestLocaleTagsWhenFilteringIsDisabled() {
        this.isolatedContextRunner.run(context -> {
            CocoLocaleResolver resolver = context.getBean(CocoLocaleResolver.class);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            for (String languageTag : List.of("zh-TW", "zh-Hant-TW", "zh-HK", "zh-CN", "zh-Hans",
                    "zh", "en-US", "ja-JP", "fr-FR", "zz-ZZ")) {
                request.removeHeader("Accept-Language");
                request.addHeader("Accept-Language", languageTag);
                assertThat(resolver.resolveLocale().toLanguageTag()).isEqualTo(languageTag);
            }
        });
    }

    @Test
    void treatsLocaleRootAsAPresentRequestLocale() {
        this.isolatedContextRunner.run(context -> {
            CocoLocaleResolver resolver = context.getBean(CocoLocaleResolver.class);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
            request.addHeader("Accept-Language", "und");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            assertThat(resolver.resolveLocale()).isSameAs(request.getLocale()).isEqualTo(Locale.ROOT);
        });
    }

    @Test
    void createsOneResolverAndOneFallbackPolicyForCommonAndWeb() {
        this.commonWebContextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(CocoLocaleResolver.class)).hasSize(1);
            assertThat(context.getBeansOfType(CocoLocaleFallbackPolicy.class)).hasSize(1);
            assertThat(context.getBean("cocoWebLocaleResolver"))
                    .isSameAs(context.getBean(CocoLocaleResolver.class));
        });
    }

    @Test
    void usesCustomFallbackPolicyWithoutCreatingADuplicatePolicy() {
        CocoLocaleFallbackPolicy customPolicy = (locale, properties) -> Locale.US;
        this.commonWebContextRunner
                .withBean(CocoLocaleFallbackPolicy.class, () -> customPolicy)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(CocoLocaleFallbackPolicy.class).values())
                            .containsExactly(customPolicy);

                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
                    request.addHeader("Accept-Language", "fr-CA");
                    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

                    assertThat(context.getBean(CocoLocaleResolver.class).resolveLocale())
                            .isEqualTo(Locale.US);
                });
    }

    @Test
    void backsOffWhenApplicationProvidesLocaleResolver() {
        CocoLocaleResolver customResolver = () -> Locale.CANADA_FRENCH;
        this.commonWebContextRunner
                .withBean(CocoLocaleResolver.class, () -> customResolver)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(CocoLocaleResolver.class).values())
                            .containsExactly(customResolver);
                    assertThat(context.getBeansOfType(CocoLocaleFallbackPolicy.class)).hasSize(1);
                });
    }

}
