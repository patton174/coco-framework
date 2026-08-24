package io.github.coco.scheduling;

import java.util.Objects;

import io.github.coco.i18n.CocoMessageService;

final class CocoSchedulingMessageResolver {

    private final CocoMessageService messages;

    CocoSchedulingMessageResolver(CocoMessageService messages) {
        this.messages = messages;
    }

    CocoSchedulingException error(CocoSchedulingMessage message, Object... args) {
        String resolved = this.messages == null ? message.code() : this.messages.getMessage(message, args);
        return new CocoSchedulingException(message, Objects.requireNonNullElse(resolved, message.code()));
    }
}
