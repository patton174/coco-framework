package io.github.coco.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CocoNotificationServiceTest {

    @Test
    void routesToTheChannelForTheNotificationType() {
        RecordingChannel sms = new RecordingChannel(CocoNotificationChannelType.SMS);
        CocoNotificationService service = new CocoNotificationService(List.of(sms));
        CocoNotificationResult result = service.send(
                CocoNotification.of(CocoNotificationChannelType.SMS, "13800000000", "hi"));
        assertThat(result.success()).isTrue();
        assertThat(sms.sent).hasSize(1);
    }

    @Test
    void unregisteredTypeFailsClosedWithoutThrowing() {
        CocoNotificationService service = new CocoNotificationService(List.of());
        CocoNotificationResult result = service.send(
                CocoNotification.of(CocoNotificationChannelType.EMAIL, "a@b.com", "hi"));
        assertThat(result.success()).isFalse();
        assertThat(result.detail()).contains("EMAIL");
        assertThat(service.supports(CocoNotificationChannelType.EMAIL)).isFalse();
    }

    @Test
    void lastChannelWinsWhenTwoDeclareTheSameType() {
        RecordingChannel first = new RecordingChannel(CocoNotificationChannelType.SMS);
        RecordingChannel second = new RecordingChannel(CocoNotificationChannelType.SMS);
        CocoNotificationService service = new CocoNotificationService(List.of(first, second));
        service.send(CocoNotification.of(CocoNotificationChannelType.SMS, "13800000000", "hi"));
        assertThat(first.sent).isEmpty();
        assertThat(second.sent).hasSize(1);
    }

    @Test
    void inMemoryInAppChannelStoresPerRecipient() {
        InMemoryInAppCocoNotificationChannel channel = new InMemoryInAppCocoNotificationChannel();
        channel.send(CocoNotification.of(CocoNotificationChannelType.IN_APP, "user-1", "first"));
        channel.send(CocoNotification.of(CocoNotificationChannelType.IN_APP, "user-1", "second"));
        channel.send(CocoNotification.of(CocoNotificationChannelType.IN_APP, "user-2", "other"));
        assertThat(channel.inbox("user-1")).extracting(CocoNotification::content).containsExactly("first", "second");
        assertThat(channel.inbox("user-2")).hasSize(1);
        assertThat(channel.inbox("absent")).isEmpty();
    }

    @Test
    void loggingChannelReportsSuccessForItsDeclaredType() {
        LoggingCocoNotificationChannel channel = new LoggingCocoNotificationChannel(CocoNotificationChannelType.EMAIL);
        assertThat(channel.supportedType()).isEqualTo(CocoNotificationChannelType.EMAIL);
        CocoNotificationResult result = channel.send(
                new CocoNotification(CocoNotificationChannelType.EMAIL, "a@b.com", "subject", "body", null));
        assertThat(result.success()).isTrue();
    }

    @Test
    void blankRecipientIsRejectedAtConstruction() {
        assertThat(catchThrowable(() -> CocoNotification.of(CocoNotificationChannelType.SMS, "  ", "hi")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Throwable catchThrowable(Runnable runnable) {
        try {
            runnable.run();
            return null;
        }
        catch (Throwable throwable) {
            return throwable;
        }
    }

    private static final class RecordingChannel implements CocoNotificationChannel {
        private final CocoNotificationChannelType type;
        private final List<CocoNotification> sent = new java.util.ArrayList<>();

        private RecordingChannel(CocoNotificationChannelType type) {
            this.type = type;
        }

        @Override
        public CocoNotificationChannelType supportedType() {
            return this.type;
        }

        @Override
        public CocoNotificationResult send(CocoNotification notification) {
            this.sent.add(notification);
            return CocoNotificationResult.success(this.type, "recorded");
        }
    }
}
