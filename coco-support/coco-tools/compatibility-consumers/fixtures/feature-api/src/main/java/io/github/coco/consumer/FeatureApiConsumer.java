package io.github.coco.consumer;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import io.github.coco.CocoCommonProperties;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.feature.audit.CocoAuditFeature;
import io.github.coco.feature.datapermission.CocoDataPermissionFeature;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusFeature;
import io.github.coco.feature.openapi.CocoOpenApiFeature;
import io.github.coco.feature.security.CocoSecurityFeature;
import io.github.coco.feature.tenant.CocoTenantFeature;
import io.github.coco.feature.web.CocoWebFeature;
import io.github.coco.feature.web.body.CocoCachedRequestBody;
import io.github.coco.feature.web.context.CocoIpAddressSupport;
import io.github.coco.feature.web.i18n.CocoWebLocaleResolver;
import io.github.coco.i18n.CocoI18nProperties;
import io.github.coco.i18n.CocoLocaleResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class FeatureApiConsumer {

    private static final List<Class<?>> FEATURE_TYPES = List.of(
            CocoWebFeature.class,
            CocoMybatisPlusFeature.class,
            CocoTenantFeature.class,
            CocoDataPermissionFeature.class,
            CocoAuditFeature.class,
            CocoSecurityFeature.class,
            CocoOpenApiFeature.class);

    private FeatureApiConsumer() {
    }

    public static void main(String[] args) {
        if (FEATURE_TYPES.size() != 7) {
            throw new IllegalStateException("Expected seven Coco feature APIs.");
        }
        if (CocoIpAddressSupport.parseIpAddress("invalid-address") != null) {
            throw new IllegalStateException("Published CocoIpAddressSupport null sentinel changed.");
        }
        verifyMutableBasenameContract();
        verifyCommonLocaleFactoryAbi();
        verifyLocalePassThroughContract();
        verifyCachedRequestBodyContract();
        System.out.println(FEATURE_TYPES.stream()
                .map(Class::getName)
                .collect(Collectors.joining(",")));
    }

    private static void verifyMutableBasenameContract() {
        CocoI18nProperties properties = new CocoI18nProperties();
        properties.getBasename().add("consumer-messages");
        if (!properties.getBasename().contains("consumer-messages")) {
            throw new IllegalStateException("getBasename().add(...) did not update the backing list.");
        }
        if (!properties.getBasename().remove("consumer-messages")
                || properties.getBasename().contains("consumer-messages")) {
            throw new IllegalStateException("getBasename().remove(...) did not update the backing list.");
        }
        System.out.println("COCO_I18N_BASENAME_LIVE_LIST_OK");
    }

    private static void verifyCommonLocaleFactoryAbi() {
        CocoLocaleResolver resolver = new CocoCommonAutoConfiguration()
                .cocoLocaleResolver(new CocoCommonProperties());
        if (resolver == null) {
            throw new IllegalStateException("One-argument common locale resolver factory returned null.");
        }
        System.out.println("COCO_COMMON_LOCALE_FACTORY_ABI_OK");
    }

    private static void verifyLocalePassThroughContract() {
        CocoCommonProperties properties = new CocoCommonProperties();
        CocoLocaleResolver defaultResolver = new CocoCommonAutoConfiguration().cocoLocaleResolver(properties);
        CocoLocaleResolver webResolver = new CocoWebLocaleResolver(properties.getI18n());
        List<Locale> locales = List.of(
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
        int defaultCount = 0;
        int webCount = 0;
        for (Locale locale : locales) {
            LocaleContextHolder.setLocale(locale);
            try {
                requireSame(locale, defaultResolver.resolveLocale(), "default resolver");
                defaultCount++;
            } finally {
                LocaleContextHolder.resetLocaleContext();
            }
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requestFor(locale)));
            try {
                requireSame(locale, webResolver.resolveLocale(), "web resolver");
                webCount++;
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        }
        int missingCount = 0;
        requireSame(properties.getI18n().getDefaultLocale(), defaultResolver.resolveLocale(), "default missing locale");
        missingCount++;
        requireSame(properties.getI18n().getDefaultLocale(), webResolver.resolveLocale(), "web missing locale");
        missingCount++;
        if (defaultCount != 10 || webCount != 10 || missingCount != 2) {
            throw new IllegalStateException("Locale contract counters changed.");
        }
        System.out.println("COCO_LOCALE_2_0_1_PASS_THROUGH_OK default=10 web=10 missing=2");
    }

    private static HttpServletRequest requestFor(Locale locale) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                FeatureApiConsumer.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class },
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getHeader" -> "Accept-Language".equals(arguments[0]) ? locale.toLanguageTag() : null;
                    case "getLocale" -> locale;
                    case "toString" -> "LocaleRequest[" + locale + "]";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return 0;
    }

    private static void requireSame(Locale expected, Locale actual, String description) {
        if (expected != actual) {
            throw new IllegalStateException(description + " did not preserve the Locale instance.");
        }
    }

    private static void verifyCachedRequestBodyContract() {
        byte[] source = { 1, 2, 3 };
        CocoCachedRequestBody cached = new CocoCachedRequestBody(source, null, -1L, true);
        source[0] = 9;
        if (!cached.cached() || cached.length() != 3L || cached.sha256() == null || cached.content()[0] != 1) {
            throw new IllegalStateException("Published CocoCachedRequestBody cached contract changed.");
        }
        byte[] exposed = cached.content();
        exposed[0] = 9;
        if (cached.content()[0] != 1) {
            throw new IllegalStateException("Published CocoCachedRequestBody defensive copy contract changed.");
        }

        CocoCachedRequestBody uncached = new CocoCachedRequestBody(new byte[] { 1 }, "ignored",
                Long.MAX_VALUE, false);
        if (uncached.cached() || uncached.length() != 0L || uncached.sha256() != null) {
            throw new IllegalStateException("Published CocoCachedRequestBody uncached contract changed.");
        }

        CocoCachedRequestBody nullContent = new CocoCachedRequestBody(null, null, Long.MAX_VALUE, true);
        if (nullContent.length() != 0L || nullContent.content().length != 0 || nullContent.sha256() == null) {
            throw new IllegalStateException("Published CocoCachedRequestBody null-content contract changed.");
        }
    }
}
