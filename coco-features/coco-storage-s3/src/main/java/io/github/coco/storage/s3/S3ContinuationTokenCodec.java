package io.github.coco.storage.s3;

import java.io.IOException;
import java.util.Base64;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Strict context-bound codec for opaque S3 continuation tokens.
 * <p>The encoding provides context validation, not authenticity; no server-side signing secret is configured.</p>
 */
final class S3ContinuationTokenCodec {

    private static final String PREFIX = "coco-s3-list.";

    private static final int VERSION = 1;

    private static final int FIELD_COUNT = 5;

    private static final int MAX_TOKEN_LENGTH = 16 * 1024;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private S3ContinuationTokenCodec() {
    }

    static String encode(String bucket, String keyPrefix, String requestPrefix, String providerToken) {
        if (providerToken == null || providerToken.isEmpty()) {
            throw invalid();
        }
        try {
            ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.put("version", VERSION);
            root.put("bucket", bucket);
            root.put("keyPrefix", keyPrefix);
            root.put("requestPrefix", requestPrefix);
            root.put("providerToken", providerToken);
            byte[] json = OBJECT_MAPPER.writeValueAsBytes(root);
            String token = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json);
            if (token.length() > MAX_TOKEN_LENGTH) {
                throw invalid();
            }
            return token;
        }
        catch (IOException | RuntimeException exception) {
            throw invalid();
        }
    }

    static String decode(String token, String bucket, String keyPrefix, String requestPrefix) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        if (!token.startsWith(PREFIX) || token.length() > MAX_TOKEN_LENGTH) {
            throw invalid();
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(token.substring(PREFIX.length()));
            JsonNode root;
            try (JsonParser parser = OBJECT_MAPPER.getFactory().createParser(json)) {
                root = OBJECT_MAPPER.readTree(parser);
                if (root == null || parser.nextToken() != null) {
                    throw invalid();
                }
            }
            if (!root.isObject() || root.size() != FIELD_COUNT
                    || !integer(root, "version", VERSION)
                    || !text(root, "bucket", bucket)
                    || !text(root, "keyPrefix", keyPrefix)
                    || !text(root, "requestPrefix", requestPrefix)) {
                throw invalid();
            }
            JsonNode providerTokenNode = root.get("providerToken");
            if (providerTokenNode == null || !providerTokenNode.isTextual()
                    || providerTokenNode.textValue().isEmpty()) {
                throw invalid();
            }
            String providerToken = providerTokenNode.textValue();
            if (!token.equals(encode(bucket, keyPrefix, requestPrefix, providerToken))) {
                throw invalid();
            }
            return providerToken;
        }
        catch (IOException | IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static boolean integer(JsonNode root, String name, int expected) {
        JsonNode value = root.get(name);
        return value != null && value.isIntegralNumber() && value.canConvertToInt()
                && value.intValue() == expected;
    }

    private static boolean text(JsonNode root, String name, String expected) {
        JsonNode value = root.get(name);
        return value != null && value.isTextual() && expected.equals(value.textValue());
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("invalid S3 continuation token");
    }
}
