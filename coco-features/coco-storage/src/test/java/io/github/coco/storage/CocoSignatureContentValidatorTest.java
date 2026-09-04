package io.github.coco.storage;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/**
 * 魔数签名内容校验器判定顺序和开关行为测试。
 * <p>
 * 覆盖危险签名先于扩展名一致性检查的顺序要求、各类危险内容的拒绝编码、扩展名与内容不一致的拒绝，
 * 以及两个校验开关分别关闭和同时关闭时的放行行为。
 * </p>
 */
class CocoSignatureContentValidatorTest {

    @Test
    void acceptsJpegContentDeclaredWithAJpgExtension() {
        CocoSignatureContentValidator validator = validator(new CocoStorageProperties.ValidationProperties());

        assertThatCode(() -> validator.validate(CocoContentProbe.of("photos/holiday.jpg", "image/jpeg",
                bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10)))).doesNotThrowAnyException();
    }

    @Test
    void reportsDangerousContentRatherThanSignatureMismatchForAnExecutableRenamedToPng() {
        CocoSignatureContentValidator validator = validator(new CocoStorageProperties.ValidationProperties());

        assertStorageCode(() -> validator.validate(CocoContentProbe.of("uploads/evil.png", "image/png",
                bytes(0x4D, 0x5A, 0x90, 0x00, 0x03))), CocoStorageErrorCode.DANGEROUS_CONTENT);
    }

    @Test
    void rejectsElfJavaClassAndShebangContentAsDangerous() {
        CocoSignatureContentValidator validator = validator(new CocoStorageProperties.ValidationProperties());

        assertStorageCode(() -> validator.validate(CocoContentProbe.of("uploads/tool.pdf", "application/pdf",
                bytes(0x7F, 0x45, 0x4C, 0x46, 0x02, 0x01))), CocoStorageErrorCode.DANGEROUS_CONTENT);
        assertStorageCode(() -> validator.validate(CocoContentProbe.of("uploads/Payload.gif", "image/gif",
                bytes(0xCA, 0xFE, 0xBA, 0xBE, 0x00, 0x00))), CocoStorageErrorCode.DANGEROUS_CONTENT);
        assertStorageCode(() -> validator.validate(CocoContentProbe.of("uploads/notes.txt", "text/plain",
                "#!/bin/bash\nrm -rf /\n".getBytes())), CocoStorageErrorCode.DANGEROUS_CONTENT);
    }

    @Test
    void rejectsPngContentDeclaredWithAJpgExtensionAsASignatureMismatch() {
        CocoSignatureContentValidator validator = validator(new CocoStorageProperties.ValidationProperties());

        assertStorageCode(() -> validator.validate(CocoContentProbe.of("photos/mislabelled.jpg", "image/jpeg",
                bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))), CocoStorageErrorCode.SIGNATURE_MISMATCH);
    }

    @Test
    void acceptsArbitraryContentForExtensionsWithoutARegisteredSignature() {
        CocoSignatureContentValidator validator = validator(new CocoStorageProperties.ValidationProperties());

        assertThatCode(() -> validator.validate(CocoContentProbe.of("documents/report.txt", "text/plain",
                "quarterly numbers".getBytes()))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(CocoContentProbe.of("documents/no-extension", "text/plain",
                "quarterly numbers".getBytes()))).doesNotThrowAnyException();
    }

    @Test
    void acceptsExecutableContentWhenDangerousSignatureRejectionIsDisabled() {
        CocoStorageProperties.ValidationProperties properties = new CocoStorageProperties.ValidationProperties();
        properties.setRejectDangerousSignatures(false);
        CocoSignatureContentValidator validator = validator(properties);

        assertThatCode(() -> validator.validate(CocoContentProbe.of("uploads/tool.txt", "text/plain",
                bytes(0x4D, 0x5A, 0x90, 0x00)))).doesNotThrowAnyException();
    }

    @Test
    void acceptsMismatchedContentWhenSignatureMatchingIsNotRequired() {
        CocoStorageProperties.ValidationProperties properties = new CocoStorageProperties.ValidationProperties();
        properties.setRequireSignatureMatch(false);
        CocoSignatureContentValidator validator = validator(properties);

        assertThatCode(() -> validator.validate(CocoContentProbe.of("photos/mislabelled.jpg", "image/jpeg",
                bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))).doesNotThrowAnyException();
        assertStorageCode(() -> validator.validate(CocoContentProbe.of("photos/evil.jpg", "image/jpeg",
                bytes(0x4D, 0x5A, 0x90, 0x00))), CocoStorageErrorCode.DANGEROUS_CONTENT);
    }

    @Test
    void acceptsEverythingWhenBothValidationSwitchesAreDisabled() {
        CocoStorageProperties.ValidationProperties properties = new CocoStorageProperties.ValidationProperties();
        properties.setRejectDangerousSignatures(false);
        properties.setRequireSignatureMatch(false);
        CocoSignatureContentValidator validator = validator(properties);

        assertThatCode(() -> {
            validator.validate(CocoContentProbe.of("uploads/evil.png", "image/png", bytes(0x4D, 0x5A, 0x90, 0x00)));
            validator.validate(CocoContentProbe.of("uploads/tool.pdf", "application/pdf", bytes(0x7F, 0x45, 0x4C, 0x46)));
            validator.validate(CocoContentProbe.of("photos/mislabelled.jpg", "image/jpeg",
                    bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)));
            validator.validate(CocoContentProbe.of("uploads/empty.zip", "application/zip", new byte[0]));
        }).doesNotThrowAnyException();
    }

    private static CocoSignatureContentValidator validator(CocoStorageProperties.ValidationProperties properties) {
        return new CocoSignatureContentValidator(properties);
    }

    private static void assertStorageCode(ThrowingCallable action, CocoStorageErrorCode expected) {
        assertThatThrownBy(action).isInstanceOf(CocoStorageException.class)
                .extracting(exception -> ((CocoStorageException) exception).code()).isEqualTo(expected.code());
    }

    private static byte[] bytes(int... values) {
        byte[] content = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            content[index] = (byte) values[index];
        }
        return content;
    }
}
