package io.github.coco.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CocoNotificationAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoNotificationAutoConfiguration.class));

    @Test
    void disabledByDefaultRegistersNothing() {
        this.runner.run(context -> assertThat(context).doesNotHaveBean(CocoNotificationService.class));
    }

    @Test
    void enabledRegistersServiceWithReferenceChannelsForAllTypes() {
        this.runner.withPropertyValues("coco.notification.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(CocoNotificationService.class);
            CocoNotificationService service = context.getBean(CocoNotificationService.class);
            assertThat(service.supports(CocoNotificationChannelType.SMS)).isTrue();
            assertThat(service.supports(CocoNotificationChannelType.EMAIL)).isTrue();
            assertThat(service.supports(CocoNotificationChannelType.IN_APP)).isTrue();
        });
    }

    @Test
    void disablingFallbacksLeavesTypesUnsupported() {
        this.runner.withPropertyValues("coco.notification.enabled=true",
                "coco.notification.logging-fallback=false", "coco.notification.in-memory-in-app=false")
                .run(context -> {
                    CocoNotificationService service = context.getBean(CocoNotificationService.class);
                    assertThat(service.supports(CocoNotificationChannelType.SMS)).isFalse();
                    assertThat(service.supports(CocoNotificationChannelType.IN_APP)).isFalse();
                    // With no fallback and no business channel, a send must fail closed, not throw.
                    CocoNotificationResult result = service.send(
                            CocoNotification.of(CocoNotificationChannelType.SMS, "13800000000", "hi"));
                    assertThat(result.success()).isFalse();
                    assertThat(result.detail()).contains("SMS");
                });
    }

    @Test
    void businessChannelWinsOverReferenceFallback() {
        this.runner.withPropertyValues("coco.notification.enabled=true")
                .withBean("businessSms", CocoNotificationChannel.class, BusinessSmsChannel::new)
                .run(context -> {
                    CocoNotificationService service = context.getBean(CocoNotificationService.class);
                    CocoNotificationResult result = service.send(
                            CocoNotification.of(CocoNotificationChannelType.SMS, "13800000000", "hi"));
                    // The business channel's provider id proves it, not the logging fallback, handled it.
                    assertThat(result.providerMessageId()).isEqualTo("business");
                });
    }

    static final class BusinessSmsChannel implements CocoNotificationChannel {
        @Override
        public CocoNotificationChannelType supportedType() {
            return CocoNotificationChannelType.SMS;
        }

        @Override
        public CocoNotificationResult send(CocoNotification notification) {
            return CocoNotificationResult.success(CocoNotificationChannelType.SMS, "business");
        }
    }
}
