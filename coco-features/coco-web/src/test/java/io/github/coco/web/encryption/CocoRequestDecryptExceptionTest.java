package io.github.coco.web.encryption;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CocoRequestDecryptExceptionTest {

    @Test
    void legacyConstructorKeepsGenericDecryptFailureCode() {
        CocoRequestDecryptException exception = new CocoRequestDecryptException("custom decryptor failed", null);

        assertEquals("custom decryptor failed", exception.getMessage());
        assertEquals("coco.web.encryption.decrypt-failed", exception.messageCode());
        assertEquals(CocoRequestDecryptException.FailureKind.AUTHENTICATION_FAILED, exception.failureKind());
    }

    @Test
    void factoriesExposeClassifiedMessageCodes() {
        CocoRequestDecryptException malformed = CocoRequestDecryptException.malformed(
                "coco.web.encryption.malformed-request", null);
        CocoRequestDecryptException authenticationFailed = CocoRequestDecryptException.authenticationFailed(
                "coco.web.encryption.decrypt-failed", null);

        assertEquals("coco.web.encryption.malformed-request", malformed.messageCode());
        assertEquals(CocoRequestDecryptException.FailureKind.MALFORMED_REQUEST, malformed.failureKind());
        assertEquals("coco.web.encryption.decrypt-failed", authenticationFailed.messageCode());
        assertEquals(CocoRequestDecryptException.FailureKind.AUTHENTICATION_FAILED,
                authenticationFailed.failureKind());
    }
}
