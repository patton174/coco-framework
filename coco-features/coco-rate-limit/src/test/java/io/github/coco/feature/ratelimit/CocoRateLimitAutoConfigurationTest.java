package io.github.coco.feature.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import io.github.coco.feature.web.context.DefaultCocoWebRequestMatcher;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 限流自动配置测试。
 */
class CocoRateLimitAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoRateLimitAutoConfiguration.class))
            .withUserConfiguration(WebMatcherConfiguration.class);

    @Test
    void isDisabledByDefault() {
        this.contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(CocoRateLimitStore.class);
            assertThat(context).doesNotHaveBean(CocoRateLimitKeyResolver.class);
        });
    }

    @Test
    void createsReplaceableInfrastructureOnlyWhenEnabled() {
        this.contextRunner.withPropertyValues(
                "coco.rate-limit.enabled=true",
                "coco.rate-limit.routes[0].id=public-api",
                "coco.rate-limit.routes[0].matcher.path-patterns[0]=/api/**",
                "coco.rate-limit.routes[0].limit=5",
                "coco.rate-limit.routes[0].window-seconds=60")
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoRateLimitStore.class);
                    assertThat(context).hasSingleBean(CocoRateLimitKeyResolver.class);
                    assertThat(context).hasSingleBean(CocoRateLimitRouteMatcher.class);
                    assertThat(context).hasSingleBean(CocoMessageBundleRegistrar.class);
                });
    }

    @Test
    void preservesApplicationProvidedStoreAndKeyResolver() {
        CocoRateLimitStore store = permit -> new CocoRateLimitDecision(true, permit.limit(), permit.limit() - 1,
                permit.resetAt(), false);
        CocoRateLimitKeyResolver keyResolver = (request, route) -> new CocoRateLimitKey(route.getId(), "trusted-app");

        this.contextRunner.withPropertyValues("coco.rate-limit.enabled=true")
                .withBean(CocoRateLimitStore.class, () -> store)
                .withBean(CocoRateLimitKeyResolver.class, () -> keyResolver)
                .run(context -> {
                    assertThat(context.getBean(CocoRateLimitStore.class)).isSameAs(store);
                    assertThat(context.getBean(CocoRateLimitKeyResolver.class)).isSameAs(keyResolver);
                });
    }

    @Test
    void doesNotCreateServletFilterForANonWebContext() {
        this.contextRunner.withPropertyValues("coco.rate-limit.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void remainsInactiveWhenTheServletClasspathIsUnavailable() {
        new ApplicationContextRunner().withClassLoader(new FilteredClassLoader("jakarta.servlet"))
                .withConfiguration(AutoConfigurations.of(CocoRateLimitAutoConfiguration.class))
                .withPropertyValues("coco.rate-limit.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void backsOffFromAnApplicationProvidedRateLimitClock() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC);
        this.contextRunner.withPropertyValues("coco.rate-limit.enabled=true")
                .withBean("cocoRateLimitClock", Clock.class, () -> clock)
                .run(context -> assertThat(context.getBean("cocoRateLimitClock")).isSameAs(clock));
    }

    @Configuration(proxyBeanMethods = false)
    static class WebMatcherConfiguration {

        @Bean
        CocoWebRequestMatcher cocoWebRequestMatcher() {
            return new DefaultCocoWebRequestMatcher();
        }
    }
}
