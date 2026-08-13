package io.github.coco.feature.idempotency.servlet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.coco.feature.idempotency.CocoIdempotencyProperties;
import jakarta.servlet.http.HttpServletResponse;

final class CocoIdempotencyResponseHeaders {

    private static final Set<String> TRANSIENT_HEADERS = Set.of("content-length", "date");

    private static final Set<String> FORBIDDEN_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-connection", "proxy-authenticate", "proxy-authorization",
            "proxy-authentication-info", "te", "trailer", "transfer-encoding", "upgrade", "set-cookie",
            "set-cookie2", "authorization", "authentication-info", "www-authenticate", "cookie", "x-api-key");

    private CocoIdempotencyResponseHeaders() {
    }

    static Map<String, List<String>> copy(Map<String, List<String>> source,
            CocoIdempotencyProperties properties) {
        Map<String, List<String>> checkedSource = source == null ? Map.of() : source;
        Set<String> connectionHeaders = connectionHeaders(checkedSource);
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, List<String>> copy = new LinkedHashMap<>();
        int headerCount = 0;
        int headerBytes = 0;

        for (Map.Entry<String, List<String>> entry : checkedSource.entrySet()) {
            String name = requireHeaderName(entry.getKey());
            String normalizedName = name.toLowerCase(Locale.ROOT);
            if (TRANSIENT_HEADERS.contains(normalizedName)) {
                continue;
            }
            if (forbidden(normalizedName, connectionHeaders)) {
                throw new CocoIdempotencyUnsafeResponseHeaderException(
                        "Response header cannot be cached by Coco idempotency");
            }
            List<String> values = entry.getValue();
            if (values == null) {
                throw new CocoIdempotencyUnsafeResponseHeaderException(
                        "Response header values must not be null");
            }
            String targetName = names.computeIfAbsent(normalizedName, ignored -> name);
            List<String> targetValues = copy.computeIfAbsent(targetName, ignored -> new ArrayList<>());
            for (String value : values) {
                int valueBytes = requireHeaderValue(value, properties);
                headerCount++;
                headerBytes += name.length() + valueBytes + 4;
                if (headerCount > properties.getMaxResponseHeaderCount()) {
                    throw new CocoIdempotencyResponseHeadersTooLargeException(
                            "Response header count exceeds the idempotency capture limit");
                }
                if (headerBytes > properties.getMaxResponseHeaderBytes()) {
                    throw new CocoIdempotencyResponseHeadersTooLargeException(
                            "Response headers exceed the idempotency capture limit");
                }
                targetValues.add(value);
            }
        }
        return copy;
    }

    static void apply(HttpServletResponse response, Map<String, List<String>> headers) {
        headers.forEach((name, values) -> {
            if (!values.isEmpty()) {
                response.setHeader(name, values.get(0));
                values.stream().skip(1).forEach(value -> response.addHeader(name, value));
            }
        });
    }

    private static Set<String> connectionHeaders(Map<String, List<String>> source) {
        Set<String> dynamic = new LinkedHashSet<>();
        source.forEach((name, values) -> {
            if (name == null || !"connection".equalsIgnoreCase(name) || values == null) {
                return;
            }
            for (String value : values) {
                if (value == null) {
                    continue;
                }
                for (String token : value.split(",")) {
                    String normalized = token.trim().toLowerCase(Locale.ROOT);
                    if (!normalized.isEmpty()) {
                        dynamic.add(normalized);
                    }
                }
            }
        });
        return dynamic;
    }

    private static boolean forbidden(String normalizedName, Set<String> connectionHeaders) {
        return FORBIDDEN_HEADERS.contains(normalizedName)
                || connectionHeaders.contains(normalizedName)
                || normalizedName.contains("auth")
                || normalizedName.contains("token");
    }

    private static String requireHeaderName(String name) {
        if (name == null || name.isBlank() || !name.chars().allMatch(CocoIdempotencyResponseHeaders::tokenCharacter)) {
            throw new CocoIdempotencyUnsafeResponseHeaderException("Response header name is invalid");
        }
        return name;
    }

    private static boolean tokenCharacter(int character) {
        return character >= '0' && character <= '9'
                || character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z'
                || "!#$%&'*+-.^_`|~".indexOf(character) >= 0;
    }

    private static int requireHeaderValue(String value, CocoIdempotencyProperties properties) {
        if (value == null || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\0') >= 0) {
            throw new CocoIdempotencyUnsafeResponseHeaderException("Response header value is invalid");
        }
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > properties.getMaxResponseHeaderValueBytes()) {
            throw new CocoIdempotencyResponseHeadersTooLargeException(
                    "Response header value exceeds the idempotency capture limit");
        }
        return bytes;
    }
}
