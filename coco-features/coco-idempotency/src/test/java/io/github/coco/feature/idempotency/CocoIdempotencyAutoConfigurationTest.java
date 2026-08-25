package io.github.coco.feature.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.i18n.CocoMessage;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.i18n.CocoMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

class CocoIdempotencyAutoConfigurationTest {
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoIdempotencyAutoConfiguration.class))
            .withUserConfiguration(Prerequisites.class);
    private final ApplicationContextRunner redisContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoIdempotencyRedisAutoConfiguration.class))
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

    @Test
    void redisSelectionCreatesRedisStoreAndCustomStoreStillWins() {
        this.contextRunner
                .withConfiguration(AutoConfigurations.of(CocoIdempotencyRedisAutoConfiguration.class))
                .withPropertyValues("coco.idempotency.enabled=true", "coco.idempotency.store-type=redis")
                .withBean(StringRedisTemplate.class, () -> new StringRedisTemplate(new LettuceConnectionFactory()))
                .run(context -> assertThat(context).hasSingleBean(RedisCocoIdempotencyStore.class));
        CocoIdempotencyStore customStore = new OverridesStore();
        this.contextRunner
                .withConfiguration(AutoConfigurations.of(CocoIdempotencyRedisAutoConfiguration.class))
                .withPropertyValues("coco.idempotency.enabled=true", "coco.idempotency.store-type=redis",
                        "coco.idempotency.redis.template-bean-name=missingTemplate")
                .withBean(CocoIdempotencyStore.class, () -> customStore)
                .run(context -> assertThat(context.getBean(CocoIdempotencyStore.class)).isSameAs(customStore));
    }

    @Test
    void redisStoreUsesSingleOrPrimaryTemplateAndBacksOffOtherwise() {
        this.redisContextRunner.withPropertyValues("coco.idempotency.enabled=true", "coco.idempotency.store-type=redis")
                .withBean(StringRedisTemplate.class, () -> new StringRedisTemplate(new LettuceConnectionFactory()))
                .run(context -> assertThat(context).hasSingleBean(RedisCocoIdempotencyStore.class));
        this.redisContextRunner.withPropertyValues("coco.idempotency.enabled=true", "coco.idempotency.store-type=redis")
                .withUserConfiguration(PrimaryRedisTemplates.class).run(context -> {
                    CocoIdempotencyKey key = CocoIdempotencyKey.fromRawKey("orders", "POST", "create", "key");
                    context.getBean(CocoIdempotencyStore.class).acquire(new CocoIdempotencyLease(key, "owner", Instant.now().plusSeconds(60)));
                    assertThat(context.getBean("primaryTemplate", TrackingRedisTemplate.class).calls()).isEqualTo(1);
                    assertThat(context.getBean("secondaryTemplate", TrackingRedisTemplate.class).calls()).isZero();
                });
        this.redisContextRunner.withPropertyValues("coco.idempotency.enabled=true", "coco.idempotency.store-type=redis",
                        "coco.idempotency.redis.template-bean-name=  secondaryTemplate  ")
                .withUserConfiguration(PrimaryRedisTemplates.class).run(context -> {
                    CocoIdempotencyKey key = CocoIdempotencyKey.fromRawKey("orders", "POST", "create", "key");
                    context.getBean(CocoIdempotencyStore.class).acquire(new CocoIdempotencyLease(key, "owner", Instant.now().plusSeconds(60)));
                    assertThat(context.getBean("primaryTemplate", TrackingRedisTemplate.class).calls()).isZero();
                    assertThat(context.getBean("secondaryTemplate", TrackingRedisTemplate.class).calls()).isEqualTo(1);
                });
        this.redisContextRunner.withPropertyValues("coco.idempotency.enabled=true", "coco.idempotency.store-type=redis")
                .withUserConfiguration(NonPrimaryRedisTemplates.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("candidates=[firstTemplate, secondTemplate]")
                            .hasStackTraceContaining("coco.idempotency.redis.template-bean-name");
                });
        this.redisContextRunner.withPropertyValues("coco.idempotency.enabled=true", "coco.idempotency.store-type=redis",
                        "coco.idempotency.redis.template-bean-name=missingTemplate")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("coco.idempotency.redis.template-bean-name")
                            .hasStackTraceContaining("missingTemplate");
                });
        this.redisContextRunner.withPropertyValues("coco.idempotency.enabled=true", "coco.idempotency.store-type=redis",
                        "coco.idempotency.redis.template-bean-name=notATemplate")
                .withBean("notATemplate", String.class, () -> "wrong type")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("coco.idempotency.redis.template-bean-name")
                            .hasStackTraceContaining("notATemplate");
                });
        this.redisContextRunner.withClassLoader(new FilteredClassLoader(StringRedisTemplate.class))
                .withPropertyValues("coco.idempotency.enabled=true", "coco.idempotency.store-type=redis")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CocoIdempotencyStore.class);
                });
        this.contextRunner.withConfiguration(AutoConfigurations.of(CocoIdempotencyRedisAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader(StringRedisTemplate.class))
                .withPropertyValues("coco.idempotency.enabled=true", "coco.idempotency.store-type=redis")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class Prerequisites {
        @Bean("cocoIdempotencyClock") Clock clock() { return Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean CocoMessageService cocoMessageService() { return new TestMessageService(); }
        @Bean CocoIdempotencyResponseWriter cocoIdempotencyResponseWriter() {
            return (code, request, response) -> response.setStatus(500);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PrimaryRedisTemplates {
        @Bean @Primary TrackingRedisTemplate primaryTemplate() { return new TrackingRedisTemplate(); }
        @Bean TrackingRedisTemplate secondaryTemplate() { return new TrackingRedisTemplate(); }
    }
    @Configuration(proxyBeanMethods = false)
    static class NonPrimaryRedisTemplates {
        @Bean TrackingRedisTemplate firstTemplate() { return new TrackingRedisTemplate(); }
        @Bean TrackingRedisTemplate secondTemplate() { return new TrackingRedisTemplate(); }
    }
    static final class TrackingRedisTemplate extends StringRedisTemplate {
        private final AtomicInteger calls = new AtomicInteger();
        TrackingRedisTemplate() { super(new LettuceConnectionFactory()); }
        @Override @SuppressWarnings("unchecked") public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            this.calls.incrementAndGet(); return (T) Long.valueOf(1L);
        }
        int calls() { return this.calls.get(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class Overrides {
        @Bean CocoIdempotencyStore customStore() { return new OverridesStore(); }
        @Bean("customResolver") CocoIdempotencyKeyResolver customResolver() {
            return (request, method, intent) -> CocoIdempotencyKey.fromRawKey("custom", "POST", "custom", "key");
        }
        @Bean("customWriter") @Primary CocoIdempotencyResponseWriter customWriter() {
            return (code, request, response) -> response.setStatus(418);
        }
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
