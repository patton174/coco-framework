package io.github.coco.exception.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.coco.exception.CocoBusinessCode;
import io.github.coco.exception.CocoCommonErrorCode;
import io.github.coco.exception.CocoException;
import org.junit.jupiter.api.Test;

/**
 * CocoException 旧消费者兼容性测试。
 *
 * @author patton174
 * @since 1.0.0
 */
class LegacyCocoExceptionConsumerTest {

    @Test
    void invokesEveryPublishedConstructorOverload() {
        IllegalStateException cause = new IllegalStateException("legacy cause");

        assertEquals(CocoCommonErrorCode.UNKNOWN.code(), new CocoException(CocoCommonErrorCode.UNKNOWN).code());
        assertEquals(CocoCommonErrorCode.UNKNOWN.code(),
                new CocoException(CocoCommonErrorCode.UNKNOWN, cause, "value").code());
        assertEquals("legacy.business", new CocoException(LegacyBusinessCode.BUSINESS).code());
        assertEquals("legacy.business", new CocoException(LegacyBusinessCode.BUSINESS, cause, "value").code());
        assertEquals("legacy.code", new CocoException("legacy.code").code());
        assertEquals("legacy.code", new CocoException("legacy.code", "legacy message").code());
        assertEquals("legacy.code", new CocoException("legacy.code", "legacy message", "value").code());
        assertEquals("legacy.code", new CocoException("legacy.code", "legacy message", cause).code());
        assertEquals("legacy.code", new CocoException("legacy.code", "legacy message", cause, "value").code());
    }

    private enum LegacyBusinessCode implements CocoBusinessCode {

        BUSINESS(7001, "legacy.business");

        private final int code;

        private final String messageCode;

        LegacyBusinessCode(int code, String messageCode) {
            this.code = code;
            this.messageCode = messageCode;
        }

        @Override
        public int code() {
            return this.code;
        }

        @Override
        public String messageCode() {
            return this.messageCode;
        }
    }
}
