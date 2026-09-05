package io.github.coco.messaging.internal;

import java.util.Objects;

import io.github.coco.messaging.CocoMessageEnvelope;
import io.github.coco.messaging.CocoMessagePublisher;
import io.github.coco.messaging.CocoMessageTransport;

/** 默认 Coco 消息发布器。 */
public final class DefaultCocoMessagePublisher implements CocoMessagePublisher {

    private final CocoMessageTransport transport;

    /** @param transport 消息传输层 */
    public DefaultCocoMessagePublisher(CocoMessageTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
    }

    @Override
    public void publish(CocoMessageEnvelope envelope) {
        this.transport.publish(Objects.requireNonNull(envelope, "envelope must not be null"));
    }
}
