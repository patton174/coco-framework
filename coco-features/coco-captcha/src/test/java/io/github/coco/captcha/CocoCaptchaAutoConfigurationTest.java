package io.github.coco.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class CocoCaptchaAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoCaptchaRedisAutoConfiguration.class,
                    CocoCaptchaRedisMissingDependencyAutoConfiguration.class, CocoCaptchaAutoConfiguration.class));

    @Test
    void disabledByDefaultRegistersNothing() {
        this.runner.run(context -> assertThat(context).doesNotHaveBean(CocoCaptchaService.class));
    }

    @Test
    void enabledRegistersServiceWithAllReferenceGenerators() {
        this.runner.withPropertyValues("coco.captcha.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(CocoCaptchaService.class);
            assertThat(context).hasSingleBean(CocoCaptchaStore.class);
            CocoCaptchaService service = context.getBean(CocoCaptchaService.class);
            assertThat(service.supports(CocoCaptchaType.IMAGE)).isTrue();
            assertThat(service.supports(CocoCaptchaType.SLIDER)).isTrue();
            assertThat(service.supports(CocoCaptchaType.SMS_CODE)).isTrue();
        });
    }

    @Test
    void disablingATypeLeavesItUnsupported() {
        this.runner.withPropertyValues("coco.captcha.enabled=true",
                "coco.captcha.slider-enabled=false", "coco.captcha.sms-code-enabled=false")
                .run(context -> {
                    CocoCaptchaService service = context.getBean(CocoCaptchaService.class);
                    assertThat(service.supports(CocoCaptchaType.IMAGE)).isTrue();
                    assertThat(service.supports(CocoCaptchaType.SLIDER)).isFalse();
                    assertThat(service.supports(CocoCaptchaType.SMS_CODE)).isFalse();
                });
    }

    @Test
    void businessGeneratorWinsOverReferenceForItsType() {
        this.runner.withPropertyValues("coco.captcha.enabled=true")
                .withBean("businessImage", CocoCaptchaGenerator.class, BusinessImageGenerator::new)
                .run(context -> {
                    CocoCaptchaService service = context.getBean(CocoCaptchaService.class);
                    CocoCaptcha.ClientView view = service.generate(CocoCaptchaType.IMAGE);
                    // The business generator's fixed answer proves it, not the reference image gen, handled it.
                    assertThat(service.verify(CocoCaptchaType.IMAGE, view.captchaId(), "business-answer")).isTrue();
                });
    }

    @Test
    void defaultStoreTypeIsInMemory() {
        this.runner.withPropertyValues("coco.captcha.enabled=true")
                .run(context -> assertThat(context.getBean(CocoCaptchaStore.class))
                        .isInstanceOf(InMemoryCocoCaptchaStore.class));
    }

    @Test
    void redisStoreTypeRegistersTheSharedStore() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        this.runner.withPropertyValues("coco.captcha.enabled=true", "coco.captcha.store-type=redis")
                .withBean(StringRedisTemplate.class, () -> new StringRedisTemplate(connectionFactory))
                .run(context -> assertThat(context.getBean(CocoCaptchaStore.class))
                        .isInstanceOf(RedisCocoCaptchaStore.class));
    }

    @Test
    void redisStoreTypeFailsClosedWhenRedisIsNotOnTheClasspath() {
        // Falling back to the in-memory store here would break verification behind a load
        // balancer while looking healthy at startup, so this must fail instead.
        this.runner.withPropertyValues("coco.captcha.enabled=true", "coco.captcha.store-type=redis")
                .withClassLoader(new FilteredClassLoader(StringRedisTemplate.class))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void ambiguousTemplateRequiresAnExplicitBeanName() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        this.runner.withPropertyValues("coco.captcha.enabled=true", "coco.captcha.store-type=redis")
                .withBean("templateA", StringRedisTemplate.class, () -> new StringRedisTemplate(connectionFactory))
                .withBean("templateB", StringRedisTemplate.class, () -> new StringRedisTemplate(connectionFactory))
                .run(context -> assertThat(context).hasFailed());
        this.runner.withPropertyValues("coco.captcha.enabled=true", "coco.captcha.store-type=redis",
                        "coco.captcha.redis.template-bean-name=templateB")
                .withBean("templateA", StringRedisTemplate.class, () -> new StringRedisTemplate(connectionFactory))
                .withBean("templateB", StringRedisTemplate.class, () -> new StringRedisTemplate(connectionFactory))
                .run(context -> assertThat(context.getBean(CocoCaptchaStore.class))
                        .isInstanceOf(RedisCocoCaptchaStore.class));
    }

    @Test
    void businessStoreBacksOffTheRedisStore() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        this.runner.withPropertyValues("coco.captcha.enabled=true", "coco.captcha.store-type=redis")
                .withBean(StringRedisTemplate.class, () -> new StringRedisTemplate(connectionFactory))
                .withBean(CocoCaptchaStore.class, InMemoryCocoCaptchaStore::new)
                .run(context -> assertThat(context).hasSingleBean(CocoCaptchaStore.class));
    }

    static final class BusinessImageGenerator implements CocoCaptchaGenerator {
        @Override
        public CocoCaptchaType supportedType() {
            return CocoCaptchaType.IMAGE;
        }

        @Override
        public CocoCaptcha generate(String captchaId) {
            return new CocoCaptcha(captchaId, CocoCaptchaType.IMAGE, "challenge", "business-answer");
        }

        @Override
        public boolean matches(String submitted, String storedAnswer) {
            return storedAnswer.equals(submitted);
        }
    }
}
