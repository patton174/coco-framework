package io.github.coco.feature.concurrencylimit;

import java.util.Map;

import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class CocoConcurrencyLimitAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoConcurrencyLimitAutoConfiguration.class))
            .withUserConfiguration(WebPrerequisites.class);

    @Test
    void remainsDisabledWithoutExplicitProperty() {
        this.contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(CocoConcurrencyLimitStore.class);
            assertThat(context).doesNotHaveBean("cocoConcurrencyLimitFilterRegistration");
        });
    }

    @Test
    void enablesServletInfrastructureAndDefaultsToAsyncTracking() {
        this.contextRunner.withPropertyValues("coco.concurrency-limit.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoConcurrencyLimitStore.class);
                    assertThat(context).hasSingleBean(CocoConcurrencyLimitKeyResolver.class);
                    assertThat(context).hasSingleBean(CocoConcurrencyLimitRouteMatcher.class);
                    assertThat(context).hasSingleBean(CocoConcurrencyLimitRequestHandler.class);
                    FilterRegistrationBean<?> registration = context.getBean(
                            "cocoConcurrencyLimitFilterRegistration", FilterRegistrationBean.class);
                    assertThat(registration.isAsyncSupported()).isTrue();
                });
    }

    @Test
    void webFeatureDisabledPreventsConcurrencyLimitInfrastructure() {
        this.contextRunner.withPropertyValues(
                "coco.concurrency-limit.enabled=true",
                "coco.features.disabled=web")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CocoConcurrencyLimitStore.class);
                    assertThat(context).doesNotHaveBean(CocoConcurrencyLimitKeyResolver.class);
                    assertThat(context).doesNotHaveBean(CocoConcurrencyLimitRouteMatcher.class);
                    assertThat(context).doesNotHaveBean(CocoConcurrencyLimitRequestHandler.class);
                    assertThat(context).doesNotHaveBean("cocoConcurrencyLimitFilterRegistration");
                    assertThat(context).doesNotHaveBean("cocoConcurrencyLimitMvcConfigurer");
                });
    }

    @Test
    void rejectPolicyDisablesFilterAsyncSupport() {
        this.contextRunner.withPropertyValues(
                "coco.concurrency-limit.enabled=true",
                "coco.concurrency-limit.async-policy=reject")
                .run(context -> {
                    FilterRegistrationBean<?> registration = context.getBean(
                            "cocoConcurrencyLimitFilterRegistration", FilterRegistrationBean.class);
                    assertThat(registration.isAsyncSupported()).isFalse();
                });
    }

    @Test
    void customStoreAndResolverReplaceDefaults() {
        CocoConcurrencyLimitStore customStore = new RejectingStore();
        CocoConcurrencyLimitKeyResolver customResolver = (snapshot, route) -> "custom";

        this.contextRunner.withPropertyValues("coco.concurrency-limit.enabled=true")
                .withBean(CocoConcurrencyLimitStore.class, () -> customStore)
                .withBean(CocoConcurrencyLimitKeyResolver.class, () -> customResolver)
                .run(context -> {
                    assertThat(context.getBean(CocoConcurrencyLimitStore.class)).isSameAs(customStore);
                    assertThat(context.getBean(CocoConcurrencyLimitKeyResolver.class)).isSameAs(customResolver);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class WebPrerequisites {

        @Bean
        CocoWebRequestMatcher cocoWebRequestMatcher() {
            return (request, rules) -> false;
        }

        @Bean
        CocoWebRequestContextResolver cocoWebRequestContextResolver() {
            return (traceId, request) -> new CocoWebRequestSnapshot(traceId, request.getMethod(),
                    request.getRequestURI(), null, request.getRemoteAddr(), null, null, null, null, null, null,
                    Map.of(), Map.of());
        }

        @Bean
        CocoConcurrencyLimitResponseWriter cocoConcurrencyLimitResponseWriter() {
            return (errorCode, request, response) -> response.setStatus(429);
        }
    }

    private static final class RejectingStore implements CocoConcurrencyLimitStore {

        @Override
        public CocoConcurrencyLimitAcquisition acquire(CocoConcurrencyLimitRequest request) {
            CocoConcurrencyLimitConstraint constraint = request.constraints().get(0);
            return CocoConcurrencyLimitAcquisition.rejected(java.util.List.of(), constraint.dimension(),
                    CocoConcurrencyLimitRejectionReason.UNAVAILABLE);
        }

        @Override
        public void release(CocoConcurrencyLimitPermit permit) {
        }
    }
}
