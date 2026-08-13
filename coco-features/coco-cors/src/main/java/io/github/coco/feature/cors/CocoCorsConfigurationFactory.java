package io.github.coco.feature.cors;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;

final class CocoCorsConfigurationFactory {

    private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+\\-.^_`|~0-9A-Za-z]+");

    private static final Pattern ORIGIN_PATTERN = Pattern.compile(
            "https?://(?:\\*|[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)"
                    + "(?:\\.(?:\\*|[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?))*"
                    + "(?::(?:\\*|[0-9]{1,5}|\\[[0-9]{1,5}(?:,[0-9]{1,5})*\\]))?",
            Pattern.CASE_INSENSITIVE);

    private CocoCorsConfigurationFactory() {
    }

    static UrlBasedCorsConfigurationSource create(CocoCorsProperties properties) {
        List<String> allowedOrigins = normalizeOrigins(properties.getAllowedOrigins());
        List<String> allowedOriginPatterns = normalizeOriginPatterns(properties.getAllowedOriginPatterns());
        validateOrigins(allowedOrigins, allowedOriginPatterns, properties.isAllowCredentials());

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedOriginPatterns(allowedOriginPatterns);
        configuration.setAllowedMethods(normalizeMethods(properties.getAllowedMethods()));
        configuration.setAllowedHeaders(normalizeHeaders(properties.getAllowedHeaders(), "allowed-headers"));
        configuration.setExposedHeaders(normalizeHeaders(properties.getExposedHeaders(), "exposed-headers"));
        configuration.setAllowCredentials(properties.isAllowCredentials());
        configuration.setMaxAge(validateMaxAge(properties.getMaxAge()));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        for (String pathPattern : normalizePathPatterns(properties.getPathPatterns())) {
            source.registerCorsConfiguration(pathPattern, configuration);
        }
        return source;
    }

    private static List<String> normalizeOrigins(List<String> values) {
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String value : values == null ? List.<String>of() : values) {
            value = requireValue(value, "allowed-origins");
            String origin = "*".equals(value) ? value : normalizeOrigin(value);
            if (!seen.add(origin)) {
                throw invalid("allowed-origins contains duplicates");
            }
            normalized.add(origin);
        }
        return normalized;
    }

    private static List<String> normalizeOriginPatterns(List<String> values) {
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String value : values == null ? List.<String>of() : values) {
            String pattern = requireValue(value, "allowed-origin-patterns");
            if (!"*".equals(pattern) && !ORIGIN_PATTERN.matcher(pattern).matches()) {
                throw invalid("allowed-origin-patterns contains an invalid value");
            }
            validatePatternPorts(pattern);
            String canonical = pattern.toLowerCase(Locale.ROOT);
            if (!seen.add(canonical)) {
                throw invalid("allowed-origin-patterns contains duplicates");
            }
            normalized.add(canonical);
        }
        return normalized;
    }

    private static String normalizeOrigin(String value) {
        URI origin;
        try {
            origin = URI.create(value);
        }
        catch (IllegalArgumentException exception) {
            throw invalid("allowed-origins contains an invalid value");
        }
        String scheme = origin.getScheme();
        String host = origin.getHost();
        if (origin.isOpaque() || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || !StringUtils.hasText(host) || origin.getRawUserInfo() != null
                || origin.getRawQuery() != null || origin.getRawFragment() != null
                || StringUtils.hasText(origin.getRawPath()) || invalidPort(origin.getPort())) {
            throw invalid("allowed-origins contains an invalid value");
        }
        return scheme.toLowerCase(Locale.ROOT) + "://" + host.toLowerCase(Locale.ROOT)
                + (origin.getPort() < 0 ? "" : ":" + origin.getPort());
    }

    private static boolean invalidPort(int port) {
        return port > 65535;
    }

    private static void validatePatternPorts(String pattern) {
        int start = pattern.lastIndexOf(':');
        if (start < "https://".length()) {
            return;
        }
        String value = pattern.substring(start + 1);
        if ("*".equals(value)) {
            return;
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            for (String port : value.substring(1, value.length() - 1).split(",")) {
                validatePort(port);
            }
            return;
        }
        validatePort(value);
    }

    private static void validatePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 0 || port > 65535) {
                throw invalid("allowed-origin-patterns contains an invalid port");
            }
        }
        catch (NumberFormatException exception) {
            throw invalid("allowed-origin-patterns contains an invalid port");
        }
    }

    private static void validateOrigins(List<String> allowedOrigins, List<String> allowedOriginPatterns,
            boolean allowCredentials) {
        if (allowedOrigins.isEmpty() && allowedOriginPatterns.isEmpty()) {
            throw invalid("enabled CORS requires allowed-origins or allowed-origin-patterns");
        }
        boolean hasWildcard = allowedOrigins.contains("*")
                || allowedOriginPatterns.stream().anyMatch(pattern -> pattern.contains("*"));
        if (hasWildcard && allowCredentials) {
            throw invalid("allow-credentials cannot be combined with a wildcard origin or origin pattern");
        }
        if (allowedOrigins.contains("*") && (allowedOrigins.size() > 1 || !allowedOriginPatterns.isEmpty())) {
            throw invalid("wildcard allowed-origins cannot be combined with other origins");
        }
        if (allowedOriginPatterns.contains("*")
                && (allowedOriginPatterns.size() > 1 || !allowedOrigins.isEmpty())) {
            throw invalid("wildcard allowed-origin-patterns cannot be combined with other origins");
        }
    }

    private static List<String> normalizeMethods(List<String> values) {
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String value : requireValues(values, "allowed-methods")) {
            String method = value.toUpperCase(Locale.ROOT);
            if (!"*".equals(method)) {
                try {
                    HttpMethod.valueOf(method);
                }
                catch (IllegalArgumentException exception) {
                    throw invalid("allowed-methods contains an invalid HTTP method");
                }
            }
            if (!seen.add(method)) {
                throw invalid("allowed-methods contains duplicates");
            }
            normalized.add(method);
        }
        return normalized;
    }

    private static List<String> normalizeHeaders(List<String> values, String propertyName) {
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String value : values == null ? List.<String>of() : values) {
            String header = requireValue(value, propertyName);
            if (!"*".equals(header) && !HEADER_NAME.matcher(header).matches()) {
                throw invalid(propertyName + " contains an invalid HTTP header");
            }
            String canonical = header.toLowerCase(Locale.ROOT);
            if (!seen.add(canonical)) {
                throw invalid(propertyName + " contains duplicates");
            }
            normalized.add(header);
        }
        return normalized;
    }

    private static long validateMaxAge(long maxAge) {
        if (maxAge < 0) {
            throw invalid("max-age must not be negative");
        }
        return maxAge;
    }

    private static List<String> normalizePathPatterns(List<String> values) {
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        PathPatternParser parser = new PathPatternParser();
        for (String value : requireValues(values, "path-patterns")) {
            if (!value.startsWith("/")) {
                throw invalid("path-patterns must start with '/'");
            }
            try {
                parser.parse(value);
            }
            catch (IllegalArgumentException exception) {
                throw invalid("path-patterns contains an invalid pattern");
            }
            if (!seen.add(value)) {
                throw invalid("path-patterns contains duplicates");
            }
            normalized.add(value);
        }
        return normalized;
    }

    private static List<String> requireValues(List<String> values, String propertyName) {
        if (values == null || values.isEmpty()) {
            throw invalid(propertyName + " must not be empty");
        }
        List<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            normalized.add(requireValue(value, propertyName));
        }
        return normalized;
    }

    private static String requireValue(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw invalid(propertyName + " must not contain blank values");
        }
        return value.trim();
    }

    private static IllegalStateException invalid(String reason) {
        return new IllegalStateException("Invalid coco.cors configuration: " + reason);
    }
}
