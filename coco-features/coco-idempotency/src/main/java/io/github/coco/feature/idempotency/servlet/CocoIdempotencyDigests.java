package io.github.coco.feature.idempotency.servlet;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import jakarta.servlet.http.HttpServletRequest;

final class CocoIdempotencyDigests {

    private CocoIdempotencyDigests() {
    }

    static String scopeDigest(String scope) {
        return sha256(scope.getBytes(StandardCharsets.UTF_8));
    }

    static String scopedKeyHash(String scopeDigest, String key) {
        MessageDigest digest = sha256Digest();
        update(digest, scopeDigest);
        update(digest, key);
        return HexFormat.of().formatHex(digest.digest());
    }

    static String requestHash(HttpServletRequest request, byte[] body) {
        MessageDigest digest = sha256Digest();
        update(digest, request.getMethod());
        update(digest, request.getRequestURI());
        update(digest, request.getQueryString());
        update(digest, request.getContentType());
        update(digest, body);
        return HexFormat.of().formatHex(digest.digest());
    }

    static String prefix(String digest) {
        return digest == null ? "none" : digest.substring(0, Math.min(16, digest.length()));
    }

    private static String sha256(byte[] value) {
        return HexFormat.of().formatHex(sha256Digest().digest(value));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static void update(MessageDigest digest, String value) {
        update(digest, value == null ? null : value.getBytes(StandardCharsets.UTF_8));
    }

    private static void update(MessageDigest digest, byte[] value) {
        if (value == null) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
            return;
        }
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
        digest.update(value);
    }
}
