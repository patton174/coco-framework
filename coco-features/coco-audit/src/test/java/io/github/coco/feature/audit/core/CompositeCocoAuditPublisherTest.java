package io.github.coco.feature.audit.core;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositeCocoAuditPublisherTest {

    @Test
    void ignoresRecorderFailureAndContinuesWhenConfiguredFailOpen() {
        AtomicInteger successfulRecords = new AtomicInteger();
        RuntimeException recorderFailure = new IllegalStateException("recorder failed");
        CocoAuditRecorder failingRecorder = event -> {
            throw recorderFailure;
        };
        CocoAuditRecorder succeedingRecorder = event -> successfulRecords.incrementAndGet();
        CompositeCocoAuditPublisher publisher = new CompositeCocoAuditPublisher(
                List.of(failingRecorder, succeedingRecorder),
                new PolicyCocoAuditErrorHandler(CocoAuditFailurePolicy.IGNORE));

        publisher.publish(CocoAuditEvent.builder("runtime-contract").build());

        assertThat(successfulRecords).hasValue(1);
    }

    @Test
    void propagatesRecorderFailureAndStopsWhenConfiguredFailClosed() {
        AtomicInteger subsequentRecords = new AtomicInteger();
        RuntimeException recorderFailure = new IllegalStateException("recorder failed");
        CocoAuditRecorder failingRecorder = event -> {
            throw recorderFailure;
        };
        CocoAuditRecorder subsequentRecorder = event -> subsequentRecords.incrementAndGet();
        CompositeCocoAuditPublisher publisher = new CompositeCocoAuditPublisher(
                List.of(failingRecorder, subsequentRecorder),
                new PolicyCocoAuditErrorHandler(CocoAuditFailurePolicy.THROW));

        assertThatThrownBy(() -> publisher.publish(CocoAuditEvent.builder("runtime-contract").build()))
                .isSameAs(recorderFailure);
        assertThat(subsequentRecords).hasValue(0);
    }

    @Test
    void doesNotRecursivelyHandleAnErrorHandlerFailure() {
        AtomicInteger handlerInvocations = new AtomicInteger();
        RuntimeException handlerFailure = new IllegalStateException("handler failed");
        CocoAuditRecorder failingRecorder = event -> {
            throw new IllegalArgumentException("recorder failed");
        };
        CocoAuditErrorHandler failingHandler = (event, recorder, failure) -> {
            handlerInvocations.incrementAndGet();
            throw handlerFailure;
        };
        CompositeCocoAuditPublisher publisher = new CompositeCocoAuditPublisher(List.of(failingRecorder),
                failingHandler);

        assertThatThrownBy(() -> publisher.publish(CocoAuditEvent.builder("runtime-contract").build()))
                .isSameAs(handlerFailure);
        assertThat(handlerInvocations).hasValue(1);
    }

}
