package io.github.coco.storage.s3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Reversible single-header codec that prevents S3 metadata key canonicalization from changing business metadata. */
final class S3MetadataCodec {

    static final String HEADER = "coco-metadata-v1";

    private static final int MAX_ENCODED_BYTES = 1800;

    private static final int MAX_ENTRIES = 128;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private S3MetadataCodec() {
    }

    static Map<String, String> encode(Map<String, String> metadata) throws IOException {
        Map<String, String> checkedMetadata = metadata == null ? Map.of() : metadata;
        if (checkedMetadata.size() > MAX_ENTRIES) {
            throw new IOException("S3 metadata exceeds supported entry limit");
        }
        Map<String, String> sortedMetadata = new TreeMap<>();
        for (Map.Entry<String, String> entry : checkedMetadata.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IOException("S3 metadata must not contain null keys or values");
            }
            validateCharacters(entry.getKey());
            validateCharacters(entry.getValue());
            sortedMetadata.put(entry.getKey(), entry.getValue());
        }
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        for (Map.Entry<String, String> entry : sortedMetadata.entrySet()) {
            root.put(entry.getKey(), entry.getValue());
        }
        String encoded;
        try {
            encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(OBJECT_MAPPER.writeValueAsBytes(root));
        }
        catch (RuntimeException exception) {
            throw new IOException("S3 metadata cannot be encoded");
        }
        if (encoded.length() > MAX_ENCODED_BYTES) {
            throw new IOException("S3 metadata exceeds supported encoded size");
        }
        return Map.of(HEADER, encoded);
    }

    static Map<String, String> decode(Map<String, String> headers) throws IOException {
        String encoded = requireHeader(headers);
        if (encoded.length() > MAX_ENCODED_BYTES) {
            throw new IOException("S3 metadata header exceeds supported size");
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(encoded);
            JsonNode root;
            try (JsonParser parser = OBJECT_MAPPER.getFactory().createParser(json)) {
                parser.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());
                root = OBJECT_MAPPER.readTree(parser);
                if (root == null || parser.nextToken() != null) {
                    throw invalidHeader();
                }
            }
            if (!root.isObject() || root.size() > MAX_ENTRIES) {
                throw invalidHeader();
            }
            Map<String, String> result = new LinkedHashMap<>();
            var fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!field.getValue().isTextual()) {
                    throw invalidHeader();
                }
                validateCharacters(field.getKey());
                validateCharacters(field.getValue().textValue());
                result.put(field.getKey(), field.getValue().textValue());
            }
            Map<String, String> decoded = Map.copyOf(result);
            if (!encoded.equals(encode(decoded).get(HEADER))) {
                throw invalidHeader();
            }
            return decoded;
        }
        catch (IOException | RuntimeException exception) {
            throw invalidHeader();
        }
    }

    private static String requireHeader(Map<String, String> headers) throws IOException {
        if (headers == null || headers.isEmpty()) {
            throw new IOException("missing S3 metadata header");
        }
        int matchingHeaders = 0;
        boolean exactHeader = false;
        String encoded = null;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && HEADER.equalsIgnoreCase(entry.getKey())) {
                matchingHeaders++;
                if (HEADER.equals(entry.getKey())) {
                    exactHeader = true;
                    encoded = entry.getValue();
                }
            }
        }
        if (matchingHeaders == 0) {
            throw new IOException("missing S3 metadata header");
        }
        if (matchingHeaders != 1 || !exactHeader || encoded == null || encoded.isEmpty()) {
            throw new IOException("invalid S3 metadata header marker");
        }
        return encoded;
    }

    private static void validateCharacters(String value) throws IOException {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IOException("S3 metadata contains control characters");
            }
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (!value.equals(new String(bytes, StandardCharsets.UTF_8))) {
            throw new IOException("invalid Unicode in S3 metadata");
        }
    }

    private static IOException invalidHeader() {
        return new IOException("invalid S3 metadata header");
    }
}
