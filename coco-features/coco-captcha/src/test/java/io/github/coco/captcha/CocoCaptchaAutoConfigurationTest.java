package io.github.coco.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CocoCaptchaAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoCaptchaAutoConfiguration.class));

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
