package io.github.coco.feature.web.context.payload;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.feature.web.body.CocoCachedBodyHttpServletRequest;
import io.github.coco.feature.web.body.CocoCachedRequestBody;
import io.github.coco.feature.web.context.CocoWebParameterProperties;
import io.github.coco.feature.web.context.CocoWebParameterSource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 默认 Coco 请求体参数解析器。
 * <p>
 * 仅从已缓存请求体中解析 JSON 和表单 payload 参数，不主动消费 Servlet 原始输入流。
 * </p>
 * <p>
 * 同时返回统一的解析结果模型，明确区分已解析、未缓存、密文传输态、格式错误和限制截断等状态。
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
public final class DefaultCocoPayloadParameterResolver implements CocoPayloadParameterResolver {

    private static final String MASKED_VALUE = "******";

    private static final String FORM_URLENCODED = "application/x-www-form-urlencoded";

    private static final Set<String> DEFAULT_ENCRYPTED_HEADER_NAMES = Set.of("X-Coco-Encrypted");

    private final CocoWebParameterProperties properties;

    private final ObjectMapper objectMapper;

    private final Set<String> encryptedHeaderNames;

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
        this(properties, objectMapper, DEFAULT_ENCRYPTED_HEADER_NAMES);
    }

    /**
     * <p>
     * 创建默认 Coco 请求体参数解析器。
     * </p>
     * @param properties Web 请求参数配置属性
     * @param objectMapper JSON 序列化器
     * @param encryptedHeaderNames 加密标记请求头名称集合
     */
    public DefaultCocoPayloadParameterResolver(CocoWebParameterProperties properties, ObjectMapper objectMapper,
            Set<String> encryptedHeaderNames) {
        this.properties = properties == null ? new CocoWebParameterProperties() : properties;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.encryptedHeaderNames = normalizeHeaderNames(encryptedHeaderNames);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoWebPayloadParseResult resolvePayloadParseResult(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        return resolvePayloadParseResult(checkedRequest, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<String>> resolvePayloadParameters(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        return resolvePayloadParseResult(checkedRequest, true).parameters();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoWebPayloadParseResult resolveRawPayloadParseResult(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        return resolvePayloadParseResult(checkedRequest, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<String>> resolveRawPayloadParameters(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        return resolvePayloadParseResult(checkedRequest, false).parameters();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoWebPayloadParseStatus resolvePayloadParseStatus(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        return resolvePayloadParseResult(checkedRequest, true).status();
    }

    private CocoWebPayloadParseResult resolvePayloadParseResult(HttpServletRequest request, boolean sanitize) {
        if (!this.properties.getPayload().isEnabled()) {
            return CocoWebPayloadParseResult.empty(CocoWebPayloadParseStatus.DISABLED, CocoWebParameterSource.NONE);
        }
        if (encryptedTransportBody(request)) {
            return CocoWebPayloadParseResult.empty(CocoWebPayloadParseStatus.ENCRYPTED_TRANSPORT,
                    inferPayloadSource(request));
        }
        String contentType = normalizeMediaType(request.getContentType());
        if (!hasRequestBody(request)) {
            return CocoWebPayloadParseResult.empty(CocoWebPayloadParseStatus.NO_BODY, CocoWebParameterSource.NONE);
        }
        if (!isIncludedContentType(contentType)) {
            return CocoWebPayloadParseResult.empty(CocoWebPayloadParseStatus.UNSUPPORTED_CONTENT_TYPE,
                    inferPayloadSource(contentType));
        }
        return CocoCachedBodyHttpServletRequest.cachedBody(request)
                .filter(CocoCachedRequestBody::cached)
                .map(cachedBody -> parseCachedBody(contentType, request, cachedBody, sanitize))
                .orElseGet(() -> CocoWebPayloadParseResult.empty(CocoWebPayloadParseStatus.NOT_CACHED,
                        inferPayloadSource(contentType)));
    }

    private CocoWebPayloadParseResult parseCachedBody(String contentType, HttpServletRequest request,
            CocoCachedRequestBody cachedBody, boolean sanitize) {
        byte[] content = cachedBody.content();
        if (content.length == 0) {
            return CocoWebPayloadParseResult.empty(CocoWebPayloadParseStatus.PARSED, inferPayloadSource(contentType));
        }
        if (FORM_URLENCODED.equals(contentType)) {
            return adapt(parseFormPayload(new String(content, requestCharset(request))), true, requestCharset(request),
                    sanitize);
        }
        if (isJsonContentType(contentType)) {
            return adapt(parseJsonPayload(content), false, requestCharset(request), sanitize);
        }
        return CocoWebPayloadParseResult.empty(CocoWebPayloadParseStatus.UNSUPPORTED_CONTENT_TYPE,
                inferPayloadSource(contentType));
    }

    private CocoWebPayloadParseResult parseFormPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return CocoWebPayloadParseResult.empty(CocoWebPayloadParseStatus.PARSED, CocoWebParameterSource.FORM);
        }
        PayloadParseState state = new PayloadParseState(this.properties.getPayload().getMaxParameterCount(),
                this.properties.getPayload().getMaxJsonDepth());
        for (String pair : payload.split("&", -1)) {
            if (state.parameterLimitReached()) {
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
            if (state.reachedParameterLimit()) {
                state.markParameterLimitReached();
                break;
            }
            addParameter(state.parameters(), name, value, state);
        }
        return new CocoWebPayloadParseResult(copy(state.parameters()), state.status(), CocoWebParameterSource.FORM);
    }

    private CocoWebPayloadParseResult parseJsonPayload(byte[] content) {
        try {
            JsonNode root = this.objectMapper.readTree(content);
            if (root == null || root.isMissingNode()) {
                return CocoWebPayloadParseResult.empty(CocoWebPayloadParseStatus.PARSED, CocoWebParameterSource.JSON);
            }
            PayloadParseState state = new PayloadParseState(this.properties.getPayload().getMaxParameterCount(),
                    this.properties.getPayload().getMaxJsonDepth());
            flattenJson(state.parameters(), "", root, 0, state);
            return new CocoWebPayloadParseResult(copy(state.parameters()), state.status(), CocoWebParameterSource.JSON);
        }
        catch (Exception ex) {
            return CocoWebPayloadParseResult.empty(CocoWebPayloadParseStatus.MALFORMED_PAYLOAD,
                    CocoWebParameterSource.JSON);
        }
    }

    private void flattenJson(Map<String, List<String>> parameters, String path, JsonNode node,
            int depth, PayloadParseState state) {
        if (node == null || state.parameterLimitReached()) {
            return;
        }
        if (depth > state.maxJsonDepth()) {
            state.markJsonDepthLimitReached();
            return;
        }
        if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                if (state.reachedParameterLimit()) {
                    state.markParameterLimitReached();
                    return;
                }
                String fieldName = fieldNames.next();
                flattenJson(parameters, childPath(path, fieldName), node.get(fieldName), depth + 1, state);
            }
            return;
        }
        if (node.isArray()) {
            flattenJsonArray(parameters, path, node, depth, state);
            return;
        }
        if (state.reachedParameterLimit()) {
            state.markParameterLimitReached();
            return;
        }
        addParameter(parameters, path.isBlank() ? "$" : path, scalarValue(node), state);
    }

    private void flattenJsonArray(Map<String, List<String>> parameters, String path, JsonNode node,
            int depth, PayloadParseState state) {
        for (int index = 0; index < node.size(); index++) {
            if (state.reachedParameterLimit()) {
                state.markParameterLimitReached();
                return;
            }
            JsonNode value = node.get(index);
            if (value != null && value.isValueNode()) {
                addParameter(parameters, path.isBlank() ? "$" : path, scalarValue(value), state);
            }
            else {
                flattenJson(parameters, indexedPath(path, index), value, depth + 1, state);
            }
        }
    }

    private CocoWebPayloadParseResult adapt(CocoWebPayloadParseResult parseResult, boolean formPayload,
            Charset charset, boolean sanitize) {
        if (!sanitize) {
            return parseResult;
        }
        return new CocoWebPayloadParseResult(
                sanitizePayloadParameters(parseResult.parameters(), formPayload, charset),
                parseResult.status(),
                parseResult.source());
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

    private static boolean hasRequestBody(HttpServletRequest request) {
        CocoCachedRequestBody cachedBody = CocoCachedBodyHttpServletRequest.effectiveBody(request)
                .orElse(CocoCachedRequestBody.empty());
        if (cachedBody.cached()) {
            return cachedBody.length() > 0;
        }
        long contentLength = request.getContentLengthLong();
        if (contentLength > 0L) {
            return true;
        }
        String contentLengthHeader = request.getHeader("Content-Length");
        if (contentLengthHeader != null && !contentLengthHeader.isBlank()) {
            try {
                return Long.parseLong(contentLengthHeader.trim()) > 0L;
            }
            catch (NumberFormatException ignored) {
                return true;
            }
        }
        String transferEncoding = request.getHeader("Transfer-Encoding");
        return transferEncoding != null && !transferEncoding.isBlank();
    }

    private boolean encryptedTransportBody(HttpServletRequest request) {
        if (!encrypted(request)) {
            return false;
        }
        CocoCachedRequestBody effectiveBody = CocoCachedBodyHttpServletRequest.effectiveBody(request)
                .orElse(CocoCachedRequestBody.empty());
        CocoCachedRequestBody transportBody = CocoCachedBodyHttpServletRequest.transportBody(request)
                .orElse(CocoCachedRequestBody.empty());
        return effectiveBody.cached() && transportBody.cached()
                && Objects.equals(effectiveBody.sha256(), transportBody.sha256());
    }

    private boolean encrypted(HttpServletRequest request) {
        for (String encryptedHeaderName : this.encryptedHeaderNames) {
            String encrypted = request.getHeader(encryptedHeaderName);
            if (encrypted != null && ("true".equalsIgnoreCase(encrypted.trim()) || "1".equals(encrypted.trim()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isJsonContentType(String contentType) {
        return contentType != null
                && ("application/json".equals(contentType) || contentType.endsWith("+json"));
    }

    private static CocoWebParameterSource inferPayloadSource(HttpServletRequest request) {
        return inferPayloadSource(normalizeMediaType(request.getContentType()));
    }

    private static CocoWebParameterSource inferPayloadSource(String contentType) {
        if (FORM_URLENCODED.equals(contentType)) {
            return CocoWebParameterSource.FORM;
        }
        if (isJsonContentType(contentType)) {
            return CocoWebParameterSource.JSON;
        }
        return contentType == null ? CocoWebParameterSource.NONE : CocoWebParameterSource.PAYLOAD;
    }

    private static void addParameter(Map<String, List<String>> parameters, String name, String value,
            PayloadParseState state) {
        if (name == null || name.isBlank()) {
            return;
        }
        parameters.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value == null ? "" : value);
        state.incrementCount();
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

    private static Set<String> normalizeHeaderNames(Set<String> headerNames) {
        if (headerNames == null || headerNames.isEmpty()) {
            return DEFAULT_ENCRYPTED_HEADER_NAMES;
        }
        Set<String> normalizedHeaderNames = new LinkedHashSet<>();
        for (String headerName : headerNames) {
            if (headerName != null && !headerName.isBlank()) {
                normalizedHeaderNames.add(headerName.trim());
            }
        }
        return normalizedHeaderNames.isEmpty() ? DEFAULT_ENCRYPTED_HEADER_NAMES : Set.copyOf(normalizedHeaderNames);
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

    private static final class PayloadParseState {

        private final Map<String, List<String>> parameters = new LinkedHashMap<>();

        private final int maxParameterCount;

        private final int maxJsonDepth;

        private int parameterCount;

        private boolean parameterLimitReached;

        private boolean jsonDepthLimitReached;

        private PayloadParseState(int maxParameterCount, int maxJsonDepth) {
            this.maxParameterCount = maxParameterCount;
            this.maxJsonDepth = maxJsonDepth;
        }

        private Map<String, List<String>> parameters() {
            return this.parameters;
        }

        private int maxJsonDepth() {
            return this.maxJsonDepth;
        }

        private boolean reachedParameterLimit() {
            return this.parameterCount >= this.maxParameterCount;
        }

        private boolean parameterLimitReached() {
            return this.parameterLimitReached;
        }

        private void markParameterLimitReached() {
            this.parameterLimitReached = true;
        }

        private void markJsonDepthLimitReached() {
            this.jsonDepthLimitReached = true;
        }

        private void incrementCount() {
            this.parameterCount++;
        }

        private CocoWebPayloadParseStatus status() {
            if (this.parameterLimitReached) {
                return CocoWebPayloadParseStatus.PARAMETER_LIMIT_REACHED;
            }
            if (this.jsonDepthLimitReached) {
                return CocoWebPayloadParseStatus.JSON_DEPTH_LIMIT_REACHED;
            }
            return CocoWebPayloadParseStatus.PARSED;
        }
    }
}
