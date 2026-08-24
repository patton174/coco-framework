package io.github.coco.feature.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.i18n.CocoMessage;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.i18n.CocoMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

class CocoRateLimitAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoRateLimitAutoConfiguration.class))
            .withUserConfiguration(RateLimitPrerequisites.class);

    @Test
    void legacyDirectModuleModeRegistersCoreBeansAndProcessesConfiguredRoute() {
        this.contextRunner
                .withPropertyValues(
                        "coco.rate-limit.enabled=true",
                        "coco.rate-limit.routes[0].id=orders",
                        "coco.rate-limit.routes[0].limit=2",
                        "coco.rate-limit.routes[0].window-seconds=60",
                        "coco.rate-limit.routes[0].matcher.methods[0]=GET",
                        "coco.rate-limit.routes[0].matcher.path-patterns[0]=/orders/**")
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoRateLimitProperties.class);
                    assertThat(context).hasSingleBean(CocoRateLimitKeyResolver.class);
                    assertThat(context).hasSingleBean(CocoRateLimitStore.class);
                    assertThat(context).hasSingleBean(CocoRateLimitRouteMatcher.class);
                    assertThat(context).hasSingleBean(CocoRateLimitResponseWriter.class);
                    assertThat(context).hasSingleBean(CocoRateLimitRequestHandler.class);
                    assertThat(context).hasSingleBean(CocoMessageBundleRegistrar.class);
                    assertThat(context).hasSingleBean(WebMvcConfigurer.class);
                    assertThat(context).hasBean("cocoRateLimitFilterRegistration");

                    FilterRegistrationBean<?> registration = context.getBean("cocoRateLimitFilterRegistration",
                            FilterRegistrationBean.class);
                    assertThat(registration.getFilter()).isInstanceOf(CocoRateLimitFilter.class);
                    CocoRateLimitFilter filter = (CocoRateLimitFilter) registration.getFilter();
                    MockHttpServletRequest firstRequest = request();
                    MockHttpServletResponse firstResponse = new MockHttpServletResponse();
                    MockFilterChain firstChain = new MockFilterChain();

                    filter.doFilter(firstRequest, firstResponse, firstChain);

                    assertThat(firstChain.getRequest()).isSameAs(firstRequest);
                    assertThat(firstRequest.getAttribute(CocoRateLimitFilter.APPLIED_ROUTE_ATTRIBUTE)).isEqualTo("orders");
                    assertThat(firstResponse.getHeader("RateLimit-Limit")).isEqualTo("2");
                    assertThat(firstResponse.getHeader("RateLimit-Remaining")).isEqualTo("1");
                    MockHttpServletRequest secondRequest = request();
                    MockHttpServletResponse secondResponse = new MockHttpServletResponse();
                    MockFilterChain secondChain = new MockFilterChain();

                    filter.doFilter(secondRequest, secondResponse, secondChain);

                    assertThat(secondChain.getRequest()).isSameAs(secondRequest);
                    assertThat(secondRequest.getAttribute(CocoRateLimitFilter.APPLIED_ROUTE_ATTRIBUTE)).isEqualTo("orders");
                    assertThat(secondResponse.getHeader("RateLimit-Remaining")).isEqualTo("0");
                    AtomicBoolean rejectedChainCalled = new AtomicBoolean();
                    MockHttpServletResponse rejectedResponse = new MockHttpServletResponse();

                    filter.doFilter(request(), rejectedResponse,
                            (servletRequest, servletResponse) -> rejectedChainCalled.set(true));

                    assertThat(rejectedChainCalled).isFalse();
                    assertThat(rejectedResponse.getStatus()).isEqualTo(429);
                    assertThat(rejectedResponse.getContentAsString()).contains("\"code\":42900");
                });
    }

    @Test
    void enabledStandardFeatureRegistersRateLimitInfrastructure() {
        this.contextRunner
                .withPropertyValues("coco.features.enabled=rate-limit", "coco.rate-limit.enabled=true")
                .run(this::assertRateLimitInfrastructure);
    }

    @Test
    void disabledPropertyDoesNotRegisterRateLimitInfrastructure() {
        this.contextRunner.run(context -> {
            assertRateLimitInfrastructureIsAbsent(context);
        });
    }

    @Test
    void disabledStandardFeaturePreventsRegistrationWhenPropertyIsEnabled() {
        this.contextRunner
                .withPropertyValues("coco.features.disabled=rate-limit", "coco.rate-limit.enabled=true")
                .run(this::assertRateLimitInfrastructureIsAbsent);
    }

    @Test
    void redisSelectionRequiresTemplateAndBacksOffForCustomStore() {
        this.contextRunner
                .withConfiguration(AutoConfigurations.of(CocoRateLimitRedisAutoConfiguration.class))
                .withPropertyValues("coco.rate-limit.enabled=true", "coco.rate-limit.store-type=redis")
                .withBean(StringRedisTemplate.class, () -> new StringRedisTemplate(new LettuceConnectionFactory()))
                .run(context -> assertThat(context).hasSingleBean(RedisCocoRateLimitStore.class));
        CocoRateLimitStore customStore = permit -> new CocoRateLimitDecision(false, permit.limit(), 0,
                permit.resetAt(), true);
        this.contextRunner
                .withConfiguration(AutoConfigurations.of(CocoRateLimitRedisAutoConfiguration.class))
                .withPropertyValues("coco.rate-limit.enabled=true", "coco.rate-limit.store-type=redis")
                .withBean(CocoRateLimitStore.class, () -> customStore)
                .run(context -> assertThat(context.getBean(CocoRateLimitStore.class)).isSameAs(customStore));
    }

    private void assertRateLimitInfrastructure(org.springframework.boot.test.context.assertj.AssertableWebApplicationContext context) {
        assertThat(context).hasSingleBean(CocoRateLimitProperties.class);
        assertThat(context).hasSingleBean(CocoRateLimitKeyResolver.class);
        assertThat(context).hasSingleBean(CocoRateLimitStore.class);
        assertThat(context).hasSingleBean(CocoRateLimitRouteMatcher.class);
        assertThat(context).hasSingleBean(CocoRateLimitResponseWriter.class);
        assertThat(context).hasSingleBean(CocoRateLimitRequestHandler.class);
        assertThat(context).hasBean("cocoRateLimitFilterRegistration");
    }

    private void assertRateLimitInfrastructureIsAbsent(
            org.springframework.boot.test.context.assertj.AssertableWebApplicationContext context) {
        assertThat(context).doesNotHaveBean(CocoRateLimitProperties.class);
        assertThat(context).doesNotHaveBean(CocoRateLimitKeyResolver.class);
        assertThat(context).doesNotHaveBean(CocoRateLimitStore.class);
        assertThat(context).doesNotHaveBean(CocoRateLimitRouteMatcher.class);
        assertThat(context).doesNotHaveBean(CocoRateLimitResponseWriter.class);
        assertThat(context).doesNotHaveBean(CocoRateLimitRequestHandler.class);
        assertThat(context).doesNotHaveBean("cocoRateLimitFilterRegistration");
        assertThat(context).doesNotHaveBean("cocoRateLimitMvcConfigurer");
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders/42");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    @Configuration(proxyBeanMethods = false)
    static class RateLimitPrerequisites {

        @Bean("cocoRateLimitClock")
        Clock cocoRateLimitClock() {
            return Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        CocoMessageService cocoMessageService() {
            return new TestMessageService();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private static final class TestMessageService implements CocoMessageService {

        @Override
        public String getMessage(String code, Object... args) {
            return code;
        }

        @Override
        public String getMessage(String code, Locale locale, Object... args) {
            return code;
        }

        @Override
        public String getMessageOrDefault(String code, String defaultMessage, Object... args) {
            return defaultMessage;
        }

        @Override
        public String getMessageOrDefault(String code, String defaultMessage, Locale locale, Object... args) {
            return defaultMessage;
        }

        @Override
        public String resolve(CocoMessage message) {
            return message.code();
        }

        @Override
        public String resolve(CocoMessage message, Locale locale) {
            return message.code();
        }
    }
}
