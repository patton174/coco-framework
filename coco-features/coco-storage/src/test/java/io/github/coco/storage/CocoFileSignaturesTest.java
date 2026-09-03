package io.github.coco.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 内置文件魔数签名表匹配行为测试。
 * <p>
 * 覆盖各签名组的正例、多片段与非零偏移量签名的判定、扩展名与内容不一致的拒绝、无签名扩展名的放行，
 * 以及探测字节不足时不抛异常的边界行为。
 * </p>
 */
class CocoFileSignaturesTest {

    @Test
    void matchesEachAllowedSignatureGroupAgainstItsRealMagicBytes() {
        assertThat(CocoFileSignatures.matchesExtension("jpg", bytes(0xFF, 0xD8, 0xFF, 0xE0))).isTrue();
        assertThat(CocoFileSignatures.matchesExtension("jpeg", bytes(0xFF, 0xD8, 0xFF, 0xE1))).isTrue();
        assertThat(CocoFileSignatures.matchesExtension("png",
                bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))).isTrue();
        assertThat(CocoFileSignatures.matchesExtension("gif", bytes(0x47, 0x49, 0x46, 0x38, 0x39, 0x61))).isTrue();
        assertThat(CocoFileSignatures.matchesExtension("pdf", bytes(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31))).isTrue();
        assertThat(CocoFileSignatures.matchesExtension("zip", bytes(0x50, 0x4B, 0x03, 0x04))).isTrue();
    }

    @Test
    void requiresBothWebpPartsAndRejectsRiffContainerWithAnotherFormAtOffsetEight() {
        assertThat(CocoFileSignatures.matchesExtension("webp",
                bytes(0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50))).isTrue();
        assertThat(CocoFileSignatures.matchesExtension("webp",
                bytes(0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x41, 0x56, 0x49, 0x20))).isFalse();
        assertThat(CocoFileSignatures.matchesExtension("webp", bytes(0x52, 0x49, 0x46, 0x46))).isFalse();
    }

    @Test
    void honoursTheDeclaredOffsetForMp4FtypAndRejectsTheSameBytesAtOffsetZero() {
        assertThat(CocoFileSignatures.matchesExtension("mp4",
                bytes(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70))).isTrue();
        assertThat(CocoFileSignatures.matchesExtension("mp4",
                bytes(0x66, 0x74, 0x79, 0x70, 0x00, 0x00, 0x00, 0x18))).isFalse();
    }

    @Test
    void rejectsContentWhoseMagicBytesBelongToAnotherAllowedExtension() {
        byte[] png = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);

        assertThat(CocoFileSignatures.matchesExtension("jpg", png)).isFalse();
        assertThat(CocoFileSignatures.matchesExtension("png", png)).isTrue();
    }

    @Test
    void passesExtensionsWithoutAReliableMagicNumberWithoutRegisteringASignature() {
        assertThat(CocoFileSignatures.hasKnownSignature("txt")).isFalse();
        assertThat(CocoFileSignatures.matchesExtension("txt", "any text at all".getBytes())).isTrue();
        assertThat(CocoFileSignatures.matchesExtension("txt", bytes(0x89, 0x50, 0x4E, 0x47))).isTrue();
        assertThat(CocoFileSignatures.matchesExtension("", new byte[0])).isTrue();
        assertThat(CocoFileSignatures.hasKnownSignature("jpg")).isTrue();
    }

    @Test
    void acceptsEveryZipContainerVariantForZipBackedExtensions() {
        for (String extension : new String[] { "zip", "docx", "xlsx", "pptx" }) {
            assertThat(CocoFileSignatures.matchesExtension(extension, bytes(0x50, 0x4B, 0x03, 0x04))).isTrue();
            assertThat(CocoFileSignatures.matchesExtension(extension, bytes(0x50, 0x4B, 0x05, 0x06))).isTrue();
            assertThat(CocoFileSignatures.matchesExtension(extension, bytes(0x50, 0x4B, 0x07, 0x08))).isTrue();
            assertThat(CocoFileSignatures.matchesExtension(extension, bytes(0x50, 0x4B, 0x01, 0x02))).isFalse();
        }
    }

    @Test
    void looksUpExtensionsCaseInsensitivelyAndIgnoresSurroundingWhitespace() {
        byte[] jpeg = bytes(0xFF, 0xD8, 0xFF, 0xE0);

        assertThat(CocoFileSignatures.hasKnownSignature("JPG")).isTrue();
        assertThat(CocoFileSignatures.matchesExtension("JPG", jpeg)).isTrue();
        assertThat(CocoFileSignatures.matchesExtension("Jpg", bytes(0x89, 0x50, 0x4E, 0x47))).isFalse();
        assertThat(CocoFileSignatures.matchesExtension(" jpg ", jpeg)).isTrue();
    }

    @Test
    void treatsProbesShorterThanTheSignatureAsNoMatchWithoutThrowing() {
        assertThat(CocoFileSignatures.matchesExtension("png", bytes(0x89, 0x50))).isFalse();
        assertThat(CocoFileSignatures.matchesExtension("jpg", bytes(0xFF, 0xD8))).isFalse();
        assertThat(CocoFileSignatures.matchesExtension("mp4", bytes(0x00, 0x00, 0x00, 0x18, 0x66))).isFalse();
        assertThat(CocoFileSignatures.matchesExtension("png", new byte[0])).isFalse();
        assertThat(CocoFileSignatures.matchesExtension("txt", new byte[0])).isTrue();
        assertThat(CocoFileSignatures.findDangerous(new byte[0])).isEmpty();
        assertThat(CocoFileSignatures.findDangerous(bytes(0x7F, 0x45))).isEmpty();
    }

    @Test
    void findsEveryDangerousSignatureAndLeavesBenignContentAlone() {
        assertThat(CocoFileSignatures.findDangerous(bytes(0x4D, 0x5A, 0x90, 0x00))).get()
                .extracting(CocoFileSignature::label).isEqualTo("PE executable (EXE/DLL)");
        assertThat(CocoFileSignatures.findDangerous(bytes(0x7F, 0x45, 0x4C, 0x46, 0x02))).get()
                .extracting(CocoFileSignature::label).isEqualTo("ELF executable");
        assertThat(CocoFileSignatures.findDangerous(bytes(0xCA, 0xFE, 0xBA, 0xBE, 0x00))).get()
                .extracting(CocoFileSignature::label).isEqualTo("Java class file");
        assertThat(CocoFileSignatures.findDangerous("#!/bin/sh\nexit 0\n".getBytes())).get()
                .extracting(CocoFileSignature::label).isEqualTo("Shell script shebang");
        assertThat(CocoFileSignatures.findDangerous(bytes(0xFF, 0xD8, 0xFF, 0xE0))).isEmpty();
        assertThat(CocoFileSignatures.findDangerous("plain report text".getBytes())).isEmpty();
    }

    private static byte[] bytes(int... values) {
        byte[] content = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            content[index] = (byte) values[index];
        }
        return content;
    }
}
