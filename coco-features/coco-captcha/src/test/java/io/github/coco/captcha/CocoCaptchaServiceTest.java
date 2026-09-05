package io.github.coco.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class CocoCaptchaServiceTest {

    private static CocoCaptchaService service(Clock clock, CocoCaptchaGenerator... generators) {
        return new CocoCaptchaService(List.of(generators), new InMemoryCocoCaptchaStore(clock),
                Duration.ofMinutes(2));
    }

    @Test
    void generateThenVerifySucceedsForImage() {
        // A generator whose answer is deterministic so the test can submit the right value.
        FixedGenerator gen = new FixedGenerator(CocoCaptchaType.IMAGE, "AB12");
        CocoCaptchaService service = service(Clock.systemUTC(), gen);
        CocoCaptcha.ClientView view = service.generate(CocoCaptchaType.IMAGE);
        assertThat(view.type()).isEqualTo(CocoCaptchaType.IMAGE);
        assertThat(service.verify(CocoCaptchaType.IMAGE, view.captchaId(), "ab12")).isTrue();
    }

    @Test
    void verifyIsSingleUse() {
        FixedGenerator gen = new FixedGenerator(CocoCaptchaType.IMAGE, "AB12");
        CocoCaptchaService service = service(Clock.systemUTC(), gen);
        CocoCaptcha.ClientView view = service.generate(CocoCaptchaType.IMAGE);
        assertThat(service.verify(CocoCaptchaType.IMAGE, view.captchaId(), "AB12")).isTrue();
        // Second attempt with the same id must fail: the answer was consumed.
        assertThat(service.verify(CocoCaptchaType.IMAGE, view.captchaId(), "AB12")).isFalse();
    }

    @Test
    void wrongSubmissionFailsAndAlsoConsumes() {
        FixedGenerator gen = new FixedGenerator(CocoCaptchaType.IMAGE, "AB12");
        CocoCaptchaService service = service(Clock.systemUTC(), gen);
        CocoCaptcha.ClientView view = service.generate(CocoCaptchaType.IMAGE);
        assertThat(service.verify(CocoCaptchaType.IMAGE, view.captchaId(), "ZZZZ")).isFalse();
        // Even a wrong attempt burns the captcha — no brute force on one id.
        assertThat(service.verify(CocoCaptchaType.IMAGE, view.captchaId(), "AB12")).isFalse();
    }

    @Test
    void expiredCaptchaFailsVerification() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-14T00:00:00Z"));
        FixedGenerator gen = new FixedGenerator(CocoCaptchaType.IMAGE, "AB12");
        CocoCaptchaService service = new CocoCaptchaService(List.of(gen),
                new InMemoryCocoCaptchaStore(clock), Duration.ofMinutes(2));
        CocoCaptcha.ClientView view = service.generate(CocoCaptchaType.IMAGE);
        clock.advance(Duration.ofMinutes(3));
        assertThat(service.verify(CocoCaptchaType.IMAGE, view.captchaId(), "AB12")).isFalse();
    }

    @Test
    void generateRejectsTypeWithoutGenerator() {
        CocoCaptchaService service = service(Clock.systemUTC());
        assertThatThrownBy(() -> service.generate(CocoCaptchaType.SLIDER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyReturnsFalseForUnknownCaptchaId() {
        FixedGenerator gen = new FixedGenerator(CocoCaptchaType.IMAGE, "AB12");
        CocoCaptchaService service = service(Clock.systemUTC(), gen);
        assertThat(service.verify(CocoCaptchaType.IMAGE, "never-issued", "AB12")).isFalse();
    }

    @Test
    void sliderMatchesWithinToleranceOnly() {
        SliderCocoCaptchaGenerator slider = new SliderCocoCaptchaGenerator(5);
        assertThat(slider.matches("103", "100")).isTrue();
        assertThat(slider.matches("106", "100")).isFalse();
        assertThat(slider.matches("not-a-number", "100")).isFalse();
    }

    @Test
    void imageGeneratorProducesBase64PngChallenge() {
        ImageCocoCaptchaGenerator image = new ImageCocoCaptchaGenerator(4);
        CocoCaptcha captcha = image.generate("id-1");
        assertThat(captcha.challenge()).startsWith("data:image/png;base64,");
        assertThat(captcha.answer()).hasSize(4);
        assertThat(image.matches(captcha.answer().toLowerCase(java.util.Locale.ROOT), captcha.answer())).isTrue();
    }

    @Test
    void smsCodeGeneratorProducesNumericAnswerAndEmptyChallenge() {
        SmsCodeCocoCaptchaGenerator sms = new SmsCodeCocoCaptchaGenerator(6);
        CocoCaptcha captcha = sms.generate("id-1");
        assertThat(captcha.challenge()).isEmpty();
        assertThat(captcha.answer()).hasSize(6).matches("\\d{6}");
    }

    @Test
    void clientViewNeverCarriesTheAnswer() {
        CocoCaptcha captcha = new CocoCaptcha("id-1", CocoCaptchaType.IMAGE, "challenge", "secret");
        CocoCaptcha.ClientView view = captcha.toClientView();
        assertThat(view.captchaId()).isEqualTo("id-1");
        assertThat(view.challenge()).isEqualTo("challenge");
        // ClientView has no answer accessor at all — this is a structural guarantee.
        assertThat(view.getClass().getRecordComponents())
                .noneMatch(rc -> rc.getName().equals("answer"));
    }

    private static final class FixedGenerator implements CocoCaptchaGenerator {
        private final CocoCaptchaType type;
        private final String answer;

        private FixedGenerator(CocoCaptchaType type, String answer) {
            this.type = type;
            this.answer = answer;
        }

        @Override
        public CocoCaptchaType supportedType() {
            return this.type;
        }

        @Override
        public CocoCaptcha generate(String captchaId) {
            return new CocoCaptcha(captchaId, this.type, "challenge", this.answer);
        }

        @Override
        public boolean matches(String submitted, String storedAnswer) {
            return submitted != null && submitted.strip().equalsIgnoreCase(storedAnswer);
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        private MutableClock(Instant instant) {
            this.instant = new AtomicReference<>(instant);
        }

        private void advance(Duration duration) {
            this.instant.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return this.instant.get();
        }
    }
}
