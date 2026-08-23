package io.github.coco.feature.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.i18n.CocoMessage;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.i18n.CocoMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

class CocoIdempotencyAutoConfigurationTest {
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoIdempotencyAutoConfiguration.class))
            .withUserConfiguration(Prerequisites.class);

    @Test
    void enabledFeatureRegistersReplaceableInfrastructure() {
        this.contextRunner.withPropertyValues("coco.idempotency.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(CocoIdempotencyProperties.class);
            assertThat(context).hasSingleBean(CocoIdempotencyKeyResolver.class);
            assertThat(context).hasSingleBean(CocoIdempotencyStore.class);
            assertThat(context).hasSingleBean(CocoIdempotencyResponseWriter.class);
            assertThat(context).hasSingleBean(CocoMessageBundleRegistrar.class);
            assertThat(context).hasSingleBean(WebMvcConfigurer.class);
        });
    }

    @Test
    void disabledPropertyCreatesNoIdempotencyBeans() {
        this.contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(CocoIdempotencyProperties.class);
            assertThat(context).doesNotHaveBean(CocoIdempotencyStore.class);
            assertThat(context).doesNotHaveBean("cocoIdempotencyMvcConfigurer");
        });
    }

    @Test
    void disabledStandardFeatureCreatesNoIdempotencyBeans() {
        this.contextRunner.withPropertyValues("coco.idempotency.enabled=true", "coco.features.disabled=idempotency")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CocoIdempotencyStore.class);
                    assertThat(context).doesNotHaveBean("cocoIdempotencyMvcConfigurer");
                });
    }

    @Test
    void applicationCanReplaceStoreAndKeyResolver() {
        this.contextRunner.withPropertyValues("coco.idempotency.enabled=true")
                .withUserConfiguration(Overrides.class).run(context -> {
                    assertThat(context).hasSingleBean(CocoIdempotencyStore.class);
                    assertThat(context).hasSingleBean(CocoIdempotencyKeyResolver.class);
                    assertThat(context.getBean(CocoIdempotencyStore.class)).isInstanceOf(OverridesStore.class);
                    assertThat(context.getBean(CocoIdempotencyKeyResolver.class)).isSameAs(context.getBean("customResolver"));
                    assertThat(context.getBean(CocoIdempotencyResponseWriter.class)).isSameAs(context.getBean("customWriter"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class Prerequisites {
        @Bean("cocoIdempotencyClock") Clock clock() { return Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean CocoMessageService cocoMessageService() { return new TestMessageService(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class Overrides {
        @Bean CocoIdempotencyStore customStore() { return new OverridesStore(); }
        @Bean("customResolver") CocoIdempotencyKeyResolver customResolver() { return (request, method, intent) -> new CocoIdempotencyKey("custom", "POST", "/custom", "digest"); }
        @Bean("customWriter") CocoIdempotencyResponseWriter customWriter() { return (code, request, response) -> response.setStatus(418); }
    }

    static final class OverridesStore implements CocoIdempotencyStore {
        @Override public AcquireResult acquire(CocoIdempotencyLease lease) { return AcquireResult.ACQUIRED; }
        @Override public void release(CocoIdempotencyLease lease) { }
    }

    private static final class TestMessageService implements CocoMessageService {
        @Override public String getMessage(String code, Object... args) { return code; }
        @Override public String getMessage(String code, Locale locale, Object... args) { return code; }
        @Override public String getMessageOrDefault(String code, String defaultMessage, Object... args) { return defaultMessage; }
        @Override public String getMessageOrDefault(String code, String defaultMessage, Locale locale, Object... args) { return defaultMessage; }
        @Override public String resolve(CocoMessage message) { return message.code(); }
        @Override public String resolve(CocoMessage message, Locale locale) { return message.code(); }
    }
}
