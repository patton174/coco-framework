package io.github.coco.storage.s3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reversible single-header codec that prevents S3 metadata key canonicalization from changing business metadata. */
final class S3MetadataCodec {

    static final String HEADER = "coco-metadata-v1";

    private static final int MAX_ENCODED_BYTES = 1800;

    private static final int MAX_ENTRIES = 128;

    private S3MetadataCodec() {
    }

    static Map<String, String> encode(Map<String, String> metadata) throws IOException {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        if (metadata.size() > MAX_ENTRIES) {
            throw new IOException("S3 metadata exceeds supported entry limit");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(metadata.size());
                for (Map.Entry<String, String> entry : metadata.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        throw new IOException("S3 metadata must not contain null keys or values");
                    }
                    validateCharacters(entry.getKey());
                    validateCharacters(entry.getValue());
                    write(output, entry.getKey());
                    write(output, entry.getValue());
                }
            }
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
            if (encoded.length() > MAX_ENCODED_BYTES) {
                throw new IOException("S3 metadata exceeds supported encoded size");
            }
            return Map.of(HEADER, encoded);
        } catch (IllegalArgumentException exception) {
            throw new IOException("S3 metadata cannot be encoded", exception);
        }
    }

    static Map<String, String> decode(Map<String, String> headers) throws IOException {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        String encoded = headers.get(HEADER);
        if (encoded == null) {
            return Map.of();
        }
        if (encoded.length() > MAX_ENCODED_BYTES) {
            throw new IOException("S3 metadata header exceeds supported size");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(encoded)))) {
            int count = input.readInt();
            if (count < 0 || count > MAX_ENTRIES) {
                throw new IOException("invalid S3 metadata header");
            }
            Map<String, String> result = new LinkedHashMap<>();
            for (int index = 0; index < count; index++) {
                String key = read(input);
                String value = read(input);
                if (result.putIfAbsent(key, value) != null) {
                    throw new IOException("duplicate S3 metadata key");
                }
            }
            if (input.read() != -1) {
                throw new IOException("invalid S3 metadata header");
            }
            return Map.copyOf(result);
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid S3 metadata header", exception);
        }
    }

    private static void write(DataOutputStream output, String value) throws IOException {
        byte[] bytes;
        try {
            java.nio.ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(value));
            bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
        }
        catch (CharacterCodingException exception) {
            throw new IOException("invalid UTF-16 in S3 metadata", exception);
        }
        if (bytes.length > 65535) {
            throw new IOException("S3 metadata value exceeds supported size");
        }
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static String read(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("truncated S3 metadata header");
        }
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            validateCharacters(value);
            return value;
        }
        catch (CharacterCodingException exception) {
            throw new IOException("invalid UTF-8 in S3 metadata header", exception);
        }
    }

    private static void validateCharacters(String value) throws IOException {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IOException("S3 metadata contains control characters");
            }
        }
    }
}
