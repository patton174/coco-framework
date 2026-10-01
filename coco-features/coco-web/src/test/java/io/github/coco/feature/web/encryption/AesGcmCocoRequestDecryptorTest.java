package io.github.coco.feature.web.encryption;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

/**
 * {@link AesGcmCocoRequestDecryptor} 单元测试。
 *
 * @author patton174
 * @since 1.0.0
 */
class AesGcmCocoRequestDecryptorTest {

    private static final byte[] KEY = "0123456789abcdef".getBytes();

    private final CocoEncryptionProperties properties = new CocoEncryptionProperties();

    private final AesGcmCocoRequestDecryptor decryptor = new AesGcmCocoRequestDecryptor(properties);

    @Test
    void decryptReturnsPlaintextOnValidCiphertext() throws Exception {
        byte[] plaintext = "hello coco".getBytes();
        byte[] iv = randomIv();
        byte[] aad = CocoEncryptionAssociatedData.from("app-1", null, Base64.getEncoder().encodeToString(iv),
                "AES-GCM", true);
        byte[] ciphertext = encrypt(plaintext, KEY, iv, aad);
        CocoEncryptedRequest request = new CocoEncryptedRequest("app-1", null,
                Base64.getEncoder().encodeToString(iv), "AES-GCM", true,
                Base64.getEncoder().encodeToString(ciphertext).getBytes());
        CocoRequestDecryptionContext context = new CocoRequestDecryptionContext(request,
                new CocoEncryptionKey("app-1", null, KEY), aad);

        byte[] result = decryptor.decrypt(context);

        assertArrayEquals(plaintext, result);
    }

    @Test
    void decryptThrowsOnUnsupportedAlgorithm() {
        byte[] iv = randomIv();
        CocoEncryptedRequest request = new CocoEncryptedRequest("app-1", null,
                Base64.getEncoder().encodeToString(iv), "ChaCha20", true,
                Base64.getEncoder().encodeToString(new byte[32]).getBytes());
        CocoRequestDecryptionContext context = new CocoRequestDecryptionContext(request,
                new CocoEncryptionKey("app-1", null, KEY));

        CocoRequestDecryptException ex = assertThrows(CocoRequestDecryptException.class,
                () -> decryptor.decrypt(context));
        assertNotNull(ex.failureKind());
    }

    @Test
    void decryptThrowsOnTamperedCiphertext() throws Exception {
        byte[] plaintext = "hello coco".getBytes();
        byte[] iv = randomIv();
        byte[] aad = CocoEncryptionAssociatedData.from("app-1", null, Base64.getEncoder().encodeToString(iv),
                "AES-GCM", true);
        byte[] ciphertext = encrypt(plaintext, KEY, iv, aad);
        ciphertext[0] ^= 0xFF;
        CocoEncryptedRequest request = new CocoEncryptedRequest("app-1", null,
                Base64.getEncoder().encodeToString(iv), "AES-GCM", true,
                Base64.getEncoder().encodeToString(ciphertext).getBytes());
        CocoRequestDecryptionContext context = new CocoRequestDecryptionContext(request,
                new CocoEncryptionKey("app-1", null, KEY), aad);

        CocoRequestDecryptException ex = assertThrows(CocoRequestDecryptException.class,
                () -> decryptor.decrypt(context));
        assertNotNull(ex.failureKind());
    }

    @Test
    void decryptThrowsOnWrongKey() throws Exception {
        byte[] plaintext = "hello coco".getBytes();
        byte[] iv = randomIv();
        byte[] correctKey = KEY;
        byte[] wrongKey = "fedcba9876543210".getBytes();
        byte[] aad = CocoEncryptionAssociatedData.from("app-1", null, Base64.getEncoder().encodeToString(iv),
                "AES-GCM", true);
        byte[] ciphertext = encrypt(plaintext, correctKey, iv, aad);
        CocoEncryptedRequest request = new CocoEncryptedRequest("app-1", null,
                Base64.getEncoder().encodeToString(iv), "AES-GCM", true,
                Base64.getEncoder().encodeToString(ciphertext).getBytes());
        CocoRequestDecryptionContext context = new CocoRequestDecryptionContext(request,
                new CocoEncryptionKey("app-1", null, wrongKey), aad);

        assertThrows(CocoRequestDecryptException.class, () -> decryptor.decrypt(context));
    }

    @Test
    void decryptAcceptsNormalizedAlgorithmNames() throws Exception {
        byte[] plaintext = "normalize test".getBytes();
        for (String algorithm : new String[]{"aes_gcm", "AES/GCM/NoPadding"}) {
            byte[] iv = randomIv();
            byte[] aad = CocoEncryptionAssociatedData.from("app-1", null,
                    Base64.getEncoder().encodeToString(iv), algorithm, true);
            byte[] ciphertext = encrypt(plaintext, KEY, iv, aad);
            CocoEncryptedRequest request = new CocoEncryptedRequest("app-1", null,
                    Base64.getEncoder().encodeToString(iv), algorithm, true,
                    Base64.getEncoder().encodeToString(ciphertext).getBytes());
            CocoRequestDecryptionContext context = new CocoRequestDecryptionContext(request,
                    new CocoEncryptionKey("app-1", null, KEY), aad);

            byte[] result = decryptor.decrypt(context);

            assertArrayEquals(plaintext, result);
        }
    }

    private static byte[] encrypt(byte[] plaintext, byte[] key, byte[] iv, byte[] aad) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad);
        return cipher.doFinal(plaintext);
    }

    private static byte[] randomIv() {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
}