package io.github.coco.feature.web.context;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 默认 Coco 浏览器指纹解析器。
 * <p>
 * 基于 {@link CocoWebContextProperties#getFingerprintHeaderNames()} 配置的请求头采集浏览器信号，并生成稳定摘要。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class DefaultCocoBrowserFingerprintResolver implements CocoBrowserFingerprintResolver {

    private final CocoWebContextProperties properties;

    /**
     * <p>
     * 创建默认 Coco 浏览器指纹解析器。
     * </p>
     * @param properties Web 请求上下文配置属性
     */
    public DefaultCocoBrowserFingerprintResolver(CocoWebContextProperties properties) {
        this.properties = properties == null ? new CocoWebContextProperties() : properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoBrowserFingerprint resolve(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        Map<String, String> signals = new LinkedHashMap<>();
        Map<String, String> hashSignals = new LinkedHashMap<>();
        for (String headerName : this.properties.getFingerprintHeaderNames()) {
            if (headerName == null || headerName.isBlank()) {
                continue;
            }
            List<String> values = existingHeaderValues(checkedRequest, headerName);
            if (!values.isEmpty()) {
                String normalizedName = headerName.trim().toLowerCase(Locale.ROOT);
                signals.put(normalizedName, fingerprintSignalValue(values, this.properties.getMaxHeaderValueLength()));
                hashSignals.put(normalizedName, fingerprintSignalValue(values, 0));
            }
        }
        return CocoBrowserFingerprint.from(signals, hashSignals);
    }

    private static List<String> existingHeaderValues(HttpServletRequest request, String headerName) {
        Enumeration<String> values = request.getHeaders(headerName);
        if (values == null) {
            return List.of();
        }
        return enumerationAsStream(values)
                .map(DefaultCocoBrowserFingerprintResolver::normalizeString)
                .filter(Objects::nonNull)
                .toList();
    }

    private static Stream<String> enumerationAsStream(Enumeration<String> values) {
        if (values == null) {
            return Stream.empty();
        }
        ArrayList<String> copied = new ArrayList<>();
        while (values.hasMoreElements()) {
            copied.add(values.nextElement());
        }
        return copied.stream();
    }

    private static String normalizeString(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trimValue(String value, int maxLength) {
        String normalized = normalizeString(value);
        if (normalized == null) {
            return "";
        }
        return maxLength <= 0 || normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength) + "...";
    }

    private static String fingerprintSignalValue(List<String> values, int maxLength) {
        if (values.size() == 1) {
            return trimValue(values.get(0), maxLength);
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            String value = trimValue(values.get(index), maxLength);
            builder.append(index).append('=').append(value.length()).append(':').append(value).append(';');
        }
        return builder.toString();
    }
}
