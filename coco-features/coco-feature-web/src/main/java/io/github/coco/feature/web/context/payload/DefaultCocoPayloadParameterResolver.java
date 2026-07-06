package io.github.coco.feature.web.context.payload;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.feature.web.body.CocoCachedBodyHttpServletRequest;
import io.github.coco.feature.web.body.CocoCachedRequestBody;
import io.github.coco.feature.web.context.CocoWebParameterProperties;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 默认 Coco 请求体参数解析器。
 * <p>
 * 仅从已缓存请求体中解析 JSON 和表单 payload 参数，不主动消费 Servlet 原始输入流。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class DefaultCocoPayloadParameterResolver implements CocoPayloadParameterResolver {

    private static final String MASKED_VALUE = "******";

    private static final String FORM_URLENCODED = "application/x-www-form-urlencoded";

    private final CocoWebParameterProperties properties;

    private final ObjectMapper objectMapper;

    /**
     * <p>
     * 创建默认 Coco 请求体参数解析器。
     * </p>
     * @param properties Web 请求参数配置属性
     */
    public DefaultCocoPayloadParameterResolver(CocoWebParameterProperties properties) {
        this(properties, null);
    }

    /**
     * <p>
     * 创建默认 Coco 请求体参数解析器。
     * </p>
     * @param properties Web 请求参数配置属性
     * @param objectMapper JSON 序列化器
     */
    public DefaultCocoPayloadParameterResolver(CocoWebParameterProperties properties, ObjectMapper objectMapper) {
        this.properties = properties == null ? new CocoWebParameterProperties() : properties;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<String>> resolvePayloadParameters(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        String contentType = normalizeMediaType(checkedRequest.getContentType());
        if (!this.properties.getPayload().isEnabled() || !isIncludedContentType(contentType)) {
            return Map.of();
        }
        Map<String, List<String>> rawParameters = CocoCachedBodyHttpServletRequest.cachedBody(checkedRequest)
                .filter(CocoCachedRequestBody::cached)
                .map(cachedBody -> parseCachedBody(contentType, checkedRequest, cachedBody))
                .orElse(Map.of());
        if (rawParameters.isEmpty()) {
            return Map.of();
        }
        return sanitizePayloadParameters(rawParameters, FORM_URLENCODED.equals(contentType), requestCharset(checkedRequest));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<String>> resolveRawPayloadParameters(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        if (!this.properties.getPayload().isEnabled()) {
            return Map.of();
        }
        String contentType = normalizeMediaType(checkedRequest.getContentType());
        if (!isIncludedContentType(contentType)) {
            return Map.of();
        }
        return CocoCachedBodyHttpServletRequest.cachedBody(checkedRequest)
                .filter(CocoCachedRequestBody::cached)
                .map(cachedBody -> parseCachedBody(contentType, checkedRequest, cachedBody))
                .orElse(Map.of());
    }

    private Map<String, List<String>> parseCachedBody(String contentType, HttpServletRequest request,
            CocoCachedRequestBody cachedBody) {
        byte[] content = cachedBody.content();
        if (content.length == 0) {
            return Map.of();
        }
        if (FORM_URLENCODED.equals(contentType)) {
            return parseFormPayload(new String(content, requestCharset(request)));
        }
        if (isJsonContentType(contentType)) {
            return parseJsonPayload(content);
        }
        return Map.of();
    }

    private Map<String, List<String>> parseFormPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        int[] count = new int[] { 0 };
        for (String pair : payload.split("&", -1)) {
            if (count[0] >= this.properties.getPayload().getMaxParameterCount()) {
                break;
            }
            if (pair == null || pair.isBlank()) {
                continue;
            }
            int separatorIndex = pair.indexOf('=');
            String name = separatorIndex < 0 ? pair : pair.substring(0, separatorIndex);
            if (name.isBlank()) {
                continue;
            }
            String value = separatorIndex < 0 ? "" : pair.substring(separatorIndex + 1);
            addParameter(parameters, name, value, count);
        }
        return copy(parameters);
    }

    private Map<String, List<String>> parseJsonPayload(byte[] content) {
        try {
            JsonNode root = this.objectMapper.readTree(content);
            if (root == null || root.isMissingNode()) {
                return Map.of();
            }
            Map<String, List<String>> parameters = new LinkedHashMap<>();
            int[] count = new int[] { 0 };
            flattenJson(parameters, "", root, 0, count);
            return copy(parameters);
        }
        catch (Exception ex) {
            return Map.of();
        }
    }

    private void flattenJson(Map<String, List<String>> parameters, String path, JsonNode node,
            int depth, int[] count) {
        if (node == null || count[0] >= this.properties.getPayload().getMaxParameterCount()
                || depth > this.properties.getPayload().getMaxJsonDepth()) {
            return;
        }
        if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                flattenJson(parameters, childPath(path, fieldName), node.get(fieldName), depth + 1, count);
            }
            return;
        }
        if (node.isArray()) {
            flattenJsonArray(parameters, path, node, depth, count);
            return;
        }
        addParameter(parameters, path.isBlank() ? "$" : path, scalarValue(node), count);
    }

    private void flattenJsonArray(Map<String, List<String>> parameters, String path, JsonNode node,
            int depth, int[] count) {
        for (int index = 0; index < node.size(); index++) {
            if (count[0] >= this.properties.getPayload().getMaxParameterCount()) {
                return;
            }
            JsonNode value = node.get(index);
            if (value != null && value.isValueNode()) {
                addParameter(parameters, path.isBlank() ? "$" : path, scalarValue(value), count);
            }
            else {
                flattenJson(parameters, indexedPath(path, index), value, depth + 1, count);
            }
        }
    }

    private Map<String, List<String>> sanitizePayloadParameters(Map<String, List<String>> rawParameters,
            boolean formPayload, Charset charset) {
        Map<String, List<String>> sanitizedParameters = new LinkedHashMap<>();
        rawParameters.forEach((name, values) -> {
            String sanitizedName = sanitizeName(name, formPayload, charset);
            if (sanitizedName == null || sanitizedName.isBlank()) {
                return;
            }
            sanitizedParameters.computeIfAbsent(sanitizedName, ignored -> new ArrayList<>())
                    .addAll(sanitizeValues(sanitizedName, values, formPayload, charset));
        });
        return copy(sanitizedParameters);
    }

    private List<String> sanitizeValues(String name, List<String> values, boolean formPayload, Charset charset) {
        if (isMaskedParameterName(name)) {
            return List.of(MASKED_VALUE);
        }
        if (values == null || values.isEmpty()) {
            return List.of("");
        }
        return values.stream()
                .map(value -> sanitizeValue(value, formPayload, charset))
                .toList();
    }

    private String sanitizeName(String name, boolean formPayload, Charset charset) {
        return formPayload ? decodeFormComponent(name, charset) : name;
    }

    private String sanitizeValue(String value, boolean formPayload, Charset charset) {
        String normalizedValue = formPayload ? decodeFormComponent(value, charset) : value;
        return trimValue(normalizedValue, this.properties.getMaxParameterValueLength());
    }

    private boolean isMaskedParameterName(String name) {
        return name != null && this.properties.getMaskedParameterNames()
                .contains(name.trim().toLowerCase(Locale.ROOT));
    }

    private boolean isIncludedContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        for (String includedContentType : this.properties.getPayload().getIncludedContentTypes()) {
            if (matchesMediaType(includedContentType, contentType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isJsonContentType(String contentType) {
        return "application/json".equals(contentType) || contentType.endsWith("+json");
    }

    private static void addParameter(Map<String, List<String>> parameters, String name, String value, int[] count) {
        if (name == null || name.isBlank()) {
            return;
        }
        parameters.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value == null ? "" : value);
        count[0]++;
    }

    private static Map<String, List<String>> copy(Map<String, List<String>> parameters) {
        if (parameters.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copied = new LinkedHashMap<>();
        parameters.forEach((name, values) -> copied.put(name, List.copyOf(values)));
        return Collections.unmodifiableMap(copied);
    }

    private static String childPath(String path, String childName) {
        String normalizedName = childName == null ? "" : childName.trim();
        if (path == null || path.isBlank()) {
            return normalizedName;
        }
        return normalizedName.isBlank() ? path : path + "." + normalizedName;
    }

    private static String indexedPath(String path, int index) {
        String prefix = path == null || path.isBlank() ? "$" : path;
        return prefix + "[" + index + "]";
    }

    private static String scalarValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.isTextual() ? node.asText() : node.asText("");
    }

    private static Charset requestCharset(HttpServletRequest request) {
        String encoding = request.getCharacterEncoding();
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding.trim());
        }
        catch (RuntimeException ex) {
            return StandardCharsets.UTF_8;
        }
    }

    private static String normalizeMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        int parameterIndex = contentType.indexOf(';');
        String value = parameterIndex < 0 ? contentType : contentType.substring(0, parameterIndex);
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesMediaType(String pattern, String contentType) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        if ("*/*".equals(pattern)) {
            return true;
        }
        int wildcardIndex = pattern.indexOf('*');
        if (wildcardIndex < 0) {
            return contentType.equals(pattern);
        }
        String prefix = pattern.substring(0, wildcardIndex);
        String suffix = pattern.substring(wildcardIndex + 1);
        return contentType.startsWith(prefix) && contentType.endsWith(suffix);
    }

    private static String trimValue(String value, int maxLength) {
        String normalized = value == null || value.isBlank() ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }

    private static String decodeFormComponent(String value, Charset charset) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return URLDecoder.decode(value, charset);
        }
        catch (IllegalArgumentException ex) {
            return value;
        }
    }
}
