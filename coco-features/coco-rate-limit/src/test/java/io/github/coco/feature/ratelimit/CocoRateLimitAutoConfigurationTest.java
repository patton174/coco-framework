package io.github.coco.feature.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.feature.web.CocoWebAutoConfiguration;
import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import io.github.coco.feature.web.context.DefaultCocoWebRequestMatcher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 限流自动配置测试。
 */
class CocoRateLimitAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoRateLimitAutoConfiguration.class))
            .withUserConfiguration(WebMatcherConfiguration.class);

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoWebAutoConfiguration.class,
                    CocoRateLimitAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void isDisabledByDefault() {
        this.contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(CocoRateLimitStore.class);
            assertThat(context).doesNotHaveBean(CocoRateLimitKeyResolver.class);
        });
    }

    @Test
    void createsReplaceableInfrastructureOnlyWhenEnabled() {
        this.webContextRunner.withPropertyValues(
                "coco.rate-limit.enabled=true",
                "coco.rate-limit.routes[0].id=public-api",
                "coco.rate-limit.routes[0].matcher.path-patterns[0]=/api/**",
                "coco.rate-limit.routes[0].limit=5",
                "coco.rate-limit.routes[0].window-seconds=60")
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoRateLimitStore.class);
                    assertThat(context).hasSingleBean(CocoRateLimitKeyResolver.class);
                    assertThat(context).hasSingleBean(CocoRateLimitRouteMatcher.class);
                    assertThat(context).hasBean("cocoRateLimitMessageBundleRegistrar");
                    CocoRateLimitProperties properties = context.getBean(CocoRateLimitProperties.class);
                    assertThat(properties.getRoutes()).singleElement().satisfies(route -> {
                        assertThat(route.getId()).isEqualTo("public-api");
                        assertThat(route.getMatcher().getPathPatterns()).containsExactly("/api/**");
                        assertThat(route.getLimit()).isEqualTo(5);
                        assertThat(route.getWindowSeconds()).isEqualTo(60);
                    });
                    CocoRateLimitRoute boundRoute = properties.getRoutes().get(0);
                    assertThat(properties.getRoutes()).isSameAs(properties.getRoutes());
                    assertThat(boundRoute.getMatcher()).isSameAs(boundRoute.getMatcher());
                    boundRoute.getMatcher().setPathPatterns(java.util.Set.of("/bound/**"));
                    assertThat(properties.getRoutes().get(0).getMatcher().getPathPatterns())
                            .containsExactly("/bound/**");
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
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
                    assertThat(context).doesNotHaveBean(CocoRateLimitRouteMatcher.class);
                    assertThat(context).doesNotHaveBean("cocoRateLimitMvcConfigurer");
                    assertThat(context).doesNotHaveBean(WebMvcConfigurer.class);
                });
    }

    @Test
    void createsMvcConfigurerOnlyForAnEnabledServletApplication() {
        this.webContextRunner.withPropertyValues("coco.rate-limit.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoRateLimitRouteMatcher.class);
                    assertThat(context).hasSingleBean(CocoRateLimitRequestHandler.class);
                    assertThat(context).hasBean("cocoRateLimitMvcConfigurer");
                    assertThat(context).hasSingleBean(WebMvcConfigurer.class);
                });
    }

    @Test
    void remainsInactiveWhenTheServletClasspathIsUnavailable() {
        new ApplicationContextRunner().withClassLoader(new FilteredClassLoader("jakarta.servlet"))
                .withConfiguration(AutoConfigurations.of(CocoRateLimitAutoConfiguration.class))
                .withPropertyValues("coco.rate-limit.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void registersAutoConfigurationThroughTheStandardSpringBootImportsResource() throws IOException {
        String resourceName = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertThat(input).as("Spring Boot auto-configuration imports resource").isNotNull();
            String imports = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(imports.lines()
                    .map(String::trim)
                    .filter("io.github.coco.feature.ratelimit.CocoRateLimitAutoConfiguration"::equals)
                    .count()).isEqualTo(1);
        }
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
